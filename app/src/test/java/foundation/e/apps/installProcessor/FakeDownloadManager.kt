/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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

package foundation.e.apps.installProcessor

import foundation.e.apps.api.DownloadManager

class FakeDownloadManager(
    downloadManger: android.app.DownloadManager,
    cacheDir: String,
    query: android.app.DownloadManager.Query
) : DownloadManager(downloadManger, cacheDir, query) {

    override suspend fun checkDownloadProcess(
        downloadingIds: LongArray,
        handleFailed: suspend () -> Unit
    ) {
        if (downloadingIds.contains(-1)) {
            handleFailed()
        }
    }
}
