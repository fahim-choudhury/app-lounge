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

    suspend fun getHomeScreenData(
        authData: AuthData,
    ): LiveData<ResultSupreme<List<Home>>>

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
    ): Triple<List<Category>, String, ResultStatus>

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

    suspend fun getPWAApps(category: String): ResultSupreme<Pair<List<Application>, String>>

    suspend fun getOpenSourceApps(category: String): ResultSupreme<Pair<List<Application>, String>>

    /*
        * Function to search cleanapk using package name.
        * Will be used to handle f-droid deeplink.
        *
        * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5509
        */
    suspend fun getCleanapkAppDetails(packageName: String): Pair<Application, ResultStatus>

    suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): Pair<List<Application>, ResultStatus>

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
    ): ResultSupreme<List<Application>>

    /**
     * Get different filter levels.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5720
     */
    suspend fun getAppFilterLevel(application: Application, authData: AuthData?): FilterLevel

    /*
        * Similar to above method but uses Aurora OSS data class "App".
        */
    suspend fun getAppFilterLevel(app: App, authData: AuthData): FilterLevel

    suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<Application, ResultStatus>

    /**
     * Get fused app installation status.
     * Applicable for both native apps and PWAs.
     *
     * Recommended to use this instead of [PkgManagerModule.getPackageStatus].
     */
    fun getFusedAppInstallationStatus(application: Application): Status

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isAnyFusedAppUpdated(
        newApplications: List<Application>,
        oldApplications: List<Application>
    ): Boolean

    fun isAnyAppInstallStatusChanged(currentList: List<Application>): Boolean
    fun isOpenSourceSelected(): Boolean

    suspend fun getGplayAppsByCategory(authData: AuthData, category: String, pageUrl: String?): ResultSupreme<Pair<List<Application>, String>>
}
