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

package foundation.e.apps.data.parentalcontrol.gplayrating

import foundation.e.apps.data.DownloadManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePlayContentRatingsRepository
@Inject
constructor(
    private val downloadManager: DownloadManager,
    private val googlePlayContentRatingParser: GooglePlayContentRatingParser
) {

    private var _contentRatingGroups = listOf<GooglePlayContentRatingGroup>()
    val contentRatingGroups: List<GooglePlayContentRatingGroup>
        get() = _contentRatingGroups

    companion object {
        private const val CONTENT_RATINGS_FILE_URL =
            "https://gitlab.e.foundation/e/os/app-lounge-content-ratings/-/raw/main/" +
                "content_ratings.json?ref_type=heads&inline=false"
        private const val CONTENT_RATINGS_FILE_NAME = "content_ratings.json"
    }

    fun fetchContentRatingData() {
        downloadManager.downloadFileInCache(
            CONTENT_RATINGS_FILE_URL, fileName = CONTENT_RATINGS_FILE_NAME) { success, _ ->
                _contentRatingGroups =
                    if (success) {
                        googlePlayContentRatingParser.parseContentRatingData()
                    } else {
                        emptyList()
                    }
            }
    }
}
