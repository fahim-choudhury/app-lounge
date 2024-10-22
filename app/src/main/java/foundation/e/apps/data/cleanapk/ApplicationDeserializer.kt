/*
 * Copyright ECORP SAS 2022
 * Apps  Quickly and easily install Android apps onto your device!
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

package foundation.e.apps.data.cleanapk

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import foundation.e.apps.data.cleanapk.data.app.CleanApkApplication

class ApplicationDeserializer : JsonDeserializer<CleanApkApplication> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: java.lang.reflect.Type?,
        context: JsonDeserializationContext?
    ): CleanApkApplication {
        val gson = Gson()
        val cleanApkApplication = gson.fromJson(json?.asJsonObject?.toString(), CleanApkApplication::class.java)
        val lastUpdate = cleanApkApplication.app.latest_downloaded_version
        val lastUpdateJson = json?.asJsonObject?.get("app")?.asJsonObject?.get(lastUpdate)?.asJsonObject
        val lastUpdatedOn = lastUpdateJson
            ?.asJsonObject?.get("update_on")?.asString ?: ""
        cleanApkApplication.app.updatedOn = lastUpdatedOn
        cleanApkApplication.app.latest_version_code = lastUpdateJson?.get("version_code")?.asInt ?: -1
        return cleanApkApplication
    }
}
