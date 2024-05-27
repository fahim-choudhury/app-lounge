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

package foundation.e.apps.data.application.utils

import android.content.Context
import android.text.format.Formatter
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.Category
import foundation.e.apps.data.application.data.Category as AppLoungeCategory
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Ratings
import foundation.e.apps.data.enums.Origin

fun App.toApplication(context: Context): Application {
    val app = Application(
        _id = this.id.toString(),
        author = this.developerName,
        category = this.categoryName,
        description = this.description,
        perms = this.permissions,
        icon_image_path = this.iconArtwork.url,
        last_modified = this.updatedOn,
        latest_version_code = this.versionCode,
        latest_version_number = this.versionName,
        name = this.displayName,
        other_images_path = this.screenshots.toList(),
        package_name = this.packageName,
        ratings = Ratings(
            usageQualityScore =
            this.labeledRating.run {
                if (isNotEmpty()) {
                    this.replace(",", ".").toDoubleOrNull() ?: -1.0
                } else -1.0
            }
        ),
        offer_type = this.offerType,
        origin = Origin.GPLAY,
        shareUrl = this.shareUrl,
        originalSize = this.size,
        appSize = Formatter.formatFileSize(context, this.size),
        isFree = this.isFree,
        price = this.price,
        restriction = this.restriction,
        contentRating = this.contentRating
    )
    return app
}

fun Category.toCategory(): AppLoungeCategory {
    val id = this.browseUrl.substringAfter("cat=").substringBefore("&c=")
    return AppLoungeCategory(
        id = id.lowercase(),
        title = this.title,
        browseUrl = this.browseUrl,
        imageUrl = this.imageUrl,
    )
}

private fun MutableList<Artwork>.toList(): List<String> {
    val list = mutableListOf<String>()
    this.forEach {
        list.add(it.url)
    }
    return list
}
