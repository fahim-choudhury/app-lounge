/*
 * Copyright (C) 2021-2024 MURENA SAS
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

package foundation.e.apps.data.gitlab.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.squareup.moshi.Json
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class SystemAppInfo(
    val name: String,
    @Json(name = "package_name") val packageName: String,
    @Json(name = "version_code") val versionCode: Int,
    @Json(name = "min_sdk") val minSdk: Int,
    @Json(name = "version_name") val versionName: String,
    @Json(name = "url") val downloadUrl: String,
    @Json(name = "size") val size: Long?,
    @Json(name = "author_name") val authorName: String?,
    val priority: Boolean?,
    @Json(name = "blacklisted_android") val blacklistedAndroid: List<Int>?,
    @Json(name = "blacklisted_devices") val blacklistedDevices: List<String>?,
)

private const val RANDOM_SIZE = 1L

fun SystemAppInfo.toApplication(): Application {
    return Application(
        _id = UUID.randomUUID().toString(),
        author = authorName ?: "eFoundation",
        description = "",
        latest_version_code = versionCode,
        latest_version_number = versionName,
        name = name,
        package_name = packageName,
        origin = Origin.GITLAB_RELEASES,
        originalSize = size ?: RANDOM_SIZE,
        url = downloadUrl,
        isSystemApp = true,
        filterLevel = FilterLevel.NONE,
    )
}
