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
import com.aurora.gplayapi.data.models.Category as GplayapiCategory
import com.aurora.gplayapi.data.models.SearchBundle
import com.aurora.gplayapi.data.models.StreamCluster
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.cleanapk.CleanApkDownloadInfoFetcher
import foundation.e.apps.data.cleanapk.data.app.Application as CleanApkApplication
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.home.Home as CleanApkHome
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
import foundation.e.apps.data.fused.ApplicationApi.Companion.APP_TYPE_ANY
import foundation.e.apps.data.fused.ApplicationApi.Companion.APP_TYPE_OPEN
import foundation.e.apps.data.fused.ApplicationApi.Companion.APP_TYPE_PWA
import foundation.e.apps.data.fused.data.Application
import foundation.e.apps.data.fused.data.Category
import foundation.e.apps.data.fused.data.Home
import foundation.e.apps.data.fused.data.Ratings
import foundation.e.apps.data.fused.utils.CategoryType
import foundation.e.apps.data.fused.utils.CategoryUtils
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.preference.PreferenceManagerModule
import foundation.e.apps.install.pkg.PWAManagerModule
import foundation.e.apps.install.pkg.PkgManagerModule
import foundation.e.apps.ui.home.model.HomeChildFusedAppDiffUtil
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

typealias FusedHomeDeferred = Deferred<ResultSupreme<List<Home>>>

