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
import com.google.gson.Gson
import com.google.gson.JsonParser.parseReader
import foundation.e.apps.application.model.Application
import foundation.e.apps.application.model.data.BasicData
import foundation.e.apps.application.model.release.ReleaseData
import foundation.e.apps.applicationmanager.ApplicationManager
import foundation.e.apps.utils.*
import java.io.InputStreamReader


class GitlabDataRequest {


    fun requestGmsCoreRelease(callback: (Error?, GitlabDataResult?) -> Unit) = try {
        val url = Constants.RELEASE_API + Constants.MICROG_ID + Constants.RELEASE_ENDPOINT
        val urlConnection = Common.createConnection(url, Constants.REQUEST_METHOD_GET)
        val isr = InputStreamReader(urlConnection.inputStream)
        val element = parseReader(isr)

        val releaseList: List<ReleaseData> = Gson().fromJson(element.toString(),
                Array<ReleaseData>::class.java).toList()
        urlConnection.disconnect()

        val osReleaseType = OsInfo.getOSReleaseType()
        var releaseUrl = ""

        releaseList[0].assets.links.forEach {
            if (it.name.contains(osReleaseType)) {
                releaseUrl = it.url
            }
        }

        callback.invoke(null, GitlabDataResult(SystemAppDataSource.createDataSource(Constants.MICROG_ID.toString(),
                releaseList[0].tag_name, Constants.MICROG_ICON_URI, releaseUrl)))

    } catch (e: Exception) {
        callback.invoke(Error.findError(e), null)
    }

    class GitlabDataResult(private val data: BasicData) {
        fun getApplications(applicationManager: ApplicationManager, context: Context): ArrayList<Application> {
            return ApplicationParser.parseSystemAppData(applicationManager, context, data)
        }
    }


}
