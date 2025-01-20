/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.data.application.search

import android.content.Context
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.SearchBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.AppSourcesContainer
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.Stores
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.search.SearchApi.Companion.APP_TYPE_ANY
import foundation.e.apps.data.application.search.SearchApi.Companion.APP_TYPE_OPEN
import foundation.e.apps.data.application.search.SearchApi.Companion.APP_TYPE_PWA
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.login.exceptions.CleanApkIOException
import foundation.e.apps.data.login.exceptions.GPlayIOException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchApiImpl @Inject constructor(
    private val appsApi: AppsApi,
    private val appSources: AppSourcesContainer,
    private val stores: Stores,
    private val applicationDataManager: ApplicationDataManager
) : SearchApi {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    override fun getSelectedAppTypes(): List<String> {
        val selectedAppTypes = mutableListOf<String>()
        if (stores.isStoreEnabled(Source.PLAY_STORE)) selectedAppTypes.add(APP_TYPE_ANY)
        if (stores.isStoreEnabled(Source.OPEN_SOURCE)) selectedAppTypes.add(APP_TYPE_OPEN)
        if (stores.isStoreEnabled(Source.PWA)) selectedAppTypes.add(APP_TYPE_PWA)

        return selectedAppTypes
    }

    /**
     * Fetches search results from cleanAPK and returns them
     * @param query Query
     * @return A ResultSupreme with Pair of list of non-nullable [Application] and
     * a Boolean signifying if more search results are being loaded.
     */
    override suspend fun getCleanApkSearchResults(
        query: String,
    ): SearchResult {
        var finalSearchResult: SearchResult = ResultSupreme.Error(
            message = "",
            exception = CleanApkIOException("Unable to reach CleanAPK API")
        )

        val packageSpecificResults =
            fetchPackageSpecificResult(query).data?.first ?: emptyList()

        val searchResult = mutableListOf<Application>()
        if (stores.isStoreEnabled(Source.OPEN_SOURCE)) {
            finalSearchResult = fetchOpenSourceSearchResult(
                query,
                searchResult,
                packageSpecificResults
            )
        }

        if (stores.isStoreEnabled(Source.PWA)) {
            finalSearchResult = fetchPWASearchResult(
                query,
                searchResult,
                packageSpecificResults
            )
        }

        if (!stores.isStoreEnabled(Source.OPEN_SOURCE) && !stores.isStoreEnabled(Source.PWA)) {
            finalSearchResult = ResultSupreme.Success(
                Pair(
                    packageSpecificResults,
                    stores.isStoreEnabled(Source.PLAY_STORE)
                )
            )
        }

        return finalSearchResult
    }

    private suspend fun fetchPWASearchResult(
        query: String,
        searchResult: MutableList<Application>,
        packageSpecificResults: List<Application>
    ): SearchResult {
        val pwaApps: MutableList<Application> = mutableListOf()
        val result = handleNetworkResult {
            val apps =
                stores.getStore(Source.PWA)?.getSearchResults(query) ?: emptyList()

            apps.forEach {
                applicationDataManager.updateStatus(it)
                it.source = Source.PWA
                it.updateType()
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
                stores.isStoreEnabled(Source.PLAY_STORE)
            ),
            exception = result.exception
        )
    }

    private suspend fun fetchOpenSourceSearchResult(
        query: String,
        searchResult: MutableList<Application>,
        packageSpecificResults: List<Application>
    ): SearchResult {
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
                stores.isStoreEnabled(Source.PLAY_STORE) || stores.isStoreEnabled(Source.PWA)
            ),
            exception = result.exception
        )
    }

    private suspend fun fetchPackageSpecificResult(
        query: String,
    ): SearchResult {
        val packageSpecificResults: MutableList<Application> = mutableListOf()
        var gplayPackageResult: Application? = null
        var cleanapkPackageResult: Application? = null

        val result = handleNetworkResult {
            if (stores.isStoreEnabled(Source.PLAY_STORE)) {
                gplayPackageResult = getGplayPackageResult(query)
            }

            if (stores.isStoreEnabled(Source.OPEN_SOURCE)) {
                cleanapkPackageResult = getCleanApkPackageResult(query)
            }
        }

        /*
         * Currently only show open source package result if exists in both fdroid and gplay.
         */
        cleanapkPackageResult?.let { packageSpecificResults.add(it) } ?: run {
            gplayPackageResult?.let { packageSpecificResults.add(it) }
        }

        if (stores.isStoreEnabled(Source.PLAY_STORE)) {
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
        if (stores.isStoreEnabled(Source.PLAY_STORE)) {
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

    private suspend fun getGplayPackageResult(
        query: String,
    ): Application? {
        appsApi.getApplicationDetails(query, query, Source.PLAY_STORE).let {
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
            val results = stores.getStore(Source.OPEN_SOURCE)?.getSearchResults(packageName) ?: emptyList()

            if (results.isNotEmpty() && results.size == 1) {
                application = results[0]
            }
        }

        return ResultSupreme.create(result.getResultStatus(), application)
    }

    override suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry> {
        var searchSuggesions = listOf<SearchSuggestEntry>()
        handleNetworkResult {
            searchSuggesions = appSources.gplayRepo.getSearchSuggestions(query)
        }

        return searchSuggesions
    }

    private suspend fun getCleanAPKSearchResults(
        keyword: String
    ): List<Application> {
        val list = mutableListOf<Application>()
        val response =
            stores.getStore(Source.OPEN_SOURCE)?.getSearchResults(keyword) ?: emptyList()

        response.forEach {
            applicationDataManager.updateStatus(it)
            it.updateType()
            list.add(it)
        }

        return list
    }

    override suspend fun getGplaySearchResult(
        query: String,
    ): SearchResult {
        val result = handleNetworkResult {
            if (!stores.isStoreEnabled(Source.PLAY_STORE)) {
                return@handleNetworkResult Pair(
                    listOf<Application>(),
                    setOf<SearchBundle.SubBundle>()
                )
            }

            val searchResults =
                stores.getStore(Source.PLAY_STORE)?.getSearchResults(query)
                    ?: throw IllegalStateException("Could not get store")

            val apps = replaceWithFDroid(searchResults).toMutableList()
            if (searchResults.isNotEmpty()) {
                apps.add(Application(isPlaceHolder = true))
            }

            return@handleNetworkResult Pair(apps.toList(), true)
        }

        return if (result.isSuccess()) {
            ResultSupreme.Success(result.data as Pair<List<Application>, Boolean>)
        } else ResultSupreme.Error(
            message = "",
            exception = GPlayIOException("Unable to reach Google Play API")
        )
    }

    /*
     * This function will replace a GPlay app with F-Droid app if exists,
     * else will show the GPlay app itself.
     */
    private suspend fun replaceWithFDroid(gPlayApps: List<Application>): List<Application> {
        try {
            if (gPlayApps.isEmpty()) return emptyList()

            val packageNames = gPlayApps.map { it.package_name }
            val response = appSources.cleanApkAppsRepo.checkAvailablePackages(packageNames)

            val availableApps = response.body()?.apps ?: emptyList()

            return gPlayApps.map { gPlayApp ->
                availableApps.find { it.package_name == gPlayApp.package_name }?.apply {
                    isGplayReplaced = true
                    source = Source.PLAY_STORE
                } ?: gPlayApp
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to replace Google apps with their F-Droid counterparts.")
            return gPlayApps
        }
    }
}
