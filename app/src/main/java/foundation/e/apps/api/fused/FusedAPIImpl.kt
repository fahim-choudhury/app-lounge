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

package foundation.e.apps.api.fused

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import com.aurora.gplayapi.Constants
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.TopChartsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.cleanapk.CleanAPKInterface
import foundation.e.apps.api.cleanapk.CleanAPKRepository
import foundation.e.apps.api.cleanapk.data.categories.Categories
import foundation.e.apps.api.cleanapk.data.home.Home
import foundation.e.apps.api.cleanapk.data.search.Search
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.fused.data.FusedCategory
import foundation.e.apps.api.fused.data.FusedHome
import foundation.e.apps.api.fused.data.Ratings
import foundation.e.apps.api.fused.utils.CategoryUtils
import foundation.e.apps.api.gplay.GPlayAPIRepository
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.AppTag
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.Type
import foundation.e.apps.utils.modules.CommonUtilsModule.timeoutDurationInMillis
import foundation.e.apps.utils.modules.PWAManagerModule
import foundation.e.apps.utils.modules.PreferenceManagerModule
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedAPIImpl @Inject constructor(
    private val cleanAPKRepository: CleanAPKRepository,
    private val gPlayAPIRepository: GPlayAPIRepository,
    private val pkgManagerModule: PkgManagerModule,
    private val pwaManagerModule: PWAManagerModule,
    private val preferenceManagerModule: PreferenceManagerModule,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val CATEGORY_TITLE_REPLACEABLE_CONJUNCTION = "&"
        /*
         * Removing "private" access specifier to allow access in
         * MainActivityViewModel.timeoutAlertDialog
         *
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
         */
        const val APP_TYPE_ANY = "any"
        const val APP_TYPE_OPEN = "open"
        const val APP_TYPE_PWA = "pwa"
        private const val CATEGORY_OPEN_GAMES_ID = "game_open_games"
        private const val CATEGORY_OPEN_GAMES_TITLE = "Open games"
    }

    private var TAG = FusedAPIImpl::class.java.simpleName

    /**
     * Pass list of FusedHome and status.
     * Second argument can be of [ResultStatus.TIMEOUT] to indicate timeout.
     *
     * Issue:
     * https://gitlab.e.foundation/e/backlog/-/issues/5404
     * https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    suspend fun getHomeScreenData(authData: AuthData): Pair<List<FusedHome>, ResultStatus> {
        val preferredApplicationType = preferenceManagerModule.preferredApplicationType()
        return getHomeScreenDataBasedOnApplicationType(authData, preferredApplicationType)
    }

    /**
     * Check if list in all the FusedHome is empty.
     * If any list is not empty, send false.
     * Else (if all lists are empty) send true.
     */
    fun isFusedHomesEmpty(fusedHomes: List<FusedHome>): Boolean {
        fusedHomes.forEach {
            if (it.list.isNotEmpty()) return false
        }
        return true
    }

    fun getApplicationCategoryPreference(): String {
        return preferenceManagerModule.preferredApplicationType()
    }

    /*
     * Offload fetching application to a different method to dynamically fallback to a different
     * app source if the user selected app source times out.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    private suspend fun getHomeScreenDataBasedOnApplicationType(
        authData: AuthData,
        applicationType: String
    ): Pair<List<FusedHome>, ResultStatus> {
        val list = mutableListOf<FusedHome>()
        var apiStatus = ResultStatus.OK
        try {
            /*
             * Each category of home apps (example "Top Free Apps") will have its own timeout.
             * Fetching 6 such categories will have a total timeout to 2 mins 30 seconds
             * (considering each category having 25 seconds timeout).
             *
             * To prevent waiting so long and fail early, use withTimeout{}.
             */
            withTimeout(timeoutDurationInMillis) {
                if (applicationType != APP_TYPE_ANY) {
                    val response = if (applicationType == APP_TYPE_OPEN) {
                        cleanAPKRepository.getHomeScreenData(
                            CleanAPKInterface.APP_TYPE_ANY,
                            CleanAPKInterface.APP_SOURCE_FOSS
                        ).body()
                    } else {
                        cleanAPKRepository.getHomeScreenData(
                            CleanAPKInterface.APP_TYPE_PWA,
                            CleanAPKInterface.APP_SOURCE_ANY
                        ).body()
                    }
                    response?.home?.let {
                        list.addAll(generateCleanAPKHome(it, applicationType))
                    }
                } else {
                    list.addAll(fetchGPlayHome(authData))
                }
            }
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            apiStatus = ResultStatus.TIMEOUT
            Timber.d("Timed out fetching home data for type: $applicationType")
        } catch (e: Exception) {
            apiStatus = ResultStatus.UNKNOWN
            Timber.e(e)
        }
        return Pair(list, apiStatus)
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
    suspend fun getCategoriesList(type: Category.Type, authData: AuthData): Triple<List<FusedCategory>, String, ResultStatus> {
        val categoriesList = mutableListOf<FusedCategory>()
        val preferredApplicationType = preferenceManagerModule.preferredApplicationType()
        var apiStatus: ResultStatus = ResultStatus.OK
        var applicationCategoryType = preferredApplicationType

        if (preferredApplicationType != APP_TYPE_ANY) {
            handleCleanApkCategories(preferredApplicationType, categoriesList, type).run {
                if (this != ResultStatus.OK) {
                    apiStatus = this
                }
            }
        } else {
            handleAllSourcesCategories(categoriesList, type, authData).run {
                if (first != ResultStatus.OK) {
                    apiStatus = first
                    applicationCategoryType = second
                }
            }
        }
        categoriesList.sortBy { item -> item.title.lowercase() }
        return Triple(categoriesList, applicationCategoryType, apiStatus)
    }

    /**
     * Fetch categories only from cleanapk.
     * Useful when GPlay times out and we only need to load info from cleanapk.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413 [2]
     */
    suspend fun getCategoriesListOSS(type: Category.Type): Triple<List<FusedCategory>, String, ResultStatus> {
        val categoriesList = mutableListOf<FusedCategory>()
        val status = runCodeBlockWithTimeout({
            getOpenSourceCategories()?.run {
                categoriesList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        this, type, AppTag.OpenSource(context.getString(R.string.open_source))
                    )
                )
            }
            getPWAsCategories()?.run {
                categoriesList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        this, type, AppTag.PWA(context.getString(R.string.pwa))
                    )
                )
            }
        })
        return Triple(categoriesList, "open", status)
    }

    /**
     * Fetches search results from cleanAPK and GPlay servers and returns them
     * @param query Query
     * @param authData [AuthData]
     * @return A livedata Pair of list of non-nullable [FusedApp] and
     * a Boolean signifying if more search results are being loaded.
     * Observe this livedata to display new apps as they are fetched from the network.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413 [2]
     */
    fun getSearchResults(
        query: String,
        authData: AuthData
    ): LiveData<ResultSupreme<Pair<List<FusedApp>, Boolean>>> {
        /*
         * Returning livedata to improve performance, so that we do not have to wait forever
         * for all results to be fetched from network before showing them.
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
         */
        return liveData {
            val packageSpecificResults = ArrayList<FusedApp>()

            val status = runCodeBlockWithTimeout({
                if (preferenceManagerModule.preferredApplicationType() == APP_TYPE_ANY) {
                    try {
                        /*
                         * Surrounding with try-catch because if query is not a package name,
                         * then GPlay throws an error.
                         */
                        getApplicationDetails(query, query, authData, Origin.GPLAY).let {
                            if (it.second == ResultStatus.OK) {
                                packageSpecificResults.add(it.first)
                            }
                        }
                    } catch (_: Exception) {}
                }
                getCleanapkSearchResult(query).let {
                    /* Cleanapk always returns something, it is never null.
                     * If nothing is found, it returns a blank FusedApp() object.
                     * Blank result to be filtered out.
                     */
                    if (it.isSuccess() && it.data!!.package_name.isNotBlank()) {
                        packageSpecificResults.add(it.data!!)
                    }
                }
            })

            /*
             * If there was a timeout, return it and don't try to fetch anything else.
             * Also send false in the pair to signal no more results are being loaded.
             * If not timeout then send true in the pair, to signal more results are being loaded.
             */
            if (status != ResultStatus.OK) {
                emit(ResultSupreme.create(status, Pair(packageSpecificResults, false)))
                return@liveData
            } else if (packageSpecificResults.isNotEmpty()) {
                emit(ResultSupreme.create(status, Pair(packageSpecificResults, true)))
            }

            /*
             * The list packageSpecificResults may contain apps with duplicate package names.
             * Example, "org.telegram.messenger" will result in "Telegram" app from Play Store
             * and "Telegram FOSS" from F-droid. We show both of them at the top.
             *
             * But for the other keyword related search results, we do not allow duplicate package names.
             * We also filter out apps which are already present in packageSpecificResults list.
             */
            fun filterWithKeywordSearch(list: List<FusedApp>): List<FusedApp> {
                val filteredResults = list.distinctBy { it.package_name }
                    .filter { packageSpecificResults.isEmpty() || it.package_name != query }
                return packageSpecificResults + filteredResults
            }

            val cleanApkResults = mutableListOf<FusedApp>()
            when (preferenceManagerModule.preferredApplicationType()) {
                APP_TYPE_ANY -> {
                    val status = runCodeBlockWithTimeout({
                        cleanApkResults.addAll(getCleanAPKSearchResults(query))
                    })
                    if (cleanApkResults.isNotEmpty() || status != ResultStatus.OK) {
                        /*
                         * If cleanapk results are empty, dont emit emit data as it may
                         * briefly show "No apps found..."
                         * If status is timeout, then do emit the value.
                         * Send true in the pair to signal more results (i.e from GPlay) being loaded.
                         */
                        emit(
                            ResultSupreme.create(
                                status,
                                Pair(filterWithKeywordSearch(cleanApkResults), true)
                            )
                        )
                    }
                    emitSource(
                        getGplayAndCleanapkCombinedResults(query, authData, cleanApkResults).map {
                            /*
                             * We are assuming that there will be no timeout here.
                             * If there had to be any timeout, it would already have happened
                             * while fetching package specific results.
                             */
                            ResultSupreme.Success(Pair(filterWithKeywordSearch(it.first), it.second))
                        }
                    )
                }
                APP_TYPE_OPEN -> {
                    val status = runCodeBlockWithTimeout({
                        cleanApkResults.addAll(getCleanAPKSearchResults(query))
                    })
                    /*
                     * Send false in pair to signal no more results to load, as only cleanapk
                     * results are fetched, we don't have to wait for GPlay results.
                     */
                    emit(
                        ResultSupreme.create(
                            status,
                            Pair(filterWithKeywordSearch(cleanApkResults), false)
                        )
                    )
                }
                APP_TYPE_PWA -> {
                    val status = runCodeBlockWithTimeout({
                        cleanApkResults.addAll(
                            getCleanAPKSearchResults(
                                query,
                                CleanAPKInterface.APP_SOURCE_ANY,
                                CleanAPKInterface.APP_TYPE_PWA
                            )
                        )
                    })
                    /*
                     * Send false in pair to signal no more results to load, as only cleanapk
                     * results are fetched for PWAs.
                     */
                    emit(ResultSupreme.create(status, Pair(cleanApkResults, false)))
                }
            }
        }
    }

    /**
     * Similar to [getSearchResults] but this only gets search results from cleanapk and not GPlay.
     * @param query Search query.
     * @return A livedata Pair of list of non-nullable [FusedApp] and
     * a Boolean signifying if more search results are being loaded.
     * Observe this livedata to display new apps as they are fetched from the network.
     */
    fun getSearchResultsOSS(
        query: String
    ): LiveData<ResultSupreme<Pair<List<FusedApp>, Boolean>>> {
        return liveData {
            /*
             * Get package name search.
             */
            var packageSpecificResult = FusedApp()
            val status = runCodeBlockWithTimeout({
                getCleanapkSearchResult(query).let {
                    /* Cleanapk always returns something, it is never null.
                     * If nothing is found, it returns a blank FusedApp() object.
                     * Blank result to be filtered out.
                     */
                    if (it.isSuccess() && it.data!!.package_name.isNotBlank()) {
                        packageSpecificResult = it.data!!
                    }
                }
            })


            /*
             * If there was a timeout, return it and don't try to fetch anything else.
             * Also send false in the pair to signal no more results are being loaded.
             * If not timeout then send true in the pair, to signal more results are being loaded.
             */
            if (status != ResultStatus.OK) {
                emit(ResultSupreme.create(status, Pair(listOf(), false)))
                return@liveData
            } else if (packageSpecificResult.package_name.isNotBlank()) {
                emit(ResultSupreme.create(status, Pair(listOf(packageSpecificResult), true)))
            }

            /*
             * Load keyword related searches from cleanapk.
             */
            val cleanApkResults = mutableListOf<FusedApp>()
            when (preferenceManagerModule.preferredApplicationType()) {
                APP_TYPE_OPEN, APP_TYPE_ANY -> {
                    val status = runCodeBlockWithTimeout({
                        cleanApkResults.addAll(getCleanAPKSearchResults(query))
                    })
                    /*
                     * Send false in pair to signal no more results to load, as only cleanapk
                     * results are fetched, we don't have to wait for GPlay results.
                     *
                     * Also filter out apps with same package name as that of packageSpecificResult,
                     * to avoid duplicates.
                     */
                    emit(
                        ResultSupreme.create(
                            status,
                            Pair(
                                cleanApkResults.filter {
                                    it.package_name != packageSpecificResult.package_name
                                },
                                false
                            )
                        )
                    )
                }
                APP_TYPE_PWA -> {
                    val status = runCodeBlockWithTimeout({
                        cleanApkResults.addAll(
                            getCleanAPKSearchResults(
                                query,
                                CleanAPKInterface.APP_SOURCE_ANY,
                                CleanAPKInterface.APP_TYPE_PWA
                            )
                        )
                    })
                    /*
                     * Send false in pair to signal no more results to load, as only cleanapk
                     * results are fetched for PWAs.
                     */
                    emit(ResultSupreme.create(status, Pair(cleanApkResults, false)))
                }
            }
        }
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
        val status = runCodeBlockWithTimeout({
            val result = cleanAPKRepository.searchApps(
                keyword = packageName,
                by = "package_name"
            ).body()
            if (result?.apps?.isNotEmpty() == true && result.numberOfResults == 1) {
                fusedApp = result.apps[0]
            }
        })
        return ResultSupreme.create(status, fusedApp)
    }

    suspend fun getSearchSuggestions(query: String, authData: AuthData): List<SearchSuggestEntry> {
        return gPlayAPIRepository.getSearchSuggestions(query, authData)
    }

    suspend fun fetchAuthData(): Boolean {
        return gPlayAPIRepository.fetchAuthData()
    }

    suspend fun fetchAuthData(email: String, aasToken: String): AuthData? {
        return gPlayAPIRepository.fetchAuthData(email, aasToken)
    }

    suspend fun validateAuthData(authData: AuthData): Boolean {
        return gPlayAPIRepository.validateAuthData(authData)
    }

    suspend fun updateFusedDownloadWithDownloadingInfo(
        authData: AuthData,
        origin: Origin,
        fusedDownload: FusedDownload
    ) {
        val list = mutableListOf<String>()
        when (origin) {
            Origin.CLEANAPK -> {
                val downloadInfo = cleanAPKRepository.getDownloadInfo(fusedDownload.id).body()
                downloadInfo?.download_data?.download_link?.let { list.add(it) }
            }
            Origin.GPLAY -> {
                val downloadList = gPlayAPIRepository.getDownloadInfo(
                    fusedDownload.packageName,
                    fusedDownload.versionCode,
                    fusedDownload.offerType,
                    authData
                )
                fusedDownload.files = downloadList
                list.addAll(downloadList.map { it.url })
            }
            Origin.GITLAB -> {
            }
        }
        fusedDownload.downloadURLList = list
    }

    suspend fun getPWAApps(category: String): ResultSupreme<List<FusedApp>> {
        val list = mutableListOf<FusedApp>()
        val status = runCodeBlockWithTimeout({
            val response = getPWAAppsResponse(category)
            response?.apps?.forEach {
                it.updateStatus()
                it.updateType()
                list.add(it)
            }
        })
        return ResultSupreme.create(status, list)
    }

    suspend fun getOpenSourceApps(category: String): ResultSupreme<List<FusedApp>> {
        val list = mutableListOf<FusedApp>()
        val status = runCodeBlockWithTimeout({
            val response = getOpenSourceAppsResponse(category)
            response?.apps?.forEach {
                it.updateStatus()
                it.updateType()
                list.add(it)
            }
        })
        return ResultSupreme.create(status, list)
    }

    suspend fun getNextStreamBundle(
        authData: AuthData,
        homeUrl: String,
        currentStreamBundle: StreamBundle,
    ): ResultSupreme<StreamBundle> {
        var streamBundle = StreamBundle()
        val status = runCodeBlockWithTimeout({
            streamBundle = gPlayAPIRepository.getNextStreamBundle(authData, homeUrl, currentStreamBundle)
        })
        return ResultSupreme.create(status, streamBundle)
    }

    suspend fun getAdjustedFirstCluster(
        authData: AuthData,
        streamBundle: StreamBundle,
        pointer: Int = 0,
    ): ResultSupreme<StreamCluster> {
        var streamCluster = StreamCluster()
        val status = runCodeBlockWithTimeout({
            streamCluster = gPlayAPIRepository.getAdjustedFirstCluster(authData, streamBundle, pointer)
        })
        return ResultSupreme.create(status, streamCluster)
    }

    suspend fun getNextStreamCluster(
        authData: AuthData,
        currentStreamCluster: StreamCluster,
    ): ResultSupreme<StreamCluster> {
        var streamCluster = StreamCluster()
        val status = runCodeBlockWithTimeout({
            streamCluster = gPlayAPIRepository.getNextStreamCluster(authData, currentStreamCluster)
        })
        return ResultSupreme.create(status, streamCluster)
    }

    suspend fun getPlayStoreApps(browseUrl: String, authData: AuthData): ResultSupreme<List<FusedApp>> {
        val list = mutableListOf<FusedApp>()
        val status = runCodeBlockWithTimeout({
            list.addAll(
                gPlayAPIRepository.listApps(browseUrl, authData).map { app ->
                    app.transformToFusedApp()
                }
            )
        })
        return ResultSupreme.create(status, list)
    }

    /*
     * Function to search cleanapk using package name.
     * Will be used to handle f-droid deeplink.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5509
     */
    suspend fun getCleanapkAppDetails(packageName: String): Pair<FusedApp, ResultStatus> {
        var fusedApp = FusedApp()
        val status = runCodeBlockWithTimeout({
            val result = cleanAPKRepository.searchApps(
                keyword = packageName,
                by = "package_name"
            ).body()
            if (result?.apps?.isNotEmpty() == true && result.numberOfResults == 1) {
                fusedApp =
                    cleanAPKRepository.getAppOrPWADetailsByID(result.apps[0]._id).body()?.app
                        ?: FusedApp()
            }
        })
        return Pair(fusedApp, status)
    }

    /*
     * Get updates only from cleanapk.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413 [2]
     */
    suspend fun getApplicationDetailsOSS(
        packageNameList: List<String>,
    ): ResultSupreme<List<FusedApp>> {
        val list = mutableListOf<FusedApp>()

        val response: Pair<List<FusedApp>, ResultStatus> =
            getAppDetailsListFromCleanapk(packageNameList)
        response.first.forEach {
            if (it.package_name.isNotBlank()) {
                it.updateStatus()
                it.updateType()
                list.add(it)
            }
        }

        return ResultSupreme.create(response.second, list.toList()).apply {
            if (!isSuccess()) {
                message = true.toString()
            }
        }
    }

    suspend fun getApplicationDetails(
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
            status = runCodeBlockWithTimeout({
                cleanAPKRepository.searchApps(
                    keyword = packageName,
                    by = "package_name"
                ).body()?.run {
                    if (apps.isNotEmpty() && numberOfResults == 1) {
                        fusedAppList.add(apps[0])
                    }
                }
            })

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
        var fusedAppList = listOf<FusedApp>()

        /*
         * Old code moved from getApplicationDetails()
         */
        val status = runCodeBlockWithTimeout({
            fusedAppList = gPlayAPIRepository.getAppDetails(packageNameList, authData).map { app ->
                /*
                 * Some apps are restricted to locations. Example "com.skype.m2".
                 * For restricted apps, check if it is possible to get their specific app info.
                 *
                 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5174
                 */
                if (app.restriction != Constants.Restriction.NOT_RESTRICTED) {
                    try {
                        gPlayAPIRepository.getAppDetails(app.packageName, authData)
                            ?.transformToFusedApp() ?: FusedApp()
                    } catch (e: Exception) {
                        FusedApp()
                    }
                } else {
                    app.transformToFusedApp()
                }
            }
        })

        return Pair(fusedAppList, status)
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
    suspend fun filterRestrictedGPlayApps(
        authData: AuthData,
        appList: List<App>,
    ): ResultSupreme<List<FusedApp>> {
        val filteredFusedApps = mutableListOf<FusedApp>()
        val status = runCodeBlockWithTimeout({
            appList.forEach {
                if (it.restriction != Constants.Restriction.NOT_RESTRICTED) {
                    try {
                        gPlayAPIRepository.getAppDetails(it.packageName, authData)?.let { app ->
                            filteredFusedApps.add(app.transformToFusedApp())
                        }
                    } catch (e: Exception) {}
                } else {
                    filteredFusedApps.add(it.transformToFusedApp())
                }
            }
        })

        return ResultSupreme.create(status, filteredFusedApps)
    }

    suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<FusedApp, ResultStatus> {

        var response: FusedApp? = null

        val status = runCodeBlockWithTimeout({
            response = if (origin == Origin.CLEANAPK) {
                cleanAPKRepository.getAppOrPWADetailsByID(id).body()?.app
            } else {
                val app = gPlayAPIRepository.getAppDetails(packageName, authData)
                app?.transformToFusedApp()
            }
            response?.let {
                it.updateStatus()
                it.updateType()
            }
        })

        return Pair(response ?: FusedApp(), status)
    }

    suspend fun getApplicationDetailsOSS(
        id: String,
    ): Pair<FusedApp, ResultStatus> {

        var response: FusedApp? = null

        val status = runCodeBlockWithTimeout({
            response = cleanAPKRepository.getAppOrPWADetailsByID(id).body()?.app
            response?.let {
                it.updateStatus()
                it.updateType()
            }
        })

        return Pair(response ?: FusedApp(), status)
    }

    /*
     * Categories-related internal functions
     */

    private suspend fun handleCleanApkCategories(
        preferredApplicationType: String,
        categoriesList: MutableList<FusedCategory>,
        type: Category.Type
    ): ResultStatus {
        return runCodeBlockWithTimeout({
            val data = getCleanApkCategories(preferredApplicationType)
            data?.let { category ->
                categoriesList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        category,
                        type,
                        getCategoryTag(preferredApplicationType)
                    )
                )
            }
        })
    }

    private fun getCategoryTag(preferredApplicationType: String): AppTag {
        return if (preferredApplicationType == APP_TYPE_OPEN) {
            AppTag.OpenSource(context.getString(R.string.open_source))
        } else {
            AppTag.PWA(context.getString(R.string.pwa))
        }
    }

    private suspend fun getCleanApkCategories(preferredApplicationType: String): Categories? {
        return if (preferredApplicationType == APP_TYPE_OPEN) {
            getOpenSourceCategories()
        } else {
            getPWAsCategories()
        }
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
        type: Category.Type,
        authData: AuthData
    ): Pair<ResultStatus, String> {
        var data: Categories? = null
        var apiStatus = ResultStatus.OK
        var errorApplicationCategory = ""

        /*
         * Try within timeout limit for open source native apps categories.
         */
        runCodeBlockWithTimeout({
            data = getOpenSourceCategories()
            data?.let {
                categoriesList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        it,
                        type,
                        AppTag.OpenSource(context.getString(R.string.open_source))
                    )
                )
            }
        }, {
            errorApplicationCategory = APP_TYPE_OPEN
            apiStatus = ResultStatus.TIMEOUT
        }, {
            errorApplicationCategory = APP_TYPE_OPEN
            apiStatus = ResultStatus.UNKNOWN
        })

        /*
         * Try within timeout limit to get PWA categories
         */
        runCodeBlockWithTimeout({
            data = getPWAsCategories()
            data?.let {
                categoriesList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        it, type, AppTag.PWA(context.getString(R.string.pwa))
                    )
                )
            }
        }, {
            errorApplicationCategory = APP_TYPE_PWA
            apiStatus = ResultStatus.TIMEOUT
        }, {
            errorApplicationCategory = APP_TYPE_PWA
            apiStatus = ResultStatus.UNKNOWN
        })

        /*
         * Try within timeout limit to get native app categories from Play Store
         */
        runCodeBlockWithTimeout({
            val playResponse = gPlayAPIRepository.getCategoriesList(type, authData).map { app ->
                val category = app.transformToFusedCategory()
                updateCategoryDrawable(category, app)
                category
            }
            categoriesList.addAll(playResponse)
        }, {
            errorApplicationCategory = APP_TYPE_ANY
            apiStatus = ResultStatus.TIMEOUT
        }, {
            errorApplicationCategory = APP_TYPE_ANY
            apiStatus = ResultStatus.UNKNOWN
        })

        return Pair(apiStatus, errorApplicationCategory)
    }

    /**
     * Run a block of code with timeout. Returns status.
     *
     * @param block Main block to execute within [timeoutDurationInMillis] limit.
     * @param timeoutBlock Optional code to execute in case of timeout.
     * @param exceptionBlock Optional code to execute in case of an exception other than timeout.
     *
     * @return Instance of [ResultStatus] based on whether [block] was executed within timeout limit.
     */
    private suspend fun runCodeBlockWithTimeout(
        block: suspend () -> Unit,
        timeoutBlock: (() -> Unit)? = null,
        exceptionBlock: (() -> Unit)? = null,
        timeoutLimit: Long = timeoutDurationInMillis,
    ): ResultStatus {
        return try {
            withTimeout(timeoutLimit) {
                block()
            }
            ResultStatus.OK
        } catch (e: TimeoutCancellationException) {
            timeoutBlock?.invoke()
            ResultStatus.TIMEOUT
        } catch (e: Exception) {
            e.printStackTrace()
            exceptionBlock?.invoke()
            ResultStatus.UNKNOWN
        }
    }

    private fun updateCategoryDrawable(
        category: FusedCategory,
        app: Category
    ) {
        category.drawable =
            getCategoryIconResource(app.type, getCategoryIconName(category))
    }

    private fun getCategoryIconName(category: FusedCategory): String {
        var categoryTitle = if (category.tag.getOperationalTag()
            .contentEquals(AppTag.GPlay().getOperationalTag())
        ) category.id else category.title

        if (categoryTitle.contains(CATEGORY_TITLE_REPLACEABLE_CONJUNCTION)) {
            categoryTitle = categoryTitle.replace(CATEGORY_TITLE_REPLACEABLE_CONJUNCTION, "and")
        }
        categoryTitle = categoryTitle.replace(' ', '_')
        return categoryTitle.lowercase()
    }

    private fun getFusedCategoryBasedOnCategoryType(
        categories: Categories,
        categoryType: Category.Type,
        tag: AppTag
    ): List<FusedCategory> {
        return when (categoryType) {
            Category.Type.APPLICATION -> {
                getAppsCategoriesAsFusedCategory(categories, tag)
            }
            Category.Type.GAME -> {
                getGamesCategoriesAsFusedCategory(categories, tag)
            }
        }
    }

    private fun getAppsCategoriesAsFusedCategory(
        categories: Categories,
        tag: AppTag
    ): List<FusedCategory> {
        return categories.apps.map { category ->
            createFusedCategoryFromCategory(category, categories, Category.Type.APPLICATION, tag)
        }
    }

    private fun getGamesCategoriesAsFusedCategory(
        categories: Categories,
        tag: AppTag
    ): List<FusedCategory> {
        return categories.games.map { category ->
            createFusedCategoryFromCategory(category, categories, Category.Type.GAME, tag)
        }
    }

    private fun createFusedCategoryFromCategory(
        category: String,
        categories: Categories,
        appType: Category.Type,
        tag: AppTag
    ): FusedCategory {
        return FusedCategory(
            id = category,
            title = getCategoryTitle(category, categories),
            drawable = getCategoryIconResource(appType, category),
            tag = tag
        )
    }

    private fun getCategoryIconResource(appType: Category.Type, category: String): Int {
        return if (appType == Category.Type.APPLICATION) {
            CategoryUtils.provideAppsCategoryIconResource(category)
        } else {
            CategoryUtils.provideGamesCategoryIconResource(category)
        }
    }

    private fun getCategoryTitle(category: String, categories: Categories): String {
        return if (category.contentEquals(CATEGORY_OPEN_GAMES_ID)) {
            CATEGORY_OPEN_GAMES_TITLE
        } else {
            categories.translations.getOrDefault(category, "")
        }
    }

    private suspend fun getPWAsCategories(): Categories? {
        return cleanAPKRepository.getCategoriesList(
            CleanAPKInterface.APP_TYPE_PWA,
            CleanAPKInterface.APP_SOURCE_ANY
        ).body()
    }

    private suspend fun getOpenSourceCategories(): Categories? {
        return cleanAPKRepository.getCategoriesList(
            CleanAPKInterface.APP_TYPE_ANY,
            CleanAPKInterface.APP_SOURCE_FOSS
        ).body()
    }

    private suspend fun getOpenSourceAppsResponse(category: String): Search? {
        return cleanAPKRepository.listApps(
            category,
            CleanAPKInterface.APP_SOURCE_FOSS,
            CleanAPKInterface.APP_TYPE_ANY
        ).body()
    }

    private suspend fun getPWAAppsResponse(category: String): Search? {
        return cleanAPKRepository.listApps(
            category,
            CleanAPKInterface.APP_SOURCE_ANY,
            CleanAPKInterface.APP_TYPE_PWA
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
        source: String = CleanAPKInterface.APP_SOURCE_FOSS,
        type: String = CleanAPKInterface.APP_TYPE_ANY,
        nres: Int = 20,
        page: Int = 1,
        by: String? = null
    ): List<FusedApp> {
        val list = mutableListOf<FusedApp>()
        val response =
            cleanAPKRepository.searchApps(keyword, source, type, nres, page, by).body()?.apps

        response?.forEach {
            it.updateStatus()
            it.updateType()
            it.source =
                if (source.contentEquals(CleanAPKInterface.APP_SOURCE_FOSS)) "Open Source" else "PWA"
            list.add(it)
        }
        return list
    }

    /*
     * Function to return a livedata with value from cleanapk and Google Play store combined.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
     */
    private fun getGplayAndCleanapkCombinedResults(
        query: String,
        authData: AuthData,
        cleanApkResults: List<FusedApp>
    ): LiveData<Pair<List<FusedApp>, Boolean>> {
        val localList = ArrayList<FusedApp>(cleanApkResults)
        return getGplaySearchResults(query, authData).map { pair ->
            Pair(
                localList.apply { addAll(pair.first) }.distinctBy { it.package_name },
                pair.second
            )
        }
    }

    private fun getGplaySearchResults(
        query: String,
        authData: AuthData
    ): LiveData<Pair<List<FusedApp>, Boolean>> {
        val searchResults = gPlayAPIRepository.getSearchResults(query, authData)
        return searchResults.map {
            Pair(
                it.first.map { app -> app.transformToFusedApp() },
                it.second
            )
        }
    }

    /*
     * Home screen-related internal functions
     */

    private fun generateCleanAPKHome(home: Home, prefType: String): List<FusedHome> {
        val list = mutableListOf<FusedHome>()
        val headings = if (prefType == APP_TYPE_OPEN) {
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
                "discover" to context.getString(R.string.discover)
            )
        }
        headings.forEach { (key, value) ->
            when (key) {
                "top_updated_apps" -> {
                    if (home.top_updated_apps.isNotEmpty()) {
                        home.top_updated_apps.forEach {
                            it.updateStatus()
                            it.updateType()
                        }
                        list.add(FusedHome(value, home.top_updated_apps))
                    }
                }
                "top_updated_games" -> {
                    if (home.top_updated_games.isNotEmpty()) {
                        home.top_updated_games.forEach {
                            it.updateStatus()
                            it.updateType()
                        }
                        list.add(FusedHome(value, home.top_updated_games))
                    }
                }
                "popular_apps" -> {
                    if (home.popular_apps.isNotEmpty()) {
                        home.popular_apps.forEach {
                            it.updateStatus()
                            it.updateType()
                        }
                        list.add(FusedHome(value, home.popular_apps))
                    }
                }
                "popular_games" -> {
                    if (home.popular_games.isNotEmpty()) {
                        home.popular_games.forEach {
                            it.updateStatus()
                            it.updateType()
                        }
                        list.add(FusedHome(value, home.popular_games))
                    }
                }
                "popular_apps_in_last_24_hours" -> {
                    if (home.popular_apps_in_last_24_hours.isNotEmpty()) {
                        home.popular_apps_in_last_24_hours.forEach {
                            it.updateStatus()
                            it.updateType()
                        }
                        list.add(FusedHome(value, home.popular_apps_in_last_24_hours))
                    }
                }
                "popular_games_in_last_24_hours" -> {
                    if (home.popular_games_in_last_24_hours.isNotEmpty()) {
                        home.popular_games_in_last_24_hours.forEach {
                            it.updateStatus()
                            it.updateType()
                        }
                        list.add(FusedHome(value, home.popular_games_in_last_24_hours))
                    }
                }
                "discover" -> {
                    if (home.discover.isNotEmpty()) {
                        home.discover.forEach {
                            it.updateStatus()
                            it.updateType()
                        }
                        list.add(FusedHome(value, home.discover))
                    }
                }
            }
        }
        return list
    }

    private suspend fun fetchGPlayHome(authData: AuthData): List<FusedHome> {
        val list = mutableListOf<FusedHome>()
        val homeElements = mutableMapOf(
            context.getString(R.string.topselling_free_apps) to mapOf(TopChartsHelper.Chart.TOP_SELLING_FREE to TopChartsHelper.Type.APPLICATION),
            context.getString(R.string.topselling_free_games) to mapOf(TopChartsHelper.Chart.TOP_SELLING_FREE to TopChartsHelper.Type.GAME),
            context.getString(R.string.topgrossing_apps) to mapOf(TopChartsHelper.Chart.TOP_GROSSING to TopChartsHelper.Type.APPLICATION),
            context.getString(R.string.topgrossing_games) to mapOf(TopChartsHelper.Chart.TOP_GROSSING to TopChartsHelper.Type.GAME),
            context.getString(R.string.movers_shakers_apps) to mapOf(TopChartsHelper.Chart.MOVERS_SHAKERS to TopChartsHelper.Type.APPLICATION),
            context.getString(R.string.movers_shakers_games) to mapOf(TopChartsHelper.Chart.MOVERS_SHAKERS to TopChartsHelper.Type.GAME),
        )
        homeElements.forEach {
            val chart = it.value.keys.iterator().next()
            val type = it.value.values.iterator().next()
            val result = gPlayAPIRepository.getTopApps(type, chart, authData).map { app ->
                app.transformToFusedApp()
            }
            list.add(FusedHome(it.key, result))
        }
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
                usageQualityScore = if (this.labeledRating.isNotEmpty()) this.labeledRating.toDoubleOrNull()
                    ?: -1.0 else -1.0
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
    fun getFusedAppInstallationStatus(fusedApp: FusedApp): Status {
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

    private fun MutableList<Artwork>.transformToList(): List<String> {
        val list = mutableListOf<String>()
        this.forEach {
            list.add(it.url)
        }
        return list
    }
}
