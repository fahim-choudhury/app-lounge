package foundation.e.apps.api

interface StoreApiRepository {
    suspend fun getHomeScreenData(): Any
    fun getSearchResult(query: String): Any
}