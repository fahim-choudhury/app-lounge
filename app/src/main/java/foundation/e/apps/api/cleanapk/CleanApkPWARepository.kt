package foundation.e.apps.api.cleanapk

import foundation.e.apps.api.StoreApiRepository

class CleanApkPWARepository(
    private val cleanAPKInterface: CleanAPKInterface,
    private val cleanApkAppDetailApi: CleanApkAppDetailApi
) : StoreApiRepository {
    override suspend fun getHomeScreenData(): Any {
        return cleanAPKInterface.getHomeScreenData(
            CleanAPKInterface.APP_TYPE_PWA,
            CleanAPKInterface.APP_SOURCE_ANY
        )
    }

    override suspend fun getSearchResult(query: String): Any {
        TODO("Not yet implemented")
    }
}