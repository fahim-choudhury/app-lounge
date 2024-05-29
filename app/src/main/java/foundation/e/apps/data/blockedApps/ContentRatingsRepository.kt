/*
 *  Copyright MURENA SAS 2024
 *  Apps  Quickly and easily install Android apps onto your device!
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.data.blockedApps

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import foundation.e.apps.data.DownloadManager
import foundation.e.apps.data.fusedDownload.FileManager
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ContentRatingsRepository @Inject constructor(
    private val downloadManager: DownloadManager,
    private val gson: Gson,
    @Named("cacheDir") private val cacheDir: String
) {

    private var _contentRatings = listOf<ContentRating>()
    val contentRatings: List<ContentRating>
        get() = _contentRatings

    companion object {
        private const val CONTENT_RATINGS_FILE_URL =
            "https://gitlab.e.foundation/e/os/app-lounge-content-ratings/-/raw/main/content_ratings.json?ref_type=heads&inline=false"
        private const val CONTENT_RATINGS_FILE_NAME = "content_ratings.json"
    }

    fun fetchContentRatingData() {
        downloadManager.downloadFileInCache(
            CONTENT_RATINGS_FILE_URL,
            fileName = CONTENT_RATINGS_FILE_NAME
        ) { success, _ ->
            if (success) {
                parseContentRatingData()
            }
        }
    }

    private fun parseContentRatingData() {
        _contentRatings = try {
            val outputPath = "$cacheDir/warning_list/"
            FileManager.moveFile("$cacheDir/",
                CONTENT_RATINGS_FILE_NAME, outputPath)
            val downloadedFile = File(outputPath + CONTENT_RATINGS_FILE_NAME)
            val contentRatingJson = String(downloadedFile.inputStream().readBytes())
            Timber.d("ContentRatings file contents: $contentRatingJson")

            val contentRatingsListType = object : TypeToken<List<ContentRating>>() {}.type
            gson.fromJson(contentRatingJson, contentRatingsListType)
        } catch (exception: Exception) {
            Timber.e(exception.localizedMessage ?: "", exception)
            mutableListOf()
        }
    }
}