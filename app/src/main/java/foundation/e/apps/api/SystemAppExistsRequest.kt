package foundation.e.apps.api

import android.util.Log
import foundation.e.apps.utils.Common
import foundation.e.apps.utils.Constants
import foundation.e.apps.utils.Error
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.Buffer
import java.util.*


class SystemAppExistsRequest(private val keyword: String) {


    fun request(callback: (Error?, ArrayList<String?>) -> Unit) {
        try {
            var l1 = ArrayList<String?>()
            val url = Constants.SYSTEM_PACKAGES_JSON_FILE_URL
            val urlConnection = Common.createConnection(url, Constants.REQUEST_METHOD_GET)
            val responseCode = urlConnection.responseCode
            //Log.e("TAG", "in request .SystemAppExistsRequest..." + responseCode);
            val data =  urlConnection.inputStream.bufferedReader().readText()
            urlConnection.disconnect()
            l1.add(data);
            callback.invoke(null, l1)
        } catch (e: Exception) {
            //Log.e("Exception ", "................"+e.message);
            callback.invoke(Error.findError(e), ArrayList())
        }
    }

}