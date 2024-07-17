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
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.handleNetworkResult
import javax.inject.Inject

class DownloadInfoApiImpl @Inject constructor(
    private val appSources: AppSourcesContainer
) : DownloadInfoApi {

    override suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): String? {
        val result = handleNetworkResult {
            appSources.gplayRepo.getOnDemandModule(
                packageName,
                moduleName,
                versionCode,
                offerType,
            )
        }

        if (result.isSuccess()) {
            for (element in result.data!!) { // isSuccess() checks ensures null safety of data
                if (element.name == "$moduleName.apk") {
                    return element.url
                }
            }
        }

        return null
    }

    override suspend fun updateFusedDownloadWithDownloadingInfo(
        origin: Origin,
        appInstall: AppInstall
    ) {
        val list = mutableListOf<String>()
        when (origin) {
            Origin.CLEANAPK -> {
                updateDownloadInfoFromCleanApk(appInstall, list)
            }

            Origin.GPLAY -> {
                updateDownloadInfoFromGplay(appInstall, list)
            }

            Origin.GITLAB_RELEASES -> {
                // TODO
            }
        }

        appInstall.downloadURLList = list
    }

    private suspend fun updateDownloadInfoFromGplay(
        appInstall: AppInstall,
        list: MutableList<String>
    ) {
        val downloadList =
            appSources.gplayRepo.getDownloadInfo(
                appInstall.packageName,
                appInstall.versionCode,
                appInstall.offerType
            )
        appInstall.files = downloadList
        list.addAll(downloadList.map { it.url })
    }

    private suspend fun updateDownloadInfoFromCleanApk(
        appInstall: AppInstall,
        list: MutableList<String>
    ) {
        val downloadInfo =
            (appSources.cleanApkAppsRepo as CleanApkDownloadInfoFetcher)
                .getDownloadInfo(appInstall.id).body()
        downloadInfo?.download_data?.download_link?.let { list.add(it) }
        appInstall.signature = downloadInfo?.download_data?.signature ?: ""
    }

    override suspend fun getOSSDownloadInfo(id: String, version: String?) =
        (appSources.cleanApkAppsRepo as CleanApkDownloadInfoFetcher)
            .getDownloadInfo(id, version)
}
