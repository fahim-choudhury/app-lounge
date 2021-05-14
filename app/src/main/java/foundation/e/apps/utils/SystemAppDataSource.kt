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

package foundation.e.apps.utils

import foundation.e.apps.application.model.data.BasicData
import foundation.e.apps.application.model.data.FullData

object SystemAppDataSource {
    lateinit var basicData: BasicData

    /**
     * Creates [BasicData] source for the system app using the given parameters
     * @param id ID of the application
     * @param tag Latest version number
     * @param iconUri icon URI
     * @param downloadUrl URL to download the application
     * @return An instance of [BasicData]
     */
    fun createDataSource(id: String, tag: String, iconUri: String, downloadUrl: String): BasicData {
        basicData = BasicData(
            id = id,
            name = Constants.MICROG,
            packageName = Constants.MICROG_PACKAGE,
            lastVersionNumber = tag,
            lastVersionCode = 0,
            latestDownloadableUpdate = "",
            armeabi_latestDownloadableUpdate = "",
            arm64_v8a_latest_latestDownloadableUpdate = "",
            x86_latestDownloadableUpdate = "",
            armeabi_v7a_latestDownloadableUpdate = "",
            apkArchitecture = ArrayList(),
            author = "e-Foundation",
            iconUri = iconUri,
            imagesUri = arrayOf(),
            privacyRating = 0f,
            ratings = BasicData.Ratings(0f, 0f),
            category = Constants.SYSTEM_APPS,
            is_pwa = false,
            x86_64_latestDownloadableUpdate = "",
            downloadUrl = downloadUrl
        )
        return basicData
    }

    /**
     * Provides a [FullData] source for the system application
     * @return An instance of [FullData] containing values from [basicData]
     */
    fun getFullData(): FullData {
        return FullData(
            basicData.id,
            basicData.name,
            basicData.packageName,
            basicData.lastVersionNumber,
            basicData.arm64_v8a_lastVersionCode,
            basicData.arm64_v8a_latest_latestDownloadableUpdate,
            basicData.x86_64_latestDownloadableUpdate,
            basicData.x86_64_lastVersionNumber,
            basicData.x86_64_lastVersionCode,
            basicData.armeabi_latestDownloadableUpdate,
            basicData.armeabi_lastVersionNumber,
            basicData.armeabi_lastVersionCode,
            basicData.arm64_v8a_latest_latestDownloadableUpdate,
            basicData.arm64_v8a_lastVersionNumber, basicData.arm64_v8a_lastVersionCode,
            basicData.x86_latestDownloadableUpdate,
            basicData.x86_lastVersionNumber,
            basicData.x86_lastVersionCode,
            basicData.armeabi_v7a_latestDownloadableUpdate,
            basicData.armeabi_v7a_lastVersionNumber,
            basicData.armeabi_v7a_lastVersionCode,
            basicData.apkArchitecture,
            basicData.author,
            basicData.iconUri,
            basicData.imagesUri, basicData.id, "", "", basicData.ratings, false,
            downloadUrl = basicData.downloadUrl
        )
    }

    /**
     * Provides the [basicData]
     * @return An instance of [BasicData]
     */
    fun basicData(): BasicData {
        return basicData
    }

}