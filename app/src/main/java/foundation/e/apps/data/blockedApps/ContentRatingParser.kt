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
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import foundation.e.apps.data.install.FileManager
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

class ContentRatingParser @Inject constructor(
    private val gson: Gson,
    @Named("cacheDir") private val cacheDir: String
) {

    companion object {
        private const val CONTENT_RATINGS_FILE_NAME = "content_ratings.json"
    }

    fun parseContentRatingData(): List<ContentRatingGroup> {
        return try {
            val outputPath = moveFile()
            val contentRatingJson = readJsonFromFile(outputPath)
            Timber.d("ContentRatings file contents: $contentRatingJson")
            parseJsonOfContentRatingGroup(contentRatingJson)
        } catch (exception: IOException) {
            handleException(exception)
        } catch (exception: JsonSyntaxException) {
            handleException(exception)
        }
    }

    private fun readJsonFromFile(outputPath: String): String {
        val downloadedFile =
            File(outputPath + CONTENT_RATINGS_FILE_NAME)
        val contentRatingJson = String(downloadedFile.inputStream().readBytes())

        return contentRatingJson
    }

    private fun moveFile(): String {
        val outputPath = "$cacheDir/content_ratings/"
        FileManager.moveFile(
            "$cacheDir/",
            CONTENT_RATINGS_FILE_NAME, outputPath
        )

        return outputPath
    }

    private fun parseJsonOfContentRatingGroup(contentRatingJson: String): List<ContentRatingGroup> {
        val contentRatingsListTypeGroup = object : TypeToken<List<ContentRatingGroup>>() {}.type
        val contentRatingGroups: List<ContentRatingGroup> =
            gson.fromJson(contentRatingJson, contentRatingsListTypeGroup)

        return contentRatingGroups.map {
            it.ratings = it.ratings.map { rating ->
                rating.lowercase()
            }
            it
        }
    }

    private fun handleException(exception: Exception): List<ContentRatingGroup> {
        Timber.e(exception.localizedMessage ?: "", exception)
        return listOf()
    }
}
