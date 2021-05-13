/*
 * Copyright (C) 2019-2021  E FOUNDATION
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.api

import android.content.Context
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import foundation.e.apps.MainActivity.Companion.mActivity
import foundation.e.apps.application.model.Application
import foundation.e.apps.application.model.data.BasicData
import foundation.e.apps.applicationmanager.ApplicationManager
import foundation.e.apps.utils.ApplicationParser
import foundation.e.apps.utils.Common
import foundation.e.apps.utils.Constants
import foundation.e.apps.utils.Error
import java.net.URLEncoder

class ListApplicationsRequest(private val category: String, private val page: Int, private val resultsPerPage: Int) {

    companion object {
        private val reader = Common.getObjectMapper().readerFor(ListApplicationsResult::class.java)
    }

    fun request(callback: (Error?, ListApplicationsResult?) -> Unit) {
        try {
            var appType = mActivity.showApplicationTypePreference()
            val string = URLEncoder.encode(category, "utf-8").lowercase()
            val url = Constants.BASE_URL + "apps?action=list_apps&category=$string&nres=$resultsPerPage&page=$page&type=$appType"
            val urlConnection = Common.createConnection(url, Constants.REQUEST_METHOD_GET)
            val result = reader.readValue<ListApplicationsResult>(urlConnection.inputStream)
            urlConnection.disconnect()
            callback.invoke(null, result)

        } catch (e: Exception) {
            callback.invoke(Error.findError(e), null)
        }
    }

    class ListApplicationsResult @JsonCreator
    constructor(@JsonProperty("apps") private val apps: Array<BasicData>) {

        fun getApplications(applicationManager: ApplicationManager, context: Context): ArrayList<Application> {
            return ApplicationParser.parseToApps(applicationManager, context, apps)
        }
    }

}
