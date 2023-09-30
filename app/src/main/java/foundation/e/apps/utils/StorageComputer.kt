/*
 *  Copyright (C) 2023 MUREANA SAS
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.utils

import android.os.Environment
import android.os.StatFs
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import java.text.CharacterIterator
import java.text.StringCharacterIterator

object StorageComputer {
    fun spaceMissing(fusedDownload: FusedDownload): Long {
        return getRequiredSpace(fusedDownload) - calculateAvailableDiskSpace()
    }

    private fun getRequiredSpace(fusedDownload: FusedDownload) =
        fusedDownload.appSize + (500 * (1000 * 1000))

    private fun calculateAvailableDiskSpace(): Long {
        val path = Environment.getDataDirectory().absolutePath
        val statFs = StatFs(path)
        return statFs.availableBytes
    }

    fun humanReadableByteCountSI(byteValue: Long): String {
        var bytes = byteValue

        if (-1000 < bytes && bytes < 1000) {
            return "$bytes B"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        while (bytes <= -999950 || bytes >= 999950) {
            bytes /= 1000
            ci.next()
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current())
    }
}
