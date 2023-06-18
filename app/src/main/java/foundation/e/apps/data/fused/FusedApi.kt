package foundation.e.apps.data.fused

import androidx.lifecycle.LiveData
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.cleanapk.data.download.Download
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fused.data.FusedCategory
import foundation.e.apps.data.fused.data.FusedHome
import foundation.e.apps.data.fused.utils.CategoryType
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import retrofit2.Response

interface FusedApi {
    companion object {
        const val APP_TYPE_ANY = "any"
        const val APP_TYPE_OPEN = "open"
        const val APP_TYPE_PWA = "pwa"
    }

    /**
     * Check if list in all the FusedHome is empty.
     * If any list is not empty, send false.
     * Else (if all lists are empty) send true.
     */
    fun isHomesEmpty(fusedHomes: List<FusedHome>): Boolean
    fun getApplicationCategoryPreference(): List<String>

    suspend fun getHomeScreenData(
        authData: AuthData,
    ): LiveData<ResultSupreme<List<FusedHome>>>

    /*
        * Return three elements from the function.
        * - List<FusedCategory> : List of categories.
        * - String : String of application type - By default it is the value in preferences.
        * In case there is any failure, for a specific type in handleAllSourcesCategories(),
        * the string value is of that type.
        * - ResultStatus : ResultStatus - by default is ResultStatus.OK. But in case there is a failure in
        * any application category type, then it takes value of that failure.
        *
        * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
        */
    suspend fun getCategoriesList(
        type: CategoryType,
    ): Triple<List<FusedCategory>, String, ResultStatus>

    /**
     * Fetches search results from cleanAPK and GPlay servers and returns them
     * @param query Query
     * @param authData [AuthData]
     * @return A livedata Pair of list of non-nullable [FusedApp] and
     * a Boolean signifying if more search results are being loaded.
     * Observe this livedata to display new apps as they are fetched from the network.
     */
    fun getSearchResults(
        query: String,
        authData: AuthData
    ): LiveData<ResultSupreme<Pair<List<FusedApp>, Boolean>>>

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

    suspend fun getPWAApps(category: String): ResultSupreme<Pair<List<FusedApp>, String>>

    suspend fun getOpenSourceApps(category: String): ResultSupreme<Pair<List<FusedApp>, String>>

    suspend fun getNextStreamBundle(
        homeUrl: String,
        currentStreamBundle: StreamBundle,
    ): ResultSupreme<StreamBundle>

    suspend fun getAdjustedFirstCluster(
        streamBundle: StreamBundle,
        pointer: Int = 0,
    ): ResultSupreme<StreamCluster>

    suspend fun getNextStreamCluster(
        currentStreamCluster: StreamCluster,
    ): ResultSupreme<StreamCluster>

    suspend fun getPlayStoreApps(
        browseUrl: String,
    ): ResultSupreme<List<FusedApp>>

    /*
        * Function to search cleanapk using package name.
        * Will be used to handle f-droid deeplink.
        *
        * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5509
        */
    suspend fun getCleanapkAppDetails(packageName: String): Pair<FusedApp, ResultStatus>

    suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): Pair<List<FusedApp>, ResultStatus>

    /**
     * Filter out apps which are restricted, whose details cannot be fetched.
     * If an app is restricted, we do try to fetch the app details inside a
     * try-catch block. If that fails, we remove the app, else we keep it even
     * if it is restricted.
     *
     * Popular example: "com.skype.m2"
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5174
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    suspend fun filterRestrictedGPlayApps(
        authData: AuthData,
        appList: List<App>,
    ): ResultSupreme<List<FusedApp>>

    /**
     * Get different filter levels.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5720
     */
    suspend fun getAppFilterLevel(fusedApp: FusedApp, authData: AuthData?): FilterLevel

    /*
        * Similar to above method but uses Aurora OSS data class "App".
        */
    suspend fun getAppFilterLevel(app: App, authData: AuthData): FilterLevel

    suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<FusedApp, ResultStatus>

    /**
     * Get fused app installation status.
     * Applicable for both native apps and PWAs.
     *
     * Recommended to use this instead of [PkgManagerModule.getPackageStatus].
     */
    fun getFusedAppInstallationStatus(fusedApp: FusedApp): Status

    /**
     * @return true, if any change is found, otherwise false
     */
    fun isHomeDataUpdated(
        newHomeData: List<FusedHome>,
        oldHomeData: List<FusedHome>
    ): Boolean

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isAnyFusedAppUpdated(
        newFusedApps: List<FusedApp>,
        oldFusedApps: List<FusedApp>
    ): Boolean

    fun isAnyAppInstallStatusChanged(currentList: List<FusedApp>): Boolean
    fun isOpenSourceSelected(): Boolean

    suspend fun getAppsByCategory(authData: AuthData, category: String, nextPageUrl: String?): ResultSupreme<Pair<List<FusedApp>, String>>
}
