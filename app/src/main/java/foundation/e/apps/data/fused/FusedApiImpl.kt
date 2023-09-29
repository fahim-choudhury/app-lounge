/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.data.fused

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.aurora.gplayapi.Constants
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.SearchBundle
import com.aurora.gplayapi.data.models.StreamCluster
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.cleanapk.CleanApkDownloadInfoFetcher
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.cleanapk.data.app.Application
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.home.Home
import foundation.e.apps.data.cleanapk.data.home.HomeScreen
import foundation.e.apps.data.cleanapk.data.search.Search
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.AppTag
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.enums.isUnFiltered
import foundation.e.apps.data.fdroid.FdroidWebInterface
import foundation.e.apps.data.fused.FusedApi.Companion.APP_TYPE_ANY
import foundation.e.apps.data.fused.FusedApi.Companion.APP_TYPE_OPEN
import foundation.e.apps.data.fused.FusedApi.Companion.APP_TYPE_PWA
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fused.data.FusedCategory
import foundation.e.apps.data.fused.data.FusedHome
import foundation.e.apps.data.fused.data.Ratings
import foundation.e.apps.data.fused.utils.CategoryType
import foundation.e.apps.data.fused.utils.CategoryUtils
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.gplay.GplayStoreRepository
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.preference.PreferenceManagerModule
import foundation.e.apps.install.pkg.PWAManagerModule
import foundation.e.apps.install.pkg.PkgManagerModule
import foundation.e.apps.ui.home.model.HomeChildFusedAppDiffUtil
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

typealias FusedHomeDeferred = Deferred<ResultSupreme<List<FusedHome>>>

