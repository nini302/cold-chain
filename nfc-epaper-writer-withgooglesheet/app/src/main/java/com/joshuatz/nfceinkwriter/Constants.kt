package com.joshuatz.nfceinkwriter

const val PackageName = "com.joshuatz.nfceinkwriter"

const val WaveShareUID = "WSDZ10m"

// Order matches WS SDK Enum (except off by 1, due to zero-index)
// @see https://www.waveshare.com/wiki/Android_SDK_for_NFC-Powered_e-Paper
// @see https://github.com/RfidResearchGroup/proxmark3/blob/0d1f8ca957c0ae6f3039237889cdabe5921afe2d/client/src/cmdhfwaveshare.c#L81-L90
val ScreenSizes = arrayOf(
    "2.13\"",
    "2.9\"",
    "4.2\"",
    "7.5\"",
    "7.5\" HD",
    "2.7\"",
    "2.9\" v.B",
)

val DefaultScreenSize = ScreenSizes[1]

val ScreenSizesInPixels = mapOf(
    // The true resolution for 2.13" is 250x122, but there is a (likely) typo in the SDK
    // @see https://github.com/joshuatz/nfc-epaper-writer/issues/2
    "2.13\"" to Pair(250, 128),
    "2.9\"" to Pair(296, 128),
    "4.2\"" to Pair(400, 300),
    "7.5\"" to Pair(800, 480),
    "7.5\" HD" to Pair(880, 528),
    "2.7\"" to Pair(264, 176),
    "2.9\" v.B" to Pair(296, 128),
)

object Constants {
    var Preference_File_Key = "Preferences"
    var PreferenceKeys = PrefKeys
}

object PrefKeys {
    var DisplaySize = "Display_Size"
    var GeneratedImgPath = "Generated_Image_Path"
}

object IntentKeys {
    var GeneratedImgPath = "$PackageName.imgUri"
    var GeneratedImgMime = "$PackageName.imgMime"
}

val GeneratedImageFilename = "generated.png"