package com.joshuatz.nfceinkwriter

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.IOException;
import java.util.Collections;



object GetValues {
    /**
     * Returns a range of values from a spreadsheet.
     *
     * @param spreadsheetId - Id of the spreadsheet.
     * @param range         - Range of cells of the spreadsheet.
     * @return Values in the range
     * @throws IOException - if credentials file not found.
     */
    @Throws(IOException::class)
    fun getValues(spreadsheetId: String?, range: String?): ValueRange? {
        /* Load pre-authorized user credentials from the environment.
           TODO(developer) - See https://developers.google.com/identity for
            guides on implementing OAuth2 for your application. */
        val credentials: GoogleCredentials = GoogleCredentials.getApplicationDefault()
            .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS))
        val requestInitializer: HttpRequestInitializer = HttpCredentialsAdapter(
            credentials
        )

        // Create the sheets API client
        val service = Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            requestInitializer
        )
            .setApplicationName("Sheets samples")
            .build()
        var result: ValueRange? = null
        try {
            // Gets the values of the cells in the specified range.
            result = service.spreadsheets().values()[spreadsheetId, range].execute()
            val numRows = if (result.getValues() != null) result.getValues().size else 0
            System.out.printf("%d rows retrieved.", numRows)
        } catch (e: GoogleJsonResponseException) {
            // TODO(developer) - handle error appropriately
            val error = e.details
            if (error.code == 404) {
                System.out.printf("Spreadsheet not found with id '%s'.\n", spreadsheetId)
            } else {
                throw e
            }
        }
        return result
    }
}