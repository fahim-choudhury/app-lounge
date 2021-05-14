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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import java.net.URL
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import javax.net.ssl.HttpsURLConnection

/**
 * Class containing various methods to work with the Images
 * @param imagesUri An array of images URI
 */
class ImagesLoader(private val imagesUri: Array<String>) {
    /**
     * Private class to be used by the parent class methods
     *
     * Loads the given Image using [AsyncTask] in background
     * @param uri URI of the image to load
     * @param key Key for the image to load
     */
    private class Image(private val uri: String, val key: Int) :
        AsyncTask<BlockingQueue<Image>, Any, Any>() {
        var image: Bitmap? = null

        override fun doInBackground(vararg params: BlockingQueue<Image>): Any? {
            val queue = params[0]
            try {
                val url = URL(Constants.BASE_URL + "media/" + uri)
                val urlConnection = url.openConnection() as HttpsURLConnection
                urlConnection.requestMethod = Constants.REQUEST_METHOD_GET
                urlConnection.connectTimeout = Constants.CONNECT_TIMEOUT
                urlConnection.readTimeout = Constants.READ_TIMEOUT
                image = BitmapFactory.decodeStream(urlConnection.inputStream)
                urlConnection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
            queue.put(this)
            return null
        }
    }

    /**
     * Loads the images in [imagesUri]
     * @return A list containing [Bitmap]
     */
    fun loadImages(): List<Bitmap> {
        val queue = LinkedBlockingQueue<Image>()
        startLoading(queue)
        val images = waitResults(queue)
        return sortByKey(images)
    }

    /**
     * Starts loading the images in [imagesUri]
     * @param queue A [BlockingQueue] of type [Image]
     */
    private fun startLoading(queue: BlockingQueue<Image>) {
        imagesUri.forEachIndexed { i, uri ->
            Image(uri, i).executeOnExecutor(Common.EXECUTOR, queue)
        }
    }

    /**
     * Waits for the resultant image to be loaded and returns it
     * @param queue A [BlockingQueue] of type [Image]
     * @return A list of [Image]
     */
    private fun waitResults(queue: BlockingQueue<Image>): List<Image> {
        val result = ArrayList<Image>()
        imagesUri.forEach { _ ->
            result.add(queue.take())
        }
        return result
    }

    /**
     * Sorts the given list of [Image] by keys
     * @return A sorted list of [Bitmap]
     */
    private fun sortByKey(images: List<Image>): List<Bitmap> {
        val result = arrayOfNulls<Bitmap>(images.size)
        images.forEach {
            result[it.key] = it.image
        }
        return makeNonNull(result)
    }

    /**
     * Filters out the null elements from the given array
     * @param images An array of [Bitmap]
     * @return A list of [Bitmap] without any null elements
     */
    private fun makeNonNull(images: Array<Bitmap?>): List<Bitmap> {
        val result = ArrayList<Bitmap>()
        images.forEach {
            if (it != null) {
                result.add(it)
            }
        }
        return result
    }
}
