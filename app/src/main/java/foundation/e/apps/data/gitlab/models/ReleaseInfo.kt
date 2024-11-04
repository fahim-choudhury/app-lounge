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

import com.squareup.moshi.Json

data class GitLabReleaseInfo(
    val name: String,
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "released_at") val releasedAt: String,
    val assets: ReleaseAssets,
) {
    fun getAssetWebLink(assetName: String): String? {
        return assets.links.firstOrNull { it.name == assetName }?.directAssetUrl
    }
}

data class ReleaseAssets(
    val links: List<ReleaseLinks>,
)

data class ReleaseLinks(
    val name: String,
    @Json(name = "direct_asset_url")
    val directAssetUrl: String
)

enum class OsReleaseType {
    TEST,
    COMMUNITY,
    STABLE,
    UNKNOWN,
    ;

    override fun toString(): String {
        return this.name.lowercase()
    }

    companion object {
        fun get(value: String?): OsReleaseType {
            return OsReleaseType.values().find { it.name == value?.trim()?.uppercase() } ?: UNKNOWN
        }
    }
}