/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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

package foundation.e.apps.data.application.search

import android.content.Context
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.application.search.SearchApi.Companion.APP_TYPE_ANY
import foundation.e.apps.data.application.search.SearchApi.Companion.APP_TYPE_OPEN
import foundation.e.apps.data.application.search.SearchApi.Companion.APP_TYPE_PWA
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.application.utils.toApplication
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.AppLoungePreference
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

typealias FusedHomeDeferred = Deferred<ResultSupreme<List<Home>>>

@Singleton
class SearchApiImpl @Inject constructor(
    private val appsApi: AppsApi,
    private val appLoungePreference: AppLoungePreference,
    @Named("gplayRepository") private val gplayRepository: PlayStoreRepository,
    @Named("cleanApkAppsRepository") private val cleanApkAppsRepository: CleanApkRepository,
    @Named("cleanApkPWARepository") private val cleanApkPWARepository: CleanApkRepository,
    private val applicationDataManager: ApplicationDataManager
) : SearchApi {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    companion object {
        private const val KEYWORD_TEST_SEARCH = "facebook"
    }

    override fun getSelectedAppTypes(): List<String> {
        val selectedAppTypes = mutableListOf<String>()
        if (appLoungePreference.isGplaySelected()) selectedAppTypes.add(APP_TYPE_ANY)
        if (appLoungePreference.isOpenSourceSelected()) selectedAppTypes.add(APP_TYPE_OPEN)
        if (appLoungePreference.isPWASelected()) selectedAppTypes.add(APP_TYPE_PWA)

        return selectedAppTypes
    }

    /**
     * Fetches search results from cleanAPK and returns them
     * @param query Query
     * @param authData [AuthData]
     * @return A ResultSupreme with Pair of list of non-nullable [Application] and
     * a Boolean signifying if more search results are being loaded.
     */
    override suspend fun getCleanApkSearchResults(
        query: String,
        authData: AuthData
    ): ResultSupreme<Pair<List<Application>, Boolean>> {
        var finalSearchResult: ResultSupreme<Pair<List<Application>, Boolean>> =
            ResultSupreme.Error()

        val packageSpecificResults =
            fetchPackageSpecificResult(authData, query).data?.first ?: emptyList()

        val searchResult = mutableListOf<Application>()
        if (appLoungePreference.isOpenSourceSelected()) {
            finalSearchResult = fetchOpenSourceSearchResult(
                query,
                searchResult,
                packageSpecificResults
            )
        }

        if (appLoungePreference.isPWASelected()) {
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
        packageSpecificResults: List<Application>
    ): ResultSupreme<Pair<List<Application>, Boolean>> {
        val pwaApps: MutableList<Application> = mutableListOf()
        val result = handleNetworkResult {
            val apps =
                cleanApkPWARepository.getSearchResult(query).body()?.apps
            apps?.forEach {
                applicationDataManager.updateStatus(it)
                it.updateType()
                it.updateSource(context)
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
                appLoungePreference.isGplaySelected()
            )
        )
    }

    private suspend fun fetchOpenSourceSearchResult(
        query: String,
        searchResult: MutableList<Application>,
        packageSpecificResults: List<Application>
    ): ResultSupreme<Pair<List<Application>, Boolean>> {
        val cleanApkResults = mutableListOf<Application>()

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
                appLoungePreference.isGplaySelected() || appLoungePreference.isPWASelected()
            )
        )
    }

    private suspend fun fetchPackageSpecificResult(
        authData: AuthData,
        query: String,
    ): ResultSupreme<Pair<List<Application>, Boolean>> {
        val packageSpecificResults: MutableList<Application> = mutableListOf()
        var gplayPackageResult: Application? = null
        var cleanapkPackageResult: Application? = null

        val result = handleNetworkResult {
            if (appLoungePreference.isGplaySelected()) {
                gplayPackageResult = getGplayPackagResult(query, authData)
            }

            if (appLoungePreference.isOpenSourceSelected()) {
                cleanapkPackageResult = getCleanApkPackageResult(query)
            }
        }

        /*
         * Currently only show open source package result if exists in both fdroid and gplay.
         */
        cleanapkPackageResult?.let { packageSpecificResults.add(it) } ?: run {
            gplayPackageResult?.let { packageSpecificResults.add(it) }
        }

        if (appLoungePreference.isGplaySelected()) {
            packageSpecificResults.add(Application(isPlaceHolder = true))
        }

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
        if (appLoungePreference.isGplaySelected()) {
            finalList.add(Application(isPlaceHolder = true))
        }

        return finalList
    }

    private suspend fun getCleanApkPackageResult(
        query: String,
    ): Application? {
        getCleanApkSearchResult(query).let {
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
        appsApi.getApplicationDetails(query, query, authData, Origin.GPLAY).let {
            if (it.second == ResultStatus.OK && it.first.package_name.isNotEmpty()) {
                return it.first
            }
        }

        return null
    }

    /*
     * Method to search cleanapk based on package name.
     * This is to be only used for showing an entry in search results list.
     * DO NOT use this to show info on ApplicationFragment as it will not have all the required
     * information to show for an app.
     *
     */
    private suspend fun getCleanApkSearchResult(packageName: String): ResultSupreme<Application> {
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

    private suspend fun getCleanAPKSearchResults(
        keyword: String
    ): List<Application> {
        val list = mutableListOf<Application>()
        val response =
            cleanApkAppsRepository.getSearchResult(keyword).body()?.apps

        response?.forEach {
            applicationDataManager.updateStatus(it)
            it.updateType()
            it.updateSource(context)
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

            if (!appLoungePreference.isGplaySelected()) {
                return@handleNetworkResult Pair(
                    listOf<Application>(),
                    setOf<SearchBundle.SubBundle>()
                )
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
        val gPlayFusedApp = gPlayApp.toApplication(context)
        val response = cleanApkAppsRepository.getAppDetails(gPlayApp.packageName)
        if (response != null) {
            val fdroidApp = getCleanApkPackageResult(gPlayFusedApp.package_name)?.apply {
                this.updateSource(context)
                isGplayReplaced = true
            }

            return fdroidApp ?: gPlayFusedApp
        }

        return gPlayFusedApp
    }

    private fun refreshToken() {
        MainScope().launch {
            EventBus.invokeEvent(
                AppEvent.InvalidAuthEvent(AuthObject.GPlayAuth::class.java.simpleName)
            )
        }
    }
}
