/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.data.install.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.aurora.gplayapi.data.models.ContentRating
import com.aurora.gplayapi.data.models.File
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.Type

@Entity(tableName = "FusedDownload")
data class AppInstall(
    @PrimaryKey val id: String = String(),
    val origin: Origin = Origin.CLEANAPK,
    var status: Status = Status.UNAVAILABLE,
    val name: String = String(),
    val packageName: String = String(),
    var downloadURLList: MutableList<String> = mutableListOf(),
    var downloadIdMap: MutableMap<Long, Boolean> = mutableMapOf(),
    val orgStatus: Status = Status.UNAVAILABLE,
    val type: Type = Type.NATIVE,
    val iconImageUrl: String = String(),
    val versionCode: Int = 1,
    val offerType: Int = -1,
    val isFree: Boolean = true,
    var appSize: Long = 0,
    var files: List<File> = mutableListOf(),
    var signature: String = String()
) {
    @Ignore
    private val installingStatusList = listOf(
        Status.AWAITING,
        Status.DOWNLOADING,
        Status.DOWNLOADED,
        Status.INSTALLING
    )

    @Ignore
    var contentRating: ContentRating = ContentRating()

    @Ignore
    var isFDroidApp: Boolean = false

    @Ignore
    var antiFeatures: List<Map<String, String>> = emptyList()

    fun isAppInstalling() = installingStatusList.contains(status)

    fun isAwaiting() = status == Status.AWAITING

    fun areFilesDownloaded() = downloadIdMap.isNotEmpty() && !downloadIdMap.values.contains(false)

    fun getAppIconUrl(): String {
        if (this.origin == Origin.CLEANAPK) {
            return "${CleanApkRetrofit.ASSET_URL}${this.iconImageUrl}"
        }
        return this.iconImageUrl
    }
}
