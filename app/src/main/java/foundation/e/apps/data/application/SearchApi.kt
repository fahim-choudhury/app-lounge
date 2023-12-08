package foundation.e.apps.data.application

import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.data.Application

typealias GplaySearchResult = ResultSupreme<Pair<List<Application>, Set<SearchBundle.SubBundle>>>

interface SearchApi {
    companion object {
        const val APP_TYPE_ANY = "any"
        const val APP_TYPE_OPEN = "open"
        const val APP_TYPE_PWA = "pwa"
    }

    fun getApplicationCategoryPreference(): List<String>

    /**
     * Fetches search results from cleanAPK and GPlay servers and returns them
     * @param query Query
     * @param authData [AuthData]
     * @return ResultSupreme which contains a Pair<List<FusedApp>, Boolean> where List<FusedApp>
     *     is the app list and [Boolean] indicates more data to load or not.
     */
    suspend fun getCleanApkSearchResults(
        query: String,
        authData: AuthData
    ): ResultSupreme<Pair<List<Application>, Boolean>>

    suspend fun getGplaySearchResult(
        query: String,
        nextPageSubBundle: Set<SearchBundle.SubBundle>?
    ): GplaySearchResult

    suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry>
}