@Singleton
class FusedApiImpl @Inject constructor(
    private val pkgManagerModule: PkgManagerModule,
    private val pwaManagerModule: PWAManagerModule,
    private val preferenceManagerModule: PreferenceManagerModule,
    private val fdroidWebInterface: FdroidWebInterface,
    @Named("gplayRepository") private val gplayRepository: GplayStoreRepository,
    @Named("cleanApkAppsRepository") private val cleanApkAppsRepository: CleanApkRepository,
    @Named("cleanApkPWARepository") private val cleanApkPWARepository: CleanApkRepository,
    @ApplicationContext private val context: Context
) : FusedApi {

    companion object {
        private const val CATEGORY_TITLE_REPLACEABLE_CONJUNCTION = "&"
        private const val CATEGORY_OPEN_GAMES_ID = "game_open_games"
        private const val CATEGORY_OPEN_GAMES_TITLE = "Open games"
    }

    /**
     * Check if list in all the FusedHome is empty.
     * If any list is not empty, send false.
     * Else (if all lists are empty) send true.
     */
    override fun isHomesEmpty(fusedHomes: List<FusedHome>): Boolean {
        fusedHomes.forEach {
            if (it.list.isNotEmpty()) return false
        }
        return true
    }

    override fun getApplicationCategoryPreference(): List<String> {
        val prefs = mutableListOf<String>()
        if (preferenceManagerModule.isGplaySelected()) prefs.add(APP_TYPE_ANY)
        if (preferenceManagerModule.isOpenSourceSelected()) prefs.add(APP_TYPE_OPEN)
        if (preferenceManagerModule.isPWASelected()) prefs.add(APP_TYPE_PWA)
        return prefs
    }

    override suspend fun getHomeScreenData(
        authData: AuthData,
    ): LiveData<ResultSupreme<List<FusedHome>>> {

        val list = mutableListOf<FusedHome>()
        var resultGplay: FusedHomeDeferred? = null
        var resultOpenSource: FusedHomeDeferred? = null
        var resultPWA: FusedHomeDeferred? = null

        return liveData {
            coroutineScope {

                if (preferenceManagerModule.isGplaySelected()) {
                    resultGplay = async { loadHomeData(list, Source.GPLAY, authData) }
                }

                if (preferenceManagerModule.isOpenSourceSelected()) {
                    resultOpenSource = async { loadHomeData(list, Source.OPEN, authData) }
                }

                if (preferenceManagerModule.isPWASelected()) {
                    resultPWA = async { loadHomeData(list, Source.PWA, authData) }
                }

                resultGplay?.await()?.let {
                    emit(it)
                }
                resultOpenSource?.await()?.let {
                    emit(it)
                }
                resultPWA?.await()?.let {
                    emit(it)
                }
            }
        }
    }

    private suspend fun loadHomeData(
        priorList: MutableList<FusedHome>,
        source: Source,
        authData: AuthData,
    ): ResultSupreme<List<FusedHome>> {

        val result = when (source) {
            Source.GPLAY -> handleNetworkResult<List<FusedHome>> {
                priorList.addAll(fetchGPlayHome(authData))
                priorList
            }

            Source.OPEN -> handleNetworkResult {
                val response =
                    (cleanApkAppsRepository.getHomeScreenData() as Response<HomeScreen>).body()
                response?.home?.let {
                    priorList.addAll(generateCleanAPKHome(it, APP_TYPE_OPEN))
                }
                priorList
            }

            Source.PWA -> handleNetworkResult {
                val response =
                    (cleanApkPWARepository.getHomeScreenData() as Response<HomeScreen>).body()
                response?.home?.let {
                    priorList.addAll(generateCleanAPKHome(it, APP_TYPE_PWA))
                }
                priorList
            }
        }

        setHomeErrorMessage(result.getResultStatus(), source)
        priorList.sortByDescending {
            when (it.source) {
                APP_TYPE_OPEN -> 2
                APP_TYPE_PWA -> 1
                else -> 3
            }
        }
        return ResultSupreme.create(result.getResultStatus(), priorList)
    }

    private fun setHomeErrorMessage(apiStatus: ResultStatus, source: Source) {
        if (apiStatus != ResultStatus.OK) {
            apiStatus.message = when (source) {
                Source.GPLAY -> ("GPlay home loading error\n" + apiStatus.message).trim()
                Source.OPEN -> ("Open Source home loading error\n" + apiStatus.message).trim()
                Source.PWA -> ("PWA home loading error\n" + apiStatus.message).trim()
            }
        }
    }

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
    override suspend fun getCategoriesList(
        type: CategoryType,
    ): Triple<List<FusedCategory>, String, ResultStatus> {
        val categoriesList = mutableListOf<FusedCategory>()
        val preferredApplicationType = preferenceManagerModule.preferredApplicationType()
        var apiStatus: ResultStatus = ResultStatus.OK
        var applicationCategoryType = preferredApplicationType

        handleAllSourcesCategories(categoriesList, type).run {
            if (first != ResultStatus.OK) {
                apiStatus = first
                applicationCategoryType = second
            }
        }
        categoriesList.sortBy { item -> item.title.lowercase() }
        return Triple(categoriesList, applicationCategoryType, apiStatus)
    }

    /**
     * Fetches search results from cleanAPK and GPlay servers and returns them
     * @param query Query
     * @param authData [AuthData]
     * @return A livedata Pair of list of non-nullable [FusedApp] and
     * a Boolean signifying if more search results are being loaded.
     * Observe this livedata to display new apps as they are fetched from the network.
     */
    override suspend fun getCleanApkSearchResults(
        query: String,
        authData: AuthData
    ): ResultSupreme<Pair<List<FusedApp>, Boolean>> {
        /*
         * Returning livedata to improve performance, so that we do not have to wait forever
         * for all results to be fetched from network before showing them.
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
         */
        val packageSpecificResults = ArrayList<FusedApp>()
        var finalSearchResult: ResultSupreme<Pair<List<FusedApp>, Boolean>> = ResultSupreme.Error()

        fetchPackageSpecificResult(authData, query, packageSpecificResults)

        val searchResult = mutableListOf<FusedApp>()
        val cleanApkResults = mutableListOf<FusedApp>()

        if (preferenceManagerModule.isOpenSourceSelected()) {
            finalSearchResult = fetchOpenSourceSearchResult(
                cleanApkResults,
                query,
                searchResult,
                packageSpecificResults
            )
        }

        if (preferenceManagerModule.isPWASelected()) {
            finalSearchResult = fetchPWASearchResult(
                query,
                searchResult,
                packageSpecificResults
            )
        }
        return finalSearchResult
    }

    private suspend fun fetchPWASearchResult(
        query: String,
        searchResult: MutableList<FusedApp>,
        packageSpecificResults: ArrayList<FusedApp>
    ): ResultSupreme<Pair<List<FusedApp>, Boolean>> {
        val pwaApps: MutableList<FusedApp> = mutableListOf()
        val result = handleNetworkResult {
            val apps =
                cleanApkPWARepository.getSearchResult(query).body()?.apps
            apps?.apply {
                if (this.isNotEmpty()) {
                    pwaApps.addAll(this)
                }
            }
        }

        if (pwaApps.isNotEmpty() || result.getResultStatus() != ResultStatus.OK) {
            searchResult.addAll(pwaApps)
        }

        return ResultSupreme.create(
            result.getResultStatus(),
            Pair(
                filterWithKeywordSearch(
                    searchResult,
                    packageSpecificResults,
                    query
                ),
                preferenceManagerModule.isGplaySelected()
            )
        )
    }

    private suspend fun fetchOpenSourceSearchResult(
        cleanApkResults: MutableList<FusedApp>,
        query: String,
        searchResult: MutableList<FusedApp>,
        packageSpecificResults: ArrayList<FusedApp>
    ): ResultSupreme<Pair<List<FusedApp>, Boolean>> {
        val result = handleNetworkResult {
            cleanApkResults.addAll(getCleanAPKSearchResults(query))
            cleanApkResults
        }

        if (cleanApkResults.isNotEmpty()) {
            searchResult.addAll(cleanApkResults)
        }

        return ResultSupreme.create(
            result.getResultStatus(),
            Pair(
                filterWithKeywordSearch(
                    searchResult,
                    packageSpecificResults,
                    query
                ),
                preferenceManagerModule.isGplaySelected() || preferenceManagerModule.isPWASelected()
            )
        )
    }

    private suspend fun fetchPackageSpecificResult(
        authData: AuthData,
        query: String,
        packageSpecificResults: MutableList<FusedApp>
    ): ResultSupreme<Pair<List<FusedApp>, Boolean>> {
        var gplayPackageResult: FusedApp? = null
        var cleanapkPackageResult: FusedApp? = null

        val result = handleNetworkResult {
            if (preferenceManagerModule.isGplaySelected()) {
                gplayPackageResult = getGplayPackagResult(query, authData)
            }

            if (preferenceManagerModule.isOpenSourceSelected()) {
                cleanapkPackageResult = getCleanApkPackageResult(query)
            }
        }

        /*
         * Currently only show open source package result if exists in both fdroid and gplay.
         * This is temporary.
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5783
         */
        cleanapkPackageResult?.let { packageSpecificResults.add(it) } ?: run {
            gplayPackageResult?.let { packageSpecificResults.add(it) }
        }

        if (preferenceManagerModule.isGplaySelected()) {
            packageSpecificResults.add(FusedApp(isPlaceHolder = true))
        }

        /*
         * If there was a timeout, return it and don't try to fetch anything else.
         * Also send true in the pair to signal more results being loaded.
         */
        if (result.getResultStatus() != ResultStatus.OK) {
            return ResultSupreme.create(
                result.getResultStatus(),
                Pair(packageSpecificResults, false)
            )
        }
        return ResultSupreme.create(result.getResultStatus(), Pair(packageSpecificResults, true))
    }

    /*
             * The list packageSpecificResults may contain apps with duplicate package names.
             * Example, "org.telegram.messenger" will result in "Telegram" app from Play Store
             * and "Telegram FOSS" from F-droid. We show both of them at the top.
             *
             * But for the other keyword related search results, we do not allow duplicate package names.
             * We also filter out apps which are already present in packageSpecificResults list.
             */
    private fun filterWithKeywordSearch(
        list: List<FusedApp>,
        packageSpecificResults: List<FusedApp>,
        query: String
    ): List<FusedApp> {
        val filteredResults = list.distinctBy { it.package_name }
            .filter { packageSpecificResults.isEmpty() || it.package_name != query }

        val finalList = (packageSpecificResults + filteredResults).toMutableList()
        finalList.removeIf { it.isPlaceHolder }
        if (preferenceManagerModule.isGplaySelected()) {
            finalList.add(FusedApp(isPlaceHolder = true))
        }

        return finalList
    }

    private suspend fun getCleanApkPackageResult(
        query: String,
    ): FusedApp? {
        getCleanapkSearchResult(query).let {
            if (it.isSuccess() && it.data!!.package_name.isNotBlank()) {
                return it.data!!
            }
        }
        return null
    }

    private suspend fun getGplayPackagResult(
        query: String,
        authData: AuthData,
    ): FusedApp? {
        try {
            getApplicationDetails(query, query, authData, Origin.GPLAY).let {
                if (it.second == ResultStatus.OK && it.first.package_name.isNotEmpty()) {
                    return it.first
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return null
    }

    /*
     * Method to search cleanapk based on package name.
     * This is to be only used for showing an entry in search results list.
     * DO NOT use this to show info on ApplicationFragment as it will not have all the required
     * information to show for an app.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/2629
     */
    private suspend fun getCleanapkSearchResult(packageName: String): ResultSupreme<FusedApp> {
        var fusedApp = FusedApp()
        val result = handleNetworkResult {
            val result = cleanApkAppsRepository.getSearchResult(
                packageName,
                "package_name"
            ).body()

            if (result?.apps?.isNotEmpty() == true && result.numberOfResults == 1) {
                fusedApp = result.apps[0]
            }
        }
        return ResultSupreme.create(result.getResultStatus(), fusedApp)
    }

    override suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry> {
        var searchSuggesions = listOf<SearchSuggestEntry>()
        handleNetworkResult {
            searchSuggesions = gplayRepository.getSearchSuggestions(query)
        }

        return searchSuggesions
    }

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

    override suspend fun getPWAApps(category: String): ResultSupreme<Pair<List<FusedApp>, String>> {
        val list = mutableListOf<FusedApp>()
        val result = handleNetworkResult {
            val response = getPWAAppsResponse(category)
            response?.apps?.forEach {
                it.updateStatus()
                it.updateType()
                it.updateFilterLevel(null)
                list.add(it)
            }
        }
        return ResultSupreme.create(result.getResultStatus(), Pair(list, ""))
    }

    override suspend fun getOpenSourceApps(category: String): ResultSupreme<Pair<List<FusedApp>, String>> {
        val list = mutableListOf<FusedApp>()
        val result = handleNetworkResult {
            val response = getOpenSourceAppsResponse(category)
            response?.apps?.forEach {
                it.updateStatus()
                it.updateType()
                it.updateFilterLevel(null)
                list.add(it)
            }
        }
        return ResultSupreme.create(result.getResultStatus(), Pair(list, ""))
    }

    /*
     * Function to search cleanapk using package name.
     * Will be used to handle f-droid deeplink.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5509
     */
    override suspend fun getCleanapkAppDetails(packageName: String): Pair<FusedApp, ResultStatus> {
        var fusedApp = FusedApp()
        val result = handleNetworkResult {
            val result = cleanApkAppsRepository.getSearchResult(
                packageName,
                "package_name"
            ).body()

            if (result?.apps?.isNotEmpty() == true && result.numberOfResults == 1) {
                fusedApp =
                    (cleanApkAppsRepository.getAppDetails(result.apps[0]._id) as Response<Application>).body()?.app
                        ?: FusedApp()
            }
            fusedApp.updateFilterLevel(null)
        }
        return Pair(fusedApp, result.getResultStatus())
    }

    // Warning - GPlay results may not have proper geo-restriction information.
    override suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): Pair<List<FusedApp>, ResultStatus> {
        val list = mutableListOf<FusedApp>()

        val response: Pair<List<FusedApp>, ResultStatus> =
            if (origin == Origin.CLEANAPK) {
                getAppDetailsListFromCleanapk(packageNameList)
            } else {
                getAppDetailsListFromGPlay(packageNameList, authData)
            }

        response.first.forEach {
            if (it.package_name.isNotBlank()) {
                it.updateStatus()
                it.updateType()
                list.add(it)
            }
        }

        return Pair(list, response.second)
    }

    /*
     * Get app details of a list of apps from cleanapk.
     * Returns list of FusedApp and ResultStatus - which will reflect timeout if even one app fails.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    private suspend fun getAppDetailsListFromCleanapk(
        packageNameList: List<String>,
    ): Pair<List<FusedApp>, ResultStatus> {
        var status = ResultStatus.OK
        val fusedAppList = mutableListOf<FusedApp>()

        /*
         * Fetch result of each cleanapk search with separate timeout,
         * i.e. check timeout for individual package query.
         */
        for (packageName in packageNameList) {
            val result = handleNetworkResult {
                cleanApkAppsRepository.getSearchResult(
                    packageName,
                    "package_name"
                ).body()?.run {
                    if (apps.isNotEmpty() && numberOfResults == 1) {
                        fusedAppList.add(
                            apps[0].apply {
                                updateFilterLevel(null)
                            }
                        )
                    }
                }
            }

            status = result.getResultStatus()

            /*
             * If status is not ok, immediately return.
             */
            if (status != ResultStatus.OK) {
                return Pair(fusedAppList, status)
            }
        }

        return Pair(fusedAppList, status)
    }

    /*
     * Get app details of a list of apps from Google Play store.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    private suspend fun getAppDetailsListFromGPlay(
        packageNameList: List<String>,
        authData: AuthData,
    ): Pair<List<FusedApp>, ResultStatus> {
        val fusedAppList = mutableListOf<FusedApp>()

        /*
         * Old code moved from getApplicationDetails()
         */
        val result = handleNetworkResult {
            gplayRepository.getAppsDetails(packageNameList).forEach { app ->
                /*
                 * Some apps are restricted to locations. Example "com.skype.m2".
                 * For restricted apps, check if it is possible to get their specific app info.
                 *
                 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5174
                 */
                val filter = getAppFilterLevel(app, authData)
                if (filter.isUnFiltered()) {
                    fusedAppList.add(
                        app.transformToFusedApp().apply {
                            filterLevel = filter
                        }
                    )
                }
            }
        }

        return Pair(fusedAppList, result.getResultStatus())
    }

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
    override suspend fun filterRestrictedGPlayApps(
        authData: AuthData,
        appList: List<App>,
    ): ResultSupreme<List<FusedApp>> {
        val filteredFusedApps = mutableListOf<FusedApp>()
        return handleNetworkResult {
            appList.forEach {
                val filter = getAppFilterLevel(it, authData)
                if (filter.isUnFiltered()) {
                    filteredFusedApps.add(
                        it.transformToFusedApp().apply {
                            this.filterLevel = filter
                        }
                    )
                }
            }
            filteredFusedApps
        }
    }

    /**
     * Get different filter levels.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5720
     */
    override suspend fun getAppFilterLevel(fusedApp: FusedApp, authData: AuthData?): FilterLevel {
        return when {
            fusedApp.package_name.isBlank() -> FilterLevel.UNKNOWN
            !fusedApp.isFree && fusedApp.price.isBlank() -> FilterLevel.UI
            fusedApp.origin == Origin.CLEANAPK -> FilterLevel.NONE
            !isRestricted(fusedApp) -> FilterLevel.NONE
            authData == null -> FilterLevel.UNKNOWN // cannot determine for gplay app
            !isApplicationVisible(fusedApp) -> FilterLevel.DATA
            fusedApp.originalSize == 0L -> FilterLevel.UI
            !isDownloadable(fusedApp) -> FilterLevel.UI
            else -> FilterLevel.NONE
        }
    }

    /**
     * Some apps are simply not visible.
     * Example: com.skype.m2
     */
    private suspend fun isApplicationVisible(fusedApp: FusedApp): Boolean {
        return kotlin.runCatching { gplayRepository.getAppDetails(fusedApp.package_name) }.isSuccess
    }

    /**
     * Some apps are visible but not downloadable.
     * Example: com.riotgames.league.wildrift
     */
    private suspend fun isDownloadable(fusedApp: FusedApp): Boolean {
        return kotlin.runCatching {
            gplayRepository.getDownloadInfo(
                fusedApp.package_name,
                fusedApp.latest_version_code,
                fusedApp.offer_type,
            )
        }.isSuccess
    }

    private fun isRestricted(fusedApp: FusedApp): Boolean {
        return fusedApp.restriction != Constants.Restriction.NOT_RESTRICTED
    }

    /*
     * Similar to above method but uses Aurora OSS data class "App".
     */
    override suspend fun getAppFilterLevel(app: App, authData: AuthData): FilterLevel {
        return getAppFilterLevel(app.transformToFusedApp(), authData)
    }

    /*
     * Handy method to run on an instance of FusedApp to update its filter level.
     */
    private suspend fun FusedApp.updateFilterLevel(authData: AuthData?) {
        this.filterLevel = getAppFilterLevel(this, authData)
    }

    override suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<FusedApp, ResultStatus> {

        var response: FusedApp? = null

        val result = handleNetworkResult {
            response = if (origin == Origin.CLEANAPK) {
                (cleanApkAppsRepository.getAppDetails(id) as Response<Application>).body()?.app
            } else {
                val app = gplayRepository.getAppDetails(packageName) as App?
                app?.transformToFusedApp()
            }
            response?.let {
                it.updateStatus()
                it.updateType()
                it.updateSource()
                it.updateFilterLevel(authData)
            }
            response
        }

        return Pair(result.data ?: FusedApp(), result.getResultStatus())
    }

    /*
     * Function to populate a given category list, from all GPlay categories, open source categories,
     * and PWAs.
     *
     * Returns: Pair of:
     * - ResultStatus - by default ResultStatus.OK, but can be different in case of an error in any category.
     * - String - Application category type having error. If no error, then blank string.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    private suspend fun handleAllSourcesCategories(
        categoriesList: MutableList<FusedCategory>,
        type: CategoryType,
    ): Pair<ResultStatus, String> {
        var apiStatus = ResultStatus.OK
        var errorApplicationCategory = ""

        if (preferenceManagerModule.isOpenSourceSelected()) {
            val openSourceCategoryResult = fetchOpenSourceCategories(type)
            categoriesList.addAll(openSourceCategoryResult.second)
            apiStatus = openSourceCategoryResult.first
            errorApplicationCategory = openSourceCategoryResult.third
        }

        if (preferenceManagerModule.isPWASelected()) {
            val pwaCategoriesResult = fetchPWACategories(type)
            categoriesList.addAll(pwaCategoriesResult.second)
            apiStatus = pwaCategoriesResult.first
            errorApplicationCategory = pwaCategoriesResult.third
        }

        if (preferenceManagerModule.isGplaySelected()) {
            val gplayCategoryResult = fetchGplayCategories(
                type,
            )
            categoriesList.addAll(gplayCategoryResult.data ?: listOf())
            apiStatus = gplayCategoryResult.getResultStatus()
            errorApplicationCategory = APP_TYPE_ANY
        }

        return Pair(apiStatus, errorApplicationCategory)
    }

    private suspend fun fetchGplayCategories(
        type: CategoryType,
    ): ResultSupreme<List<FusedCategory>> {
        val categoryList = mutableListOf<FusedCategory>()

        return handleNetworkResult {
            val playResponse = gplayRepository.getCategories(type).map { app ->
                val category = app.transformToFusedCategory()
                updateCategoryDrawable(category)
                category
            }
            categoryList.addAll(playResponse)
            categoryList
        }
    }

    private suspend fun fetchPWACategories(
        type: CategoryType,
    ): Triple<ResultStatus, List<FusedCategory>, String> {
        val fusedCategoriesList = mutableListOf<FusedCategory>()
        val result = handleNetworkResult {
            getPWAsCategories()?.let {
                fusedCategoriesList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        it, type, AppTag.PWA(context.getString(R.string.pwa))
                    )
                )
            }
        }

        return Triple(result.getResultStatus(), fusedCategoriesList, APP_TYPE_PWA)
    }

    private suspend fun fetchOpenSourceCategories(
        type: CategoryType,
    ): Triple<ResultStatus, List<FusedCategory>, String> {
        val fusedCategoryList = mutableListOf<FusedCategory>()
        val result = handleNetworkResult {
            getOpenSourceCategories()?.let {
                fusedCategoryList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        it,
                        type,
                        AppTag.OpenSource(context.getString(R.string.open_source))
                    )
                )
            }
        }

        return Triple(result.getResultStatus(), fusedCategoryList, APP_TYPE_OPEN)
    }

    private fun updateCategoryDrawable(
        category: FusedCategory,
    ) {
        category.drawable =
            getCategoryIconResource(getCategoryIconName(category))
    }

    private fun getCategoryIconName(category: FusedCategory): String {
        var categoryTitle = if (category.tag.getOperationalTag().contentEquals(AppTag.GPlay().getOperationalTag()))
            category.id else category.title

        if (categoryTitle.contains(CATEGORY_TITLE_REPLACEABLE_CONJUNCTION)) {
            categoryTitle = categoryTitle.replace(CATEGORY_TITLE_REPLACEABLE_CONJUNCTION, "and")
        }
        categoryTitle = categoryTitle.replace(' ', '_')
        return categoryTitle.lowercase()
    }

    private fun getFusedCategoryBasedOnCategoryType(
        categories: Categories,
        categoryType: CategoryType,
        tag: AppTag
    ): List<FusedCategory> {
        return when (categoryType) {
            CategoryType.APPLICATION -> {
                getAppsCategoriesAsFusedCategory(categories, tag)
            }

            CategoryType.GAMES -> {
                getGamesCategoriesAsFusedCategory(categories, tag)
            }
        }
    }

    private fun getAppsCategoriesAsFusedCategory(
        categories: Categories,
        tag: AppTag
    ): List<FusedCategory> {
        return categories.apps.map { category ->
            createFusedCategoryFromCategory(category, categories, tag)
        }
    }

    private fun getGamesCategoriesAsFusedCategory(
        categories: Categories,
        tag: AppTag
    ): List<FusedCategory> {
        return categories.games.map { category ->
            createFusedCategoryFromCategory(category, categories, tag)
        }
    }

    private fun createFusedCategoryFromCategory(
        category: String,
        categories: Categories,
        tag: AppTag
    ): FusedCategory {
        return FusedCategory(
            id = category,
            title = getCategoryTitle(category, categories),
            drawable = getCategoryIconResource(category),
            tag = tag
        )
    }

    private fun getCategoryIconResource(category: String): Int {
        return CategoryUtils.provideAppsCategoryIconResource(category)
    }

    private fun getCategoryTitle(category: String, categories: Categories): String {
        return if (category.contentEquals(CATEGORY_OPEN_GAMES_ID)) {
            CATEGORY_OPEN_GAMES_TITLE
        } else {
            categories.translations.getOrDefault(category, "")
        }
    }

    private suspend fun getPWAsCategories(): Categories? {
        return cleanApkPWARepository.getCategories().body()
    }

    private suspend fun getOpenSourceCategories(): Categories? {
        return cleanApkAppsRepository.getCategories().body()
    }

    private suspend fun getOpenSourceAppsResponse(category: String): Search? {
        return cleanApkAppsRepository.getAppsByCategory(
            category,
        ).body()
    }

    private suspend fun getPWAAppsResponse(category: String): Search? {
        return cleanApkPWARepository.getAppsByCategory(
            category,
        ).body()
    }

    private fun Category.transformToFusedCategory(): FusedCategory {
        val id = this.browseUrl.substringAfter("cat=").substringBefore("&c=")
        return FusedCategory(
            id = id.lowercase(),
            title = this.title,
            browseUrl = this.browseUrl,
            imageUrl = this.imageUrl,
        )
    }

    /*
     * Search-related internal functions
     */

    private suspend fun getCleanAPKSearchResults(
        keyword: String,
        source: String = CleanApkRetrofit.APP_SOURCE_FOSS,
    ): List<FusedApp> {
        val list = mutableListOf<FusedApp>()
        val response =
            cleanApkAppsRepository.getSearchResult(keyword).body()?.apps

        response?.forEach {
            it.updateStatus()
            it.updateType()
            it.source =
                if (source.contentEquals(CleanApkRetrofit.APP_SOURCE_FOSS)) "Open Source" else "PWA"
            list.add(it)
        }
        return list
    }

    override suspend fun getGplaySearchResult(
        query: String,
        nextPageSubBundle: Set<SearchBundle.SubBundle>?
    ): GplaySearchResult {
        return handleNetworkResult {
            val searchResults =
                gplayRepository.getSearchResult(query, nextPageSubBundle?.toMutableSet())

            if (!preferenceManagerModule.isGplaySelected()) {
                return@handleNetworkResult Pair(listOf<FusedApp>(), setOf<SearchBundle.SubBundle>())
            }

            val fusedAppList =
                searchResults.first.map { app -> replaceWithFDroid(app) }.toMutableList()

            if (searchResults.second.isNotEmpty()) {
                fusedAppList.add(FusedApp(isPlaceHolder = true))
            }

            return@handleNetworkResult Pair(fusedAppList.toList(), searchResults.second.toSet())
        }
    }

    /*
         * This function will replace a GPlay app with F-Droid app if exists,
         * else will show the GPlay app itself.
         */
    private suspend fun replaceWithFDroid(gPlayApp: App): FusedApp {
        val gPlayFusedApp = gPlayApp.transformToFusedApp()
        val response = fdroidWebInterface.getFdroidApp(gPlayFusedApp.package_name)
        if (response.isSuccessful) {
            val fdroidApp = getCleanApkPackageResult(gPlayFusedApp.package_name)?.apply {
                updateSource()
                isGplayReplaced = true
            }
            return fdroidApp ?: gPlayFusedApp
        }

        return gPlayFusedApp
    }

    /*
     * Home screen-related internal functions
     */

    private suspend fun generateCleanAPKHome(home: Home, appType: String): List<FusedHome> {
        val list = mutableListOf<FusedHome>()
        val headings = if (appType == APP_TYPE_OPEN) {
            mapOf(
                "top_updated_apps" to context.getString(R.string.top_updated_apps),
                "top_updated_games" to context.getString(R.string.top_updated_games),
                "popular_apps_in_last_24_hours" to context.getString(R.string.popular_apps_in_last_24_hours),
                "popular_games_in_last_24_hours" to context.getString(R.string.popular_games_in_last_24_hours),
                "discover" to context.getString(R.string.discover)
            )
        } else {
            mapOf(
                "popular_apps" to context.getString(R.string.popular_apps),
                "popular_games" to context.getString(R.string.popular_games),
                "discover" to context.getString(R.string.discover_pwa)
            )
        }
        headings.forEach { (key, value) ->
            when (key) {
                "top_updated_apps" -> {
                    if (home.top_updated_apps.isNotEmpty()) {
                        home.top_updated_apps.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(FusedHome(value, home.top_updated_apps))
                    }
                }

                "top_updated_games" -> {
                    if (home.top_updated_games.isNotEmpty()) {
                        home.top_updated_games.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(FusedHome(value, home.top_updated_games))
                    }
                }

                "popular_apps" -> {
                    if (home.popular_apps.isNotEmpty()) {
                        home.popular_apps.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(FusedHome(value, home.popular_apps))
                    }
                }

                "popular_games" -> {
                    if (home.popular_games.isNotEmpty()) {
                        home.popular_games.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(FusedHome(value, home.popular_games))
                    }
                }

                "popular_apps_in_last_24_hours" -> {
                    if (home.popular_apps_in_last_24_hours.isNotEmpty()) {
                        home.popular_apps_in_last_24_hours.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(FusedHome(value, home.popular_apps_in_last_24_hours))
                    }
                }

                "popular_games_in_last_24_hours" -> {
                    if (home.popular_games_in_last_24_hours.isNotEmpty()) {
                        home.popular_games_in_last_24_hours.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(FusedHome(value, home.popular_games_in_last_24_hours))
                    }
                }

                "discover" -> {
                    if (home.discover.isNotEmpty()) {
                        home.discover.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(FusedHome(value, home.discover))
                    }
                }
            }
        }
        return list.map {
            it.source = appType
            it
        }
    }

    private suspend fun fetchGPlayHome(authData: AuthData): List<FusedHome> {
        val list = mutableListOf<FusedHome>()
        val gplayHomeData = gplayRepository.getHomeScreenData() as Map<String, List<App>>
        gplayHomeData.map {
            val fusedApps = it.value.map { app ->
                app.transformToFusedApp().apply {
                    updateFilterLevel(authData)
                }
            }
            list.add(FusedHome(it.key, fusedApps))
        }
        Timber.d("===> $list")
        return list
    }

    /*
     * FusedApp-related internal extensions and functions
     */

    private fun App.transformToFusedApp(): FusedApp {
        val app = FusedApp(
            _id = this.id.toString(),
            author = this.developerName,
            category = this.categoryName,
            description = this.description,
            perms = this.permissions,
            icon_image_path = this.iconArtwork.url,
            last_modified = this.updatedOn,
            latest_version_code = this.versionCode,
            latest_version_number = this.versionName,
            name = this.displayName,
            other_images_path = this.screenshots.transformToList(),
            package_name = this.packageName,
            ratings = Ratings(
                usageQualityScore =
                this.labeledRating.run {
                    if (isNotEmpty()) {
                        this.replace(",", ".").toDoubleOrNull() ?: -1.0
                    } else -1.0
                }
            ),
            offer_type = this.offerType,
            origin = Origin.GPLAY,
            shareUrl = this.shareUrl,
            originalSize = this.size,
            appSize = Formatter.formatFileSize(context, this.size),
            isFree = this.isFree,
            price = this.price,
            restriction = this.restriction,
        )
        app.updateStatus()
        return app
    }

    /**
     * Get fused app installation status.
     * Applicable for both native apps and PWAs.
     *
     * Recommended to use this instead of [PkgManagerModule.getPackageStatus].
     */
    override fun getFusedAppInstallationStatus(fusedApp: FusedApp): Status {
        return if (fusedApp.is_pwa) {
            pwaManagerModule.getPwaStatus(fusedApp)
        } else {
            pkgManagerModule.getPackageStatus(fusedApp.package_name, fusedApp.latest_version_code)
        }
    }

    private fun FusedApp.updateStatus() {
        if (this.status != Status.INSTALLATION_ISSUE) {
            this.status = getFusedAppInstallationStatus(this)
        }
    }

    private fun FusedApp.updateType() {
        this.type = if (this.is_pwa) Type.PWA else Type.NATIVE
    }

    private fun FusedApp.updateSource() {
        this.apply {
            source = if (origin == Origin.CLEANAPK && is_pwa) context.getString(R.string.pwa)
            else if (origin == Origin.CLEANAPK) context.getString(R.string.open_source)
            else ""
        }
    }

    private fun MutableList<Artwork>.transformToList(): List<String> {
        val list = mutableListOf<String>()
        this.forEach {
            list.add(it.url)
        }
        return list
    }

    /**
     * @return true, if any change is found, otherwise false
     */
    override fun isHomeDataUpdated(
        newHomeData: List<FusedHome>,
        oldHomeData: List<FusedHome>
    ): Boolean {
        if (newHomeData.size != oldHomeData.size) {
            return true
        }

        oldHomeData.forEach {
            val fusedHome = newHomeData[oldHomeData.indexOf(it)]
            if (!it.title.contentEquals(fusedHome.title) || areFusedAppsUpdated(it, fusedHome)) {
                return true
            }
        }
        return false
    }

    private fun areFusedAppsUpdated(
        oldFusedHome: FusedHome,
        newFusedHome: FusedHome,
    ): Boolean {
        val fusedAppDiffUtil = HomeChildFusedAppDiffUtil()
        if (oldFusedHome.list.size != newFusedHome.list.size) {
            return true
        }

        oldFusedHome.list.forEach { oldFusedApp ->
            val indexOfOldFusedApp = oldFusedHome.list.indexOf(oldFusedApp)
            val fusedApp = newFusedHome.list[indexOfOldFusedApp]
            if (!fusedAppDiffUtil.areContentsTheSame(oldFusedApp, fusedApp)) {
                return true
            }
        }
        return false
    }

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    override fun isAnyFusedAppUpdated(
        newFusedApps: List<FusedApp>,
        oldFusedApps: List<FusedApp>
    ): Boolean {
        val fusedAppDiffUtil = HomeChildFusedAppDiffUtil()
        if (newFusedApps.size != oldFusedApps.size) {
            return true
        }

        newFusedApps.forEach {
            val indexOfNewFusedApp = newFusedApps.indexOf(it)
            if (!fusedAppDiffUtil.areContentsTheSame(it, oldFusedApps[indexOfNewFusedApp])) {
                return true
            }
        }
        return false
    }

    override fun isAnyAppInstallStatusChanged(currentList: List<FusedApp>): Boolean {
        currentList.forEach {
            if (it.status == Status.INSTALLATION_ISSUE) {
                return@forEach
            }
            val currentAppStatus =
                pkgManagerModule.getPackageStatus(it.package_name, it.latest_version_code)
            if (it.status != currentAppStatus) {
                return true
            }
        }
        return false
    }

    override fun isOpenSourceSelected() = preferenceManagerModule.isOpenSourceSelected()
    override suspend fun getGplayAppsByCategory(
        authData: AuthData,
        category: String,
        pageUrl: String?
    ): ResultSupreme<Pair<List<FusedApp>, String>> {
        var fusedAppList: MutableList<FusedApp> = mutableListOf()
        var nextPageUrl = ""

        return handleNetworkResult {
            val streamCluster =
                gplayRepository.getAppsByCategory(category, pageUrl) as StreamCluster

            val filteredAppList = filterRestrictedGPlayApps(authData, streamCluster.clusterAppList)
            filteredAppList.data?.let {
                fusedAppList = it.toMutableList()
            }

            nextPageUrl = streamCluster.clusterNextPageUrl
            if (!nextPageUrl.isNullOrEmpty()) {
                fusedAppList.add(FusedApp(isPlaceHolder = true))
            }
            Pair(fusedAppList, nextPageUrl)
        }
    }
}
