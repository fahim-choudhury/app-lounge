package foundation.e.apps.api.cleanapk

import foundation.e.apps.api.StoreApiRepository
import foundation.e.apps.api.cleanapk.data.home.HomeScreen
import retrofit2.Response

class CleanApkAppsRepository(
    private val cleanAPKInterface: CleanAPKInterface,
    private val cleanApkAppDetailApi: CleanApkAppDetailApi
) : StoreApiRepository {
    override suspend fun getHomeScreenData(): Response<HomeScreen> {
        return cleanAPKInterface.getHomeScreenData(
            CleanAPKInterface.APP_TYPE_ANY,
            CleanAPKInterface.APP_SOURCE_FOSS
        )
    }

    override suspend fun getSearchResult(query: String): Any {
        return cleanAPKInterface.searchApps(
            query,
            CleanAPKInterface.APP_TYPE_ANY,
            CleanAPKInterface.APP_SOURCE_FOSS,
            20,
            1,
            null
        )
    }
}