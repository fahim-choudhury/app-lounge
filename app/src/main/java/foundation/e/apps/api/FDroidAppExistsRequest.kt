package foundation.e.apps.api

import android.util.Log
import foundation.e.apps.utils.Common
import foundation.e.apps.utils.Constants
import foundation.e.apps.utils.Error
import java.util.ArrayList


class FDroidAppExistsRequest(private val keyword: String) {


    fun request(callback: (Error?, ArrayList<Int?>) -> Unit) {
        try {
            var l1=ArrayList<Int?>()
            val url = Constants.F_DROID_PACKAGES_URL + keyword+"/"
            val urlConnection = Common.createConnection(url, Constants.REQUEST_METHOD_GET)
            val responseCode = urlConnection.responseCode
            Log.e("TAG", "in request ...."+responseCode);
            urlConnection.disconnect()
            l1.add(responseCode);
            callback.invoke(null, l1)
        } catch (e: Exception) {
            //Log.e("Exception ", "................"+e.message);
            callback.invoke(Error.findError(e), ArrayList())
        }
    }

}