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
import android.util.Log
import com.aurora.gplayapi.Constants
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
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
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.Type
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.modules.CommonUtilsModule.timeoutDurationInMillis
import foundation.e.apps.utils.modules.PWAManagerModule
import foundation.e.apps.utils.modules.PreferenceManagerModule
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
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
            Log.d(TAG, "Timed out fetching home data for type: $applicationType")
        } catch (e: Exception) {
            apiStatus = ResultStatus.UNKNOWN
            e.printStackTrace()
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
     * Fetches search results from cleanAPK and GPlay servers and returns them
     * @param query Query
     * @param authData [AuthData]
     * @return A list of nullable [FusedApp]
     */
    suspend fun getSearchResults(query: String, authData: AuthData): Pair<List<FusedApp>, ResultStatus> {
        val fusedResponse = ArrayList<FusedApp>()
        val packageSpecificResults = ArrayList<FusedApp>()

        val status = runCodeBlockWithTimeout({

            try {
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
            } catch (_: Exception) {}

            when (preferenceManagerModule.preferredApplicationType()) {
                APP_TYPE_ANY -> {
                    fusedResponse.addAll(getCleanAPKSearchResults(query))
                    fusedResponse.addAll(getGplaySearchResults(query, authData))
                }
                APP_TYPE_OPEN -> {
                    fusedResponse.addAll(getCleanAPKSearchResults(query))
                }
                APP_TYPE_PWA -> {
                    fusedResponse.addAll(
                        getCleanAPKSearchResults(
                            query,
                            CleanAPKInterface.APP_SOURCE_ANY,
                            CleanAPKInterface.APP_TYPE_PWA
                        )
                    )
                }
            }
        })

        /*
         * The list packageSpecificResults may contain apps with duplicate package names.
         * Example, "org.telegram.messenger" will result in "Telegram" app from Play Store
         * and "Telegram FOSS" from F-droid. We show both of them at the top.
         *
         * But for the other keyword related search results, we do not allow duplicate package names.
         * We also filter out apps which are already present in packageSpecificResults list.
         */
        val filteredResults = fusedResponse.distinctBy { it.package_name }
            .filter { packageSpecificResults.isEmpty() || it.package_name != query }

        return Pair(packageSpecificResults + filteredResults, status)
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

    suspend fun fetchAuthData(email: String, aasToken: String): AuthData {
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

    suspend fun listApps(category: String, browseUrl: String, authData: AuthData): List<FusedApp>? {
        val preferredApplicationType = preferenceManagerModule.preferredApplicationType()

        if (preferredApplicationType != "any") {
            val response = if (preferredApplicationType == "open") {
                getOpenSourceAppsResponse(category)
            } else {
                getPWAAppsResponse(category)
            }
            response?.apps?.forEach {
                it.updateStatus()
                it.updateType()
            }
            return response?.apps
        } else {
            val listApps = gPlayAPIRepository.listApps(browseUrl, authData)
            return listApps.map { app ->
                app.transformToFusedApp()
            }
        }
    }

    suspend fun getPWAApps(category: String): Pair<List<FusedApp>, ResultStatus> {
        var list = mutableListOf<FusedApp>()
        val status = runCodeBlockWithTimeout({
            val response = getPWAAppsResponse(category)
            response?.apps?.forEach {
                it.updateStatus()
                it.updateType()
                list.add(it)
            }
        })
        return Pair(list, status)
    }

    suspend fun getOpenSourceApps(category: String): Pair<List<FusedApp>, ResultStatus> {
        val list = mutableListOf<FusedApp>()
        val status = runCodeBlockWithTimeout({
            val response = getOpenSourceAppsResponse(category)
            response?.apps?.forEach {
                it.updateStatus()
                it.updateType()
                list.add(it)
            }
        })
        return Pair(list, status)
    }

    suspend fun getPlayStoreApps(browseUrl: String, authData: AuthData): Pair<List<FusedApp>, ResultStatus> {
        var list = mutableListOf<FusedApp>()
        val status = runCodeBlockWithTimeout({
            list.addAll(gPlayAPIRepository.listApps(browseUrl, authData).map { app ->
                app.transformToFusedApp()
            })
        })
        return Pair(list, status)
    }

    suspend fun getPlayStoreAppCategoryUrls(browseUrl: String, authData: AuthData): List<String> {
        return gPlayAPIRepository.listAppCategoryUrls(browseUrl, authData)
    }

    suspend fun getAppsAndNextClusterUrl(
        browseUrl: String,
        authData: AuthData
    ): Triple<List<FusedApp>, String, ResultStatus> {
        val appsList = mutableListOf<FusedApp>()
        var nextUrl = ""
        val status = runCodeBlockWithTimeout({
            val gPlayResult = gPlayAPIRepository.getAppsAndNextClusterUrl(browseUrl, authData)
            appsList.addAll(gPlayResult.first.map { app -> app.transformToFusedApp() })
            nextUrl = gPlayResult.second
        })

        return Triple(appsList, nextUrl, status)
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

    suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<FusedApp, ResultStatus> {

        var response : FusedApp? = null

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
    ): ResultStatus {
        return try {
            withTimeout(timeoutDurationInMillis) {
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

    private suspend fun getGplaySearchResults(query: String, authData: AuthData): List<FusedApp> {
        val searchResults = gPlayAPIRepository.getSearchResults(query, authData)
        return searchResults.map { app ->
            app.transformToFusedApp()
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
            price = this.price
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
