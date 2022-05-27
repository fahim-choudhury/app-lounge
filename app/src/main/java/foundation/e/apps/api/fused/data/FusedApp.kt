/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
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

package foundation.e.apps.api.fused.data

import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.Type
import foundation.e.apps.utils.modules.CommonUtilsModule.LIST_OF_NULL

data class FusedApp(
    val _id: String = String(),
    val author: String = String(),
    val category: String = String(),
    val description: String = String(),
    var perms: List<String> = emptyList(),
    var trackers: List<String> = emptyList(),
    val icon_image_path: String = String(),
    val last_modified: String = String(),
    val latest_version_code: Int = -1,
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
    var type: Type = Type.NATIVE,
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
    var updatedOn: String = String()
)
