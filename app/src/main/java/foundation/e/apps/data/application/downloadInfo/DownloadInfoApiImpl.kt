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

package foundation.e.apps.data.application.downloadInfo

import foundation.e.apps.data.AppSourcesContainer
import foundation.e.apps.data.cleanapk.CleanApkDownloadInfoFetcher
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.playstore.PlayStoreRepository
import javax.inject.Inject
import javax.inject.Named

class DownloadInfoApiImpl @Inject constructor(
    private val appSourcesContainer: AppSourcesContainer
) : DownloadInfoApi {

    override suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): String? {
        val list = appSourcesContainer.gplayRepository.getOnDemandModule(
            packageName,
            moduleName,
            versionCode,
            offerType,
        )

        for (element in list) {
            if (element.name == "$moduleName.apk") {
                return element.url
            }
        }

        return null
    }

    override suspend fun updateFusedDownloadWithDownloadingInfo(
        origin: Origin,
        fusedDownload: FusedDownload
    ) {
        val list = mutableListOf<String>()
        when (origin) {
            Origin.CLEANAPK -> {
                updateDownloadInfoFromCleanApk(fusedDownload, list)
            }

            Origin.GPLAY -> {
                updateDownloadInfoFromGplay(fusedDownload, list)
            }
        }

        fusedDownload.downloadURLList = list
    }

    private suspend fun updateDownloadInfoFromGplay(
        fusedDownload: FusedDownload,
        list: MutableList<String>
    ) {
        val downloadList =
            appSourcesContainer.gplayRepository.getDownloadInfo(
                fusedDownload.packageName,
                fusedDownload.versionCode,
                fusedDownload.offerType
            )
        fusedDownload.files = downloadList
        list.addAll(downloadList.map { it.url })
    }

    private suspend fun updateDownloadInfoFromCleanApk(
        fusedDownload: FusedDownload,
        list: MutableList<String>
    ) {
        val downloadInfo =
            (appSourcesContainer.cleanApkAppsRepository as CleanApkDownloadInfoFetcher)
                .getDownloadInfo(fusedDownload.id).body()
        downloadInfo?.download_data?.download_link?.let { list.add(it) }
        fusedDownload.signature = downloadInfo?.download_data?.signature ?: ""
    }

    override suspend fun getOSSDownloadInfo(id: String, version: String?) =
        (appSourcesContainer.cleanApkAppsRepository as CleanApkDownloadInfoFetcher)
            .getDownloadInfo(id, version)
}
