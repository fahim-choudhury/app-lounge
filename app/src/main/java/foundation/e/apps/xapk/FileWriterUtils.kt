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

package foundation.e.apps.xapk

import androidx.annotation.WorkerThread
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileWriterUtils {

    interface FileWriterProgressCallback {
        fun onProgress(currentOffset: Long)
    }



    @WorkerThread
    fun writeFileFromIS(newFile: File, inputStreams: InputStream, fileWriterProgressCallback: FileWriterProgressCallback? = null): Boolean {
        var isSuccess = false
        var os: BufferedOutputStream? = null
        try {
            os = BufferedOutputStream(FileOutputStream(newFile))
            val data = ByteArray(1024 * 16)
            var len: Int
            var currentOffset = 0L
            while (inputStreams.read(data).apply { len = this } != -1) {
                os.write(data, 0, len)
                currentOffset += len
                fileWriterProgressCallback?.onProgress(currentOffset)
            }
            isSuccess = true
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                os?.close()
                inputStreams.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return isSuccess
    }
}
