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
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import com.aurora.gplayapi.Constants
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.helpers.TopChartsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
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
     * Pass application source type along with list of apps.
     * Application source type may change in case of timeout of GPlay/cleanapk api.
     *
     * The second item of the Pair can be one of [APP_TYPE_ANY], [APP_TYPE_OPEN], [APP_TYPE_PWA].
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    suspend fun getHomeScreenData(authData: AuthData): Pair<List<FusedHome>, String> {
        val preferredApplicationType = preferenceManagerModule.preferredApplicationType()
        val initialData = getHomeScreenDataBasedOnApplicationType(authData, preferredApplicationType)
        if (isFusedHomesEmpty(initialData.first)) {
            Log.d(TAG, "Received empty home data.")
        }
        return initialData
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

    /*
     * Offload fetching application to a different method to dynamically fallback to a different
     * app source if the user selected app source times out.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    private suspend fun getHomeScreenDataBasedOnApplicationType(
        authData: AuthData,
        applicationType: String
    ): Pair<List<FusedHome>, String> {
        val list = mutableListOf<FusedHome>()
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
            Log.d(TAG, "Timed out fetching home data for type: $applicationType")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(list, applicationType)
    }

    suspend fun getCategoriesList(type: Category.Type, authData: AuthData): List<FusedCategory> {
        val categoriesList = mutableListOf<FusedCategory>()
        val preferredApplicationType = preferenceManagerModule.preferredApplicationType()

        if (preferredApplicationType != APP_TYPE_ANY) {
            handleCleanApkCategories(preferredApplicationType, categoriesList, type)
        } else {
            handleAllSourcesCategories(categoriesList, type, authData)
        }
        categoriesList.sortBy { item -> item.title.lowercase() }
        return categoriesList
    }

    /**
     * Fetches search results from cleanAPK and GPlay servers and returns them
     * @param query Query
     * @param authData [AuthData]
     * @return A livedata list of non-nullable [FusedApp].
     * Observe this livedata to display new apps as they are fetched from the network.
     */
    fun getSearchResults(query: String, authData: AuthData): LiveData<List<FusedApp>> {
        /*
         * Returning livedata to improve performance, so that we do not have to wait forever
         * for all results to be fetched from network before showing them.
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
         */
        return liveData {
            when (preferenceManagerModule.preferredApplicationType()) {
                APP_TYPE_ANY -> {
                    val cleanApkResults = getCleanAPKSearchResults(query)
                    if (cleanApkResults.isNotEmpty()) {
                        /*
                         * If cleanapk results are empty, dont emit emit data as it may
                         * briefly show "No apps found..."
                         */
                        emit(cleanApkResults)
                    }
                    emitSource(getGplayAndCleanapkCombinedResults(query, authData, cleanApkResults))
                }
                APP_TYPE_OPEN -> {
                    emit(getCleanAPKSearchResults(query))
                }
                APP_TYPE_PWA -> {
                    emit(
                        getCleanAPKSearchResults(
                            query,
                            CleanAPKInterface.APP_SOURCE_ANY,
                            CleanAPKInterface.APP_TYPE_PWA
                        )
                    )
                }
            }
        }
    }

    suspend fun getSearchSuggestions(query: String, authData: AuthData): List<SearchSuggestEntry> {
        return gPlayAPIRepository.getSearchSuggestions(query, authData)
    }

    suspend fun fetchAuthData(): Unit? {
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

    suspend fun getPWAApps(category: String): List<FusedApp>? {
        val response = getPWAAppsResponse(category)
        response?.apps?.forEach {
            it.updateStatus()
            it.updateType()
        }
        return response?.apps
    }

    suspend fun getOpenSourceApps(category: String): List<FusedApp>? {
        val response = getOpenSourceAppsResponse(category)
        response?.apps?.forEach {
            it.updateStatus()
            it.updateType()
        }
        return response?.apps
    }

    suspend fun getPlayStoreApps(browseUrl: String, authData: AuthData): List<FusedApp> {
        return gPlayAPIRepository.listApps(browseUrl, authData).map { app ->
            app.transformToFusedApp()
        }
    }

    suspend fun getPlayStoreAppCategoryUrls(browseUrl: String, authData: AuthData): List<String> {
        return gPlayAPIRepository.listAppCategoryUrls(browseUrl, authData)
    }

    suspend fun getAppsAndNextClusterUrl(
        browseUrl: String,
        authData: AuthData
    ): Pair<List<FusedApp>, String> {
        return gPlayAPIRepository.getAppsAndNextClusterUrl(browseUrl, authData).let {
            Pair(it.first.map { app -> app.transformToFusedApp() }, it.second)
        }
    }

    suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): List<FusedApp> {
        val list = mutableListOf<FusedApp>()
        val response = if (origin == Origin.CLEANAPK) {
            val pkgList = mutableListOf<FusedApp>()
            packageNameList.forEach {
                val result = cleanAPKRepository.searchApps(
                    keyword = it,
                    by = "package_name"
                ).body()
                if (result?.apps?.isNotEmpty() == true && result.numberOfResults == 1) {
                    pkgList.add(result.apps[0])
                }
            }
            pkgList
        } else {
            gPlayAPIRepository.getAppDetails(packageNameList, authData).map { app ->
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
        }
        response.forEach {
            if (it.package_name.isNotBlank()) {
                it.updateStatus()
                it.updateType()
                list.add(it)
            }
        }
        return list
    }

    suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): FusedApp {
        val response = if (origin == Origin.CLEANAPK) {
            cleanAPKRepository.getAppOrPWADetailsByID(id).body()?.app
        } else {
            val app = gPlayAPIRepository.getAppDetails(packageName, authData)
            app?.transformToFusedApp()
        }
        response?.let {
            it.updateStatus()
            it.updateType()
        }
        return response ?: FusedApp()
    }

    /*
     * Categories-related internal functions
     */

    private suspend fun handleCleanApkCategories(
        preferredApplicationType: String,
        categoriesList: MutableList<FusedCategory>,
        type: Category.Type
    ) {
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

    private suspend fun handleAllSourcesCategories(
        categoriesList: MutableList<FusedCategory>,
        type: Category.Type,
        authData: AuthData
    ) {
        var data = getOpenSourceCategories()
        data?.let {
            categoriesList.addAll(
                getFusedCategoryBasedOnCategoryType(
                    it,
                    type,
                    AppTag.OpenSource(context.getString(R.string.open_source))
                )
            )
        }
        data = getPWAsCategories()
        data?.let {
            categoriesList.addAll(
                getFusedCategoryBasedOnCategoryType(
                    it, type, AppTag.PWA(context.getString(R.string.pwa))
                )
            )
        }
        val playResponse = gPlayAPIRepository.getCategoriesList(type, authData).map { app ->
            val category = app.transformToFusedCategory()
            updateCategoryDrawable(category, app)
            category
        }
        categoriesList.addAll(playResponse)
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
    ): LiveData<List<FusedApp>> {
        val localList = ArrayList<FusedApp>(cleanApkResults)
        return getGplaySearchResults(query, authData).map { list ->
            localList.apply {
                addAll(list)
            }.distinctBy { it.package_name }
        }
    }

    private fun getGplaySearchResults(query: String, authData: AuthData): LiveData<List<FusedApp>> {
        val searchResults = gPlayAPIRepository.getSearchResults(query, authData)
        return searchResults.map {
            it.map { app -> app.transformToFusedApp() }
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
