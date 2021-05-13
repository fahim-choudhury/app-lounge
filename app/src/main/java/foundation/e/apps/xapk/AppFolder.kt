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

import foundation.e.apps.BuildConfig
import foundation.e.apps.MainActivity
import java.io.File

object AppFolder {
    private val APP_FOLDER_NAME: String
        get() {
            return if (false) {
                "XAPK Installer"
            } else {
                "XAPK Installer-${BuildConfig.BUILD_TYPE}"
            }
        }

    private const val TEMP_FOLDER_NAME = "temp"

    val tempFolder: File?
        get() = createAppFolderDirectory(TEMP_FOLDER_NAME)


    fun getXApkInstallTempFolder(packageName: String): File {
        val tempFile = File(tempFolder, packageName)
        FsUtils.createOnNotFound(tempFile)
        return tempFile
    }


    private fun createAppFolderDirectory(directoryName: String): File? {
        return FsUtils.createOnNotFound(File(appFolder, directoryName))
    }

    private val appFolder: File?
        get() {
            return if (FsUtils.isSdUsable) {
                val appContext = MainActivity.applicationContext()
                val appFolder = File(appContext.getExternalFilesDir(null), APP_FOLDER_NAME)
                FsUtils.createOnNotFound(appFolder)
            } else {
                null
            }
        }

}
