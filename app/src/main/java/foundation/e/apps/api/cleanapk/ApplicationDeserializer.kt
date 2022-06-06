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

package foundation.e.apps.api.cleanapk

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import foundation.e.apps.api.cleanapk.data.app.Application

class ApplicationDeserializer : JsonDeserializer<Application> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: java.lang.reflect.Type?,
        context: JsonDeserializationContext?
    ): Application {
        val gson = Gson()
        val application = gson.fromJson(json?.asJsonObject?.toString(), Application::class.java)
        val lastUpdate = application.app.latest_downloaded_version
        val lastUpdatedOn = json?.asJsonObject?.get("app")?.asJsonObject?.get(lastUpdate)
            ?.asJsonObject?.get("update_on")?.asString ?: ""
        application.app.updatedOn = lastUpdatedOn
        return application
    }
}
