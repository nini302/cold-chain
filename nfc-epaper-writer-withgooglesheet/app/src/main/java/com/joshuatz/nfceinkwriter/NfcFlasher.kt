package com.joshuatz.nfceinkwriter

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.*
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import waveshare.feng.nfctag.activity.WaveShareHandler
import java.io.IOException
import java.nio.charset.StandardCharsets
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse
import com.google.auth



class NfcFlasher : AppCompatActivity() {
    private var mIsFlashing = false
        get() = field
        set(isFlashing) {
            field = isFlashing
            // Hide or show flashing UI
            this.mWhileFlashingArea?.visibility =
                if (isFlashing) View.VISIBLE else View.GONE
            this.mWhileFlashingArea?.requestLayout()
            // Regardless of state change, progress should be reset to zero
            this.mProgressVal = 0

        }
    private var mNfcAdapter: NfcAdapter? = null
    private var mPendingIntent: PendingIntent? = null
    private var mNfcTechList = arrayOf(arrayOf(NfcA::class.java.name))
    private var mNfcIntentFilters: Array<IntentFilter>? = null
    private var mNfcCheckHandler: Handler? = null
    private val mNfcCheckIntervalMs = 250L
    private val mProgressCheckInterval = 50L
    private var mProgressBar: ProgressBar? = null
    private var mProgressVal: Int = 0
    private var mBitmap: Bitmap? = null
    private var mWhileFlashingArea: ConstraintLayout? = null
    private var mImgFilePath: String? = null
    private var mImgFileUri: Uri? = null
    private var text:String?="text"
    private var textSize:Float=0.5F


    // Note: Use of object expression / anon class is so `this` can be used
    // for reference to runnable (which would normally be off-limits)
    private val mNfcCheckCallback: Runnable = object : Runnable {
        override fun run() {
            checkNfcAndAttemptRecover()
            // Loop!
            mNfcCheckHandler?.postDelayed(this, mNfcCheckIntervalMs)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mImgFileUri != null) {
            outState.putString("serializedGeneratedImgUri", mImgFileUri.toString())
        }
    }

