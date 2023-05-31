/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2022  E FOUNDATION
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
package foundation.e.apps.ui.home.model

import androidx.recyclerview.widget.DiffUtil
import foundation.e.apps.data.fused.data.FusedApp

class HomeChildFusedAppDiffUtil : DiffUtil.ItemCallback<FusedApp>() {
    override fun areItemsTheSame(oldItem: FusedApp, newItem: FusedApp): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: FusedApp, newItem: FusedApp): Boolean {
        return oldItem._id == newItem._id &&
            oldItem.appSize.contentEquals(newItem.appSize) &&
            oldItem.author.contentEquals(newItem.author) &&
            oldItem.category.contentEquals(newItem.category) &&
            oldItem.description.contentEquals(newItem.description) &&
            oldItem.icon_image_path.contentEquals(newItem.icon_image_path) &&
            oldItem.last_modified.contentEquals(newItem.last_modified) &&
            oldItem.latest_version_code == newItem.latest_version_code &&
            oldItem.latest_version_number.contentEquals(newItem.latest_version_number) &&
            oldItem.licence.contentEquals(newItem.licence) &&
            oldItem.appSize.contentEquals(newItem.appSize) &&
            oldItem.name.contentEquals(newItem.name) &&
            oldItem.offer_type == newItem.offer_type &&
            oldItem.origin == newItem.origin &&
            oldItem.other_images_path == newItem.other_images_path &&
            oldItem.package_name.contentEquals(newItem.package_name) &&
            oldItem.perms == newItem.perms &&
            oldItem.ratings == newItem.ratings &&
            oldItem.shareUrl.contentEquals(newItem.shareUrl) &&
            oldItem.source.contentEquals(newItem.source) &&
            oldItem.status == newItem.status &&
            oldItem.trackers == newItem.trackers &&
            oldItem.url.contentEquals(newItem.url) &&
            oldItem.isFree == newItem.isFree &&
            oldItem.is_pwa == newItem.is_pwa
    }
}
