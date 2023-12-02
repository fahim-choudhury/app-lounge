package foundation.e.apps.data.application

import androidx.lifecycle.LiveData
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.cleanapk.data.download.Download
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Category
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.application.utils.CategoryType
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import retrofit2.Response

typealias GplaySearchResult = ResultSupreme<Pair<List<Application>, Set<SearchBundle.SubBundle>>>

interface ApplicationApi {
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

    suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): String?

    suspend fun updateFusedDownloadWithDownloadingInfo(
        origin: Origin,
        fusedDownload: FusedDownload
    )

    suspend fun getOSSDownloadInfo(id: String, version: String?): Response<Download>

}