    // @TODO - change intent to just pass raw bytearr? Cleanup path usage?
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_flasher)



         this.mBitmap=textAsBitmap(text, textSize)
        /**
         * Actual flasher stuff
         */

        mWhileFlashingArea = findViewById(R.id.whileFlashingArea)
        mProgressBar = findViewById(R.id.nfcFlashProgressbar)

        val originatingIntent = intent

        // Set up intent and intent filters for NFC / NDEF scanning
        // This is part of the setup for foreground dispatch system
        val nfcIntent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        this.mPendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        // Set up the filters
        var ndefIntentFilter: IntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            // android:host
            ndefIntentFilter.addDataAuthority("ext", null)

            // android:pathPattern
            // allow all data paths - see notes below
            ndefIntentFilter.addDataPath(".*", PatternMatcher.PATTERN_SIMPLE_GLOB)
            // NONE of the below work, although at least one or more should
            // I think because the payload isn't getting extracted out into the intent by Android
            // Debugging shows mData.path = null, which makes no sense (it definitely is not, and if
            // I don't intercept AAR, Android definitely tries to open the corresponding app...
            //ndefIntentFilter.addDataPath("waveshare.feng.nfctag.*", PatternMatcher.PATTERN_SIMPLE_GLOB);
            //ndefIntentFilter.addDataPath(".*waveshare\\.feng\\.nfctag.*", PatternMatcher.PATTERN_SIMPLE_GLOB);
            //ndefIntentFilter.addDataPath("waveshare.feng.nfctag", PatternMatcher.PATTERN_LITERAL);
            //ndefIntentFilter.addDataPath("waveshare\\.feng\\.nfctag", PatternMatcher.PATTERN_LITERAL);

            // android:scheme
            ndefIntentFilter.addDataScheme("vnd.android.nfc")
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            Log.e("mimeTypeException", "Invalid / Malformed mimeType")
        }
        mNfcIntentFilters = arrayOf(ndefIntentFilter)

        // Init NFC adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (mNfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
        }

        // Start NFC check loop in case adapter dies
        startNfcCheckLoop()
    }

    override fun onPause() {
        super.onPause()
        this.stopNfcCheckLoop()
        this.disableForegroundDispatch()
    }

    override fun onResume() {
        super.onResume()
        this.startNfcCheckLoop()
        this.enableForegroundDispatch()
    }

    @SuppressLint("LongLogTag")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i("New intent", "New Intent: $intent")
        Log.v("Intent.action", intent.action ?: "no action")

        val preferences = Preferences(this)
        val screenSizeEnum = preferences.getScreenSizeEnum()

        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED || intent.action == NfcAdapter.ACTION_TAG_DISCOVERED || intent.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            val detectedTag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)!!
            val tagId = String(detectedTag.id, StandardCharsets.US_ASCII)
            val tagTechList = detectedTag.techList

            // Do we still have a bitmap to flash?
            val bitmap = this.mBitmap
            if (bitmap == null) {
                Log.v("Missing bitmap", "mBitmap = null")
                return
            }

            // Check for correct NFC type support
            if (tagTechList[0] != "android.nfc.tech.NfcA") {
                Log.v("Invalid tag type. Found:", tagTechList.toString())
                return
            }

            // Do an explicit check for the ID. This ID *appears* to be constant across all models
            if (tagId != WaveShareUID) {
                Log.v("Invalid tag ID", "$tagId != $WaveShareUID")
                // Currently, this ID is sometimes coming back corrupted, so it is a unreliable check
                // only enforce check if type != ndef, because in those cases we can't check AAR
                if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) {
                    return
                }
            }

            // ACTION_NDEF_DISCOVERED has the filter applied for the AAR record *type*,
            // but the filter for the payload (dataPath / pathPattern) is not working, so as
            // an extra check, AAR payload will be manually checked, as well as ID
            if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
                var aarFound = false
                val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                if (rawMsgs != null) {
                    for (msg in rawMsgs) {
                        val ndefMessage: NdefMessage = msg as NdefMessage
                        val records = ndefMessage.records
                        for (record in records) {
                            val payloadStr = String(record.payload)
                            if (!aarFound) aarFound = payloadStr == "waveshare.feng.nfctag"
                            if (aarFound) break
                        }
                        if (aarFound) break
                    }
                }

                if (!aarFound) {
                    Log.v("Bad NDEFs:", "records found, but missing AAR")
                }
            }

            if (!mIsFlashing) {
                // Here we go!!!
                Log.v("Matched!", "Tag is a match! Preparing to flash...")
                lifecycleScope.launch {
                    flashBitmap(detectedTag, bitmap, screenSizeEnum)
                }
            } else {
                Log.v("Not flashing", "Flashing already in progress!")
            }
        }
    }

    private suspend fun flashBitmap(tag: Tag, bitmap: Bitmap, screenSizeEnum: Int) {
        this.mIsFlashing = true
        val waveShareHandler = WaveShareHandler(this)
        // Setup loop to pass progress to UI
        // Handler + callback should run on main UI thread, since it needs to access View
        val progressCheckHandler = Handler(Looper.getMainLooper())
        val progressCheckCallback: Runnable = object : Runnable {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun run() {
                if (mIsFlashing) {
                    updateProgressBar(waveShareHandler.progress)
                    progressCheckHandler.postDelayed(this, mProgressCheckInterval)
                }
            }
        }
        // Notice how we are not using progressCheckCallback.run()
        // This is because this function is right now being called inside a sep thread from UI
        // so running outside of main will cause exception when trying to update progress bar / UI
        progressCheckHandler.post(progressCheckCallback)
        withContext(Dispatchers.IO) {
            val nfcaObj = NfcA.get(tag)
            try {
                // @TODO - this needs to be done in non-UI thread
                val result = waveShareHandler.sendBitmap(nfcaObj, screenSizeEnum, bitmap)

                // Need to run toast on main thread...
                runOnUiThread(Runnable {
                    var toast: Toast? = null
                    if (!result.success) {
                        toast = Toast.makeText(
                            applicationContext,
                            "FAILED to Flash :( ${result.errMessage}",
                            Toast.LENGTH_LONG
                        )
                    } else {
                        toast = Toast.makeText(
                            applicationContext,
                            "Success! Flashed display!",
                            Toast.LENGTH_LONG
                        )
                    }
                    toast?.show()
                })
                Log.v("Final success val", "Success = ${result.success.toString()}")
            } finally {
                try {
                    nfcaObj.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.v("Flashing failed", "See trace above")
                }
                Log.v("Tag closed", "Setting flash in progress = false")
                // Needs to run on UI thread - has setter method that does stuff
                runOnUiThread(Runnable {
                    mIsFlashing = false
                })
                progressCheckHandler.removeCallbacks(progressCheckCallback)
            }
        }

    }

    private fun enableForegroundDispatch() {
        this.mNfcAdapter?.enableForegroundDispatch(
            this,
            this.mPendingIntent,
            this.mNfcIntentFilters,
            this.mNfcTechList
        )
    }

    private fun disableForegroundDispatch() {
        this.mNfcAdapter?.disableForegroundDispatch(this)
    }

    private fun startNfcCheckLoop() {
        if (mNfcCheckHandler == null) {
            Log.v("NFC Check Loop", "START")
            mNfcCheckHandler = Handler(Looper.getMainLooper())
            mNfcCheckHandler?.postDelayed(mNfcCheckCallback, mNfcCheckIntervalMs)
        }
    }

    private fun stopNfcCheckLoop() {
        if (mNfcCheckHandler != null) {
            mNfcCheckHandler?.removeCallbacks(mNfcCheckCallback)
        }
        mNfcCheckHandler = null
    }

    private fun checkNfcAndAttemptRecover() {
        if (mNfcAdapter != null) {
            var isEnabled = false
            // Apparently querying the property can cause it to get updated
            // https://stackoverflow.com/a/55691449/11447682
            try {
                isEnabled = mNfcAdapter?.isEnabled ?: false
                if (!isEnabled) {
                    Log.v("NFC Check #1", "NFC is disabled. Checking again.")
                }
            } catch (_: Exception) {
            }
            try {
                isEnabled = mNfcAdapter?.isEnabled ?: false
                if (!isEnabled) {
                    Log.v("NFC Check #2", "NFC is disabled.")
                }
            } catch (_: Exception) {
            }
            if (isEnabled) {
                enableForegroundDispatch()
            } else {
                Log.w("NFC Check", "NFC is disabled - could be waiting on a system recovery")
            }
        } else {
            Log.e("NFC Check", "Adapter is completely unavailable!")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun updateProgressBar(updated: Int) {
        if (mProgressBar == null) {
            mProgressBar = findViewById(R.id.nfcFlashProgressbar)
        }
        mProgressBar?.setProgress(updated, true)
    }
    private fun textAsBitmap(text: String?, textSize: Float ): Bitmap? {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = textSize
        paint.color = 	Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        val baseline = -paint.ascent() // ascent() is negative
        val width = (paint.measureText(text) + 0.5f).toInt() // round
        val height = (baseline + paint.descent() + 0.5f).toInt()
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawText(text!!, 0F, baseline, paint)
        return image
    }
}