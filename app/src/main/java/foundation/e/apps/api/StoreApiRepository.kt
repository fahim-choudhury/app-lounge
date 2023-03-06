package foundation.e.apps.api

interface StoreApiRepository {
    suspend fun getHomeScreenData(): Any
    suspend fun getSearchResult(query: String): Any
    suspend fun getSearchSuggestions(query: String): Any
}