@Singleton
class ApplicationApiImpl @Inject constructor(
    private val pkgManagerModule: PkgManagerModule,
    private val pwaManagerModule: PWAManagerModule,
    private val preferenceManagerModule: PreferenceManagerModule,
    @Named("gplayRepository") private val gplayRepository: PlayStoreRepository,
    @Named("cleanApkAppsRepository") private val cleanApkAppsRepository: CleanApkRepository,
    @Named("cleanApkPWARepository") private val cleanApkPWARepository: CleanApkRepository,
    @ApplicationContext private val context: Context
) : ApplicationApi {

    companion object {
        private const val CATEGORY_TITLE_REPLACEABLE_CONJUNCTION = "&"
        private const val CATEGORY_OPEN_GAMES_ID = "game_open_games"
        private const val CATEGORY_OPEN_GAMES_TITLE = "Open games"
        private const val THRESHOLD_LIMITED_RESULT_HOME_PAGE = 4
        private const val KEYWORD_TEST_SEARCH = "facebook"
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
    ): LiveData<ResultSupreme<List<Home>>> {

        val list = mutableListOf<Home>()
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
        priorList: MutableList<Home>,
        source: Source,
        authData: AuthData,
    ): ResultSupreme<List<Home>> {

        val result = when (source) {
            Source.GPLAY -> handleNetworkResult<List<Home>> {
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
    ): Triple<List<Category>, String, ResultStatus> {
        val categoriesList = mutableListOf<Category>()
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
     * @return A livedata Pair of list of non-nullable [Application] and
     * a Boolean signifying if more search results are being loaded.
     * Observe this livedata to display new apps as they are fetched from the network.
     */
    override suspend fun getCleanApkSearchResults(
        query: String,
        authData: AuthData
    ): ResultSupreme<Pair<List<Application>, Boolean>> {
        /*
         * Returning livedata to improve performance, so that we do not have to wait forever
         * for all results to be fetched from network before showing them.
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
         */
        val packageSpecificResults = ArrayList<Application>()
        var finalSearchResult: ResultSupreme<Pair<List<Application>, Boolean>> = ResultSupreme.Error()

        fetchPackageSpecificResult(authData, query, packageSpecificResults)

        val searchResult = mutableListOf<Application>()
        val cleanApkResults = mutableListOf<Application>()

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
        searchResult: MutableList<Application>,
        packageSpecificResults: ArrayList<Application>
    ): ResultSupreme<Pair<List<Application>, Boolean>> {
        val pwaApps: MutableList<Application> = mutableListOf()
        val result = handleNetworkResult {
            val apps =
                cleanApkPWARepository.getSearchResult(query).body()?.apps
            apps?.forEach {
                it.updateStatus()
                it.updateType()
                it.updateSource()
                pwaApps.add(it)
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
        cleanApkResults: MutableList<Application>,
        query: String,
        searchResult: MutableList<Application>,
        packageSpecificResults: ArrayList<Application>
    ): ResultSupreme<Pair<List<Application>, Boolean>> {
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
        packageSpecificResults: MutableList<Application>
    ): ResultSupreme<Pair<List<Application>, Boolean>> {
        var gplayPackageResult: Application? = null
        var cleanapkPackageResult: Application? = null

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
            packageSpecificResults.add(Application(isPlaceHolder = true))
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
        list: List<Application>,
        packageSpecificResults: List<Application>,
        query: String
    ): List<Application> {
        val filteredResults = list.distinctBy { it.package_name }
            .filter { packageSpecificResults.isEmpty() || it.package_name != query }

        val finalList = (packageSpecificResults + filteredResults).toMutableList()
        finalList.removeIf { it.isPlaceHolder }
        if (preferenceManagerModule.isGplaySelected()) {
            finalList.add(Application(isPlaceHolder = true))
        }

        return finalList
    }

    private suspend fun getCleanApkPackageResult(
        query: String,
    ): Application? {
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
    ): Application? {
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
    private suspend fun getCleanapkSearchResult(packageName: String): ResultSupreme<Application> {
        var application = Application()
        val result = handleNetworkResult {
            val result = cleanApkAppsRepository.getSearchResult(
                packageName,
                "package_name"
            ).body()

            if (result?.apps?.isNotEmpty() == true && result.numberOfResults == 1) {
                application = result.apps[0]
            }
        }
        return ResultSupreme.create(result.getResultStatus(), application)
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

    override suspend fun getPWAApps(category: String): ResultSupreme<Pair<List<Application>, String>> {
        val list = mutableListOf<Application>()
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

    override suspend fun getOpenSourceApps(category: String): ResultSupreme<Pair<List<Application>, String>> {
        val list = mutableListOf<Application>()
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
    override suspend fun getCleanapkAppDetails(packageName: String): Pair<Application, ResultStatus> {
        var application = Application()
        val result = handleNetworkResult {
            val result = cleanApkAppsRepository.getSearchResult(
                packageName,
                "package_name"
            ).body()

            if (result?.apps?.isNotEmpty() == true && result.numberOfResults == 1) {
                application =
                    (cleanApkAppsRepository.getAppDetails(result.apps[0]._id) as Response<CleanApkApplication>).body()?.app
                        ?: Application()
            }
            application.updateFilterLevel(null)
        }
        return Pair(application, result.getResultStatus())
    }

    // Warning - GPlay results may not have proper geo-restriction information.
    override suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): Pair<List<Application>, ResultStatus> {
        val list = mutableListOf<Application>()

        val response: Pair<List<Application>, ResultStatus> =
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
    ): Pair<List<Application>, ResultStatus> {
        var status = ResultStatus.OK
        val applicationList = mutableListOf<Application>()

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
                        applicationList.add(
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
                return Pair(applicationList, status)
            }
        }

        return Pair(applicationList, status)
    }

    /*
     * Get app details of a list of apps from Google Play store.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    private suspend fun getAppDetailsListFromGPlay(
        packageNameList: List<String>,
        authData: AuthData,
    ): Pair<List<Application>, ResultStatus> {
        val applicationList = mutableListOf<Application>()

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
                    applicationList.add(
                        app.transformToFusedApp().apply {
                            filterLevel = filter
                        }
                    )
                }
            }
        }

        return Pair(applicationList, result.getResultStatus())
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
    ): ResultSupreme<List<Application>> {
        val filteredApplications = mutableListOf<Application>()
        return handleNetworkResult {
            appList.forEach {
                val filter = getAppFilterLevel(it, authData)
                if (filter.isUnFiltered()) {
                    filteredApplications.add(
                        it.transformToFusedApp().apply {
                            this.filterLevel = filter
                        }
                    )
                }
            }
            filteredApplications
        }
    }

    /**
     * Get different filter levels.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5720
     */
    override suspend fun getAppFilterLevel(application: Application, authData: AuthData?): FilterLevel {
        return when {
            application.package_name.isBlank() -> FilterLevel.UNKNOWN
            !application.isFree && application.price.isBlank() -> FilterLevel.UI
            application.origin == Origin.CLEANAPK -> FilterLevel.NONE
            !isRestricted(application) -> FilterLevel.NONE
            authData == null -> FilterLevel.UNKNOWN // cannot determine for gplay app
            !isApplicationVisible(application) -> FilterLevel.DATA
            application.originalSize == 0L -> FilterLevel.UI
            !isDownloadable(application) -> FilterLevel.UI
            else -> FilterLevel.NONE
        }
    }

    /**
     * Some apps are simply not visible.
     * Example: com.skype.m2
     */
    private suspend fun isApplicationVisible(application: Application): Boolean {
        return kotlin.runCatching { gplayRepository.getAppDetails(application.package_name) }.isSuccess
    }

    /**
     * Some apps are visible but not downloadable.
     * Example: com.riotgames.league.wildrift
     */
    private suspend fun isDownloadable(application: Application): Boolean {
        return kotlin.runCatching {
            gplayRepository.getDownloadInfo(
                application.package_name,
                application.latest_version_code,
                application.offer_type,
            )
        }.isSuccess
    }

    private fun isRestricted(application: Application): Boolean {
        return application.restriction != Constants.Restriction.NOT_RESTRICTED
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
    private suspend fun Application.updateFilterLevel(authData: AuthData?) {
        this.filterLevel = getAppFilterLevel(this, authData)
    }

    override suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<Application, ResultStatus> {

        var response: Application? = null

        val result = handleNetworkResult {
            response = if (origin == Origin.CLEANAPK) {
                (cleanApkAppsRepository.getAppDetails(id) as Response<CleanApkApplication>).body()?.app
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

        return Pair(result.data ?: Application(), result.getResultStatus())
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
        categoriesList: MutableList<Category>,
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
    ): ResultSupreme<List<Category>> {
        val categoryList = mutableListOf<Category>()

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
    ): Triple<ResultStatus, List<Category>, String> {
        val fusedCategoriesList = mutableListOf<Category>()
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
    ): Triple<ResultStatus, List<Category>, String> {
        val categoryList = mutableListOf<Category>()
        val result = handleNetworkResult {
            getOpenSourceCategories()?.let {
                categoryList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        it,
                        type,
                        AppTag.OpenSource(context.getString(R.string.open_source))
                    )
                )
            }
        }

        return Triple(result.getResultStatus(), categoryList, APP_TYPE_OPEN)
    }

    private fun updateCategoryDrawable(
        category: Category,
    ) {
        category.drawable =
            getCategoryIconResource(getCategoryIconName(category))
    }

    private fun getCategoryIconName(category: Category): String {
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
    ): List<Category> {
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
    ): List<Category> {
        return categories.apps.map { category ->
            createFusedCategoryFromCategory(category, categories, tag)
        }
    }

    private fun getGamesCategoriesAsFusedCategory(
        categories: Categories,
        tag: AppTag
    ): List<Category> {
        return categories.games.map { category ->
            createFusedCategoryFromCategory(category, categories, tag)
        }
    }

    private fun createFusedCategoryFromCategory(
        category: String,
        categories: Categories,
        tag: AppTag
    ): Category {
        return Category(
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

    private fun GplayapiCategory.transformToFusedCategory(): Category {
        val id = this.browseUrl.substringAfter("cat=").substringBefore("&c=")
        return Category(
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
        keyword: String
    ): List<Application> {
        val list = mutableListOf<Application>()
        val response =
            cleanApkAppsRepository.getSearchResult(keyword).body()?.apps

        response?.forEach {
            it.updateStatus()
            it.updateType()
            it.updateSource()
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
                return@handleNetworkResult Pair(listOf<Application>(), setOf<SearchBundle.SubBundle>())
            }

            val fusedAppList =
                searchResults.first.map { app -> replaceWithFDroid(app) }.toMutableList()

            handleLimitedResult(fusedAppList)

            if (searchResults.second.isNotEmpty()) {
                fusedAppList.add(Application(isPlaceHolder = true))
            }

            return@handleNetworkResult Pair(fusedAppList.toList(), searchResults.second.toSet())
        }
    }

    private suspend fun handleLimitedResult(appList: List<Application>?) {
        if (appList.isNullOrEmpty()) {
            // Call search api with a common keyword (ex: facebook)
            // to ensure Gplay is returning empty as search result for other keywords as well
            val searchResult = gplayRepository.getSearchResult(KEYWORD_TEST_SEARCH, null)
            if (searchResult.first.isEmpty()) {
                Timber.w("Limited result for search is found...")
                refreshToken()
            }
        }
    }

    /*
         * This function will replace a GPlay app with F-Droid app if exists,
         * else will show the GPlay app itself.
         */
    private suspend fun replaceWithFDroid(gPlayApp: App): Application {
        val gPlayFusedApp = gPlayApp.transformToFusedApp()
        val response = cleanApkAppsRepository.getAppDetails(gPlayApp.packageName)
        if (response != null) {
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

    private suspend fun generateCleanAPKHome(home: CleanApkHome, appType: String): List<Home> {
        val list = mutableListOf<Home>()
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
                        list.add(Home(value, home.top_updated_apps))
                    }
                }

                "top_updated_games" -> {
                    if (home.top_updated_games.isNotEmpty()) {
                        home.top_updated_games.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(Home(value, home.top_updated_games))
                    }
                }

                "popular_apps" -> {
                    if (home.popular_apps.isNotEmpty()) {
                        home.popular_apps.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(Home(value, home.popular_apps))
                    }
                }

                "popular_games" -> {
                    if (home.popular_games.isNotEmpty()) {
                        home.popular_games.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(Home(value, home.popular_games))
                    }
                }

                "popular_apps_in_last_24_hours" -> {
                    if (home.popular_apps_in_last_24_hours.isNotEmpty()) {
                        home.popular_apps_in_last_24_hours.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(Home(value, home.popular_apps_in_last_24_hours))
                    }
                }

                "popular_games_in_last_24_hours" -> {
                    if (home.popular_games_in_last_24_hours.isNotEmpty()) {
                        home.popular_games_in_last_24_hours.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(Home(value, home.popular_games_in_last_24_hours))
                    }
                }

                "discover" -> {
                    if (home.discover.isNotEmpty()) {
                        home.discover.forEach {
                            it.updateStatus()
                            it.updateType()
                            it.updateFilterLevel(null)
                        }
                        list.add(Home(value, home.discover))
                    }
                }
            }
        }
        return list.map {
            it.source = appType
            it
        }
    }

    private suspend fun fetchGPlayHome(authData: AuthData): List<Home> {
        val list = mutableListOf<Home>()
        val gplayHomeData = gplayRepository.getHomeScreenData() as Map<String, List<App>>
        gplayHomeData.map {
            val fusedApps = it.value.map { app ->
                app.transformToFusedApp().apply {
                    updateFilterLevel(authData)
                }
            }
            list.add(Home(it.key, fusedApps))
        }

        handleLimitedResult(list)
        Timber.d("HomePageData: $list")

        return list
    }

    private fun handleLimitedResult(homeList: List<Home>) {
        val gplayHomes = homeList.filter { fusedHome -> fusedHome.source.isEmpty() }
        val hasGplayLimitedResult = gplayHomes.any { fusedHome -> fusedHome.list.size < THRESHOLD_LIMITED_RESULT_HOME_PAGE }
        if (hasGplayLimitedResult) {
            Timber.w("Limited result is found for homepage...")
            refreshToken()
        }
    }

    private fun refreshToken() {
        MainScope().launch {
            EventBus.invokeEvent(
                AppEvent.InvalidAuthEvent(AuthObject.GPlayAuth::class.java.simpleName)
            )
        }
    }

    /*
     * FusedApp-related internal extensions and functions
     */

    private fun App.transformToFusedApp(): Application {
        val app = Application(
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
    override fun getFusedAppInstallationStatus(application: Application): Status {
        return if (application.is_pwa) {
            pwaManagerModule.getPwaStatus(application)
        } else {
            pkgManagerModule.getPackageStatus(application.package_name, application.latest_version_code)
        }
    }

    private fun Application.updateStatus() {
        if (this.status != Status.INSTALLATION_ISSUE) {
            this.status = getFusedAppInstallationStatus(this)
        }
    }

    private fun Application.updateType() {
        this.type = if (this.is_pwa) Type.PWA else Type.NATIVE
    }

    private fun Application.updateSource() {
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
        newHomeData: List<Home>,
        oldHomeData: List<Home>
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
        oldHome: Home,
        newHome: Home,
    ): Boolean {
        val fusedAppDiffUtil = HomeChildFusedAppDiffUtil()
        if (oldHome.list.size != newHome.list.size) {
            return true
        }

        oldHome.list.forEach { oldFusedApp ->
            val indexOfOldFusedApp = oldHome.list.indexOf(oldFusedApp)
            val fusedApp = newHome.list[indexOfOldFusedApp]
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
        newApplications: List<Application>,
        oldApplications: List<Application>
    ): Boolean {
        val fusedAppDiffUtil = HomeChildFusedAppDiffUtil()
        if (newApplications.size != oldApplications.size) {
            return true
        }

        newApplications.forEach {
            val indexOfNewFusedApp = newApplications.indexOf(it)
            if (!fusedAppDiffUtil.areContentsTheSame(it, oldApplications[indexOfNewFusedApp])) {
                return true
            }
        }
        return false
    }

    override fun isAnyAppInstallStatusChanged(currentList: List<Application>): Boolean {
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
    ): ResultSupreme<Pair<List<Application>, String>> {
        var applicationList: MutableList<Application> = mutableListOf()
        var nextPageUrl = ""

        return handleNetworkResult {
            val streamCluster =
                gplayRepository.getAppsByCategory(category, pageUrl) as StreamCluster

            val filteredAppList = filterRestrictedGPlayApps(authData, streamCluster.clusterAppList)
            filteredAppList.data?.let {
                applicationList = it.toMutableList()
            }

            nextPageUrl = streamCluster.clusterNextPageUrl
            if (!nextPageUrl.isNullOrEmpty()) {
                applicationList.add(Application(isPlaceHolder = true))
            }
            Pair(applicationList, nextPageUrl)
        }
    }
}
