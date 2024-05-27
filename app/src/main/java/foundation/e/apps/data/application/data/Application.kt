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
 *
 */

package foundation.e.apps.data.application.data

import android.content.Context
import android.net.Uri
import com.aurora.gplayapi.Constants.Restriction
import com.aurora.gplayapi.data.models.ContentRating
import com.google.gson.annotations.SerializedName
import foundation.e.apps.R
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.enums.Type.NATIVE
import foundation.e.apps.data.enums.Type.PWA
import foundation.e.apps.di.CommonUtilsModule.LIST_OF_NULL

data class Application(
    val _id: String = String(),
    val author: String = String(),
    val category: String = String(),
    val description: String = String(),
    var perms: List<String> = emptyList(),
    var trackers: List<String> = emptyList(),
    var reportId: Long = -1L,
    val icon_image_path: String = String(),
    val last_modified: String = String(),
    var latest_version_code: Int = -1,
    val latest_version_number: String = String(),
    val latest_downloaded_version: String = String(),
    val licence: String = String(),
    val name: String = String(),
    val other_images_path: List<String> = emptyList(),
    val package_name: String = String(),
    val ratings: Ratings = Ratings(),
    val offer_type: Int = -1,
    var status: Status = Status.UNAVAILABLE,
    var origin: Origin = Origin.CLEANAPK,
    val shareUrl: String = String(),
    val originalSize: Long = 0,
    val appSize: String = String(),
    var source: String = String(),
    val price: String = String(),
    val isFree: Boolean = true,
    val is_pwa: Boolean = false,
    var pwaPlayerDbId: Long = -1,
    val url: String = String(),
    var type: Type = NATIVE,
    var privacyScore: Int = -1,
    var isPurchased: Boolean = false,

    /*
     * List of permissions from Exodus API.
     * This list is now used to calculate the privacy score instead of perms variable above.
     * If the value is LIST_OF_NULL - listOf("null"), it means no data is available in Exodus API for this package,
     * hence display "N/A"
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5136
     */
    var permsFromExodus: List<String> = LIST_OF_NULL,
    var updatedOn: String = String(),

    /*
     * Store restriction from App.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    var restriction: Restriction = Restriction.NOT_RESTRICTED,

    /*
     * Show a blank app at the end during loading.
     * Used when loading apps of a category.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    var isPlaceHolder: Boolean = false,

    /*
     * Store the filter/restriction level.
     * If it is not NONE, then the app cannot be downloaded.
     * If it is FilterLevel.UI, then we should show "N/A" on install button.
     * If it is FilterLevel.DATA, then this app should not be displayed.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5720
     */
    var filterLevel: FilterLevel = FilterLevel.UNKNOWN,
    var isGplayReplaced: Boolean = false,
    @SerializedName(value = "on_fdroid")
    val isFDroidApp: Boolean = false,
    val contentRating: ContentRating = ContentRating()
) {
    fun updateType() {
        this.type = if (this.is_pwa) PWA else NATIVE
    }

    fun updateSource(context: Context) {
        this.apply {
            source = if (origin != Origin.CLEANAPK) ""
            else if (is_pwa) context.getString(R.string.pwa)
            else context.getString(R.string.open_source)
        }
    }
}

val Application.shareUri: Uri
    get() = when (type) {
        PWA -> Uri.parse(url)
        NATIVE -> when {
            isFDroidApp -> buildFDroidUri(package_name)
            else -> Uri.parse(shareUrl)
        }
    }

private fun buildFDroidUri(packageName: String): Uri {
    return Uri.Builder()
        .scheme("https")
        .authority("f-droid.org")
        .appendPath("packages")
        .appendPath(packageName)
        .build()
}
