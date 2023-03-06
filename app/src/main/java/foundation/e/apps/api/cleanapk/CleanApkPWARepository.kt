package foundation.e.apps.api.cleanapk

import foundation.e.apps.api.StoreApiRepository
import foundation.e.apps.api.cleanapk.data.search.Search
import retrofit2.Response

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

    override suspend fun getSearchResult(query: String): Response<Search> {
        return cleanAPKInterface.searchApps(
            query,
            CleanAPKInterface.APP_SOURCE_ANY,
            CleanAPKInterface.APP_TYPE_PWA,
            20,
            1,
            null
        )
    }

    override suspend fun getSearchSuggestions(query: String): Any {
        TODO("Not yet implemented")
    }
}
