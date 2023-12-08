package foundation.e.apps.data.application

import foundation.e.apps.data.cleanapk.CleanApkDownloadInfoFetcher
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.playstore.PlayStoreRepository
import javax.inject.Inject
import javax.inject.Named

class DownloadInfoApiImpl @Inject constructor(
    @Named("gplayRepository") private val gplayRepository: PlayStoreRepository,
    @Named("cleanApkAppsRepository") private val cleanApkAppsRepository: CleanApkRepository
) : DownloadInfoApi {

    override suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): String? {
        val list = gplayRepository.getOnDemandModule(
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
                val downloadInfo =
                    (cleanApkAppsRepository as CleanApkDownloadInfoFetcher).getDownloadInfo(
                        fusedDownload.id
                    )
                        .body()
                downloadInfo?.download_data?.download_link?.let { list.add(it) }
                fusedDownload.signature = downloadInfo?.download_data?.signature ?: ""
            }

            Origin.GPLAY -> {
                val downloadList =
                    gplayRepository.getDownloadInfo(
                        fusedDownload.packageName,
                        fusedDownload.versionCode,
                        fusedDownload.offerType
                    )
                fusedDownload.files = downloadList
                list.addAll(downloadList.map { it.url })
            }
        }
        fusedDownload.downloadURLList = list
    }

    override suspend fun getOSSDownloadInfo(id: String, version: String?) =
        (cleanApkAppsRepository as CleanApkDownloadInfoFetcher).getDownloadInfo(id, version)
}