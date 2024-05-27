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

package foundation.e.apps.ui.search

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.search.GplaySearchResult
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.exodus.repositories.IAppPrivacyInfoRepository
import foundation.e.apps.data.exodus.repositories.PrivacyScoreRepository
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.data.login.exceptions.UnknownSourceException
import foundation.e.apps.di.CommonUtilsModule.LIST_OF_NULL
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

typealias SearchResult = ResultSupreme<Pair<List<Application>, Boolean>>

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val privacyScoreRepository: PrivacyScoreRepository,
    private val appPrivacyInfoRepository: IAppPrivacyInfoRepository
) : LoadingViewModel() {

    val searchSuggest: MutableLiveData<List<SearchSuggestEntry>?> = MutableLiveData()

    private val _searchResult: MutableLiveData<SearchResult> =
        MutableLiveData()
    val searchResult: LiveData<SearchResult> = _searchResult

    val gplaySearchLoaded : MutableLiveData<Boolean> = MutableLiveData(false)

    private var lastAuthObjects: List<AuthObject>? = null

    private var nextSubBundle: Set<SearchBundle.SubBundle>? = null

    private var isLoading: Boolean = false
    private var hasGPlayBeenFetched = false

    val accumulatedList = mutableListOf<Application>()

    private var flagNoTrackers: Boolean = false
    private var flagOpenSource: Boolean = false
    private var flagPWA: Boolean = false

    companion object {
        private const val DATA_LOAD_ERROR = "Data load error"
        private const val MIN_SEARCH_DISPLAY_ITEMS = 10
        private const val PREVENT_HTTP_429_DELAY_IN_MS = 1000L
    }

    fun setFilterFlags(
        flagNoTrackers: Boolean = false,
        flagOpenSource: Boolean = false,
        flagPWA: Boolean = false,
    ) {
        this.flagNoTrackers = flagNoTrackers
        this.flagOpenSource = flagOpenSource
        this.flagPWA = flagPWA

        viewModelScope.launch {
            emitFilteredResults(null)
        }
    }

    fun getSearchSuggestions(query: String, gPlayAuth: AuthObject.GPlayAuth) {
        viewModelScope.launch(Dispatchers.IO) {
            if (gPlayAuth.result.isSuccess())
                searchSuggest.postValue(
                    applicationRepository.getSearchSuggestions(query)
                )
        }
    }

    fun loadData(
        query: String,
        lifecycleOwner: LifecycleOwner,
        authObjectList: List<AuthObject>,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean
    ) {

        if (query.isBlank()) return

        this.lastAuthObjects = authObjectList
        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getSearchResults(query, result.data!! as AuthData)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getSearchResults(query, null)
                return@onLoadData
            }
        }, retryBlock)
    }

    /*
     * Observe data from Fused API and publish the result in searchResult.
     * This allows us to show apps as they are being fetched from the network,
     * without having to wait for all of the apps.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
     */
    private fun getSearchResults(
        query: String,
        authData: AuthData?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val searchResultSupreme = applicationRepository.getCleanApkSearchResults(
                query,
                authData ?: AuthData("", "")
            )

            hasGPlayBeenFetched = false
            emitFilteredResults(searchResultSupreme)

            if (!searchResultSupreme.isSuccess()) {
                val exception =
                    if (authData != null) {
                        GPlayException(
                            searchResultSupreme.isTimeout(),
                            searchResultSupreme.message.ifBlank { DATA_LOAD_ERROR }
                        )
                    } else {
                        CleanApkException(
                            searchResultSupreme.isTimeout(),
                            searchResultSupreme.message.ifBlank { DATA_LOAD_ERROR }
                        )
                    }

                handleException(exception)
            }

            if (authData == null) {
                return@launch
            }

            nextSubBundle = null
            fetchGplayData(query)
        }
    }

    fun loadMore(query: String, autoTriggered: Boolean = false) {
        if (isLoading) {
            Timber.d("Search result is loading....")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (autoTriggered) {
                delay(PREVENT_HTTP_429_DELAY_IN_MS)
            }
            fetchGplayData(query)
        }
    }

    private suspend fun fetchGplayData(query: String) {
        isLoading = true
        val gplaySearchResult = applicationRepository.getGplaySearchResults(query, nextSubBundle)

        if (!gplaySearchResult.isSuccess()) {
            handleException(gplaySearchResult.exception ?: UnknownSourceException())
        }

        nextSubBundle = gplaySearchResult.data?.second

        val currentAppList = updateCurrentAppList(gplaySearchResult)
        val finalResult = ResultSupreme.Success(
            Pair(currentAppList.toList(), nextSubBundle?.isNotEmpty() ?: false)
        )

        hasGPlayBeenFetched = true
        emitFilteredResults(finalResult)

        isLoading = false
    }

    private fun updateCurrentAppList(gplaySearchResult: GplaySearchResult): List<Application> {
        val currentAppList = accumulatedList
        currentAppList.removeIf { item -> item.isPlaceHolder }
        currentAppList.addAll(gplaySearchResult.data?.first ?: emptyList())
        return currentAppList.distinctBy { it.package_name }
    }

    private fun handleException(exception: Exception) {
        exceptionsList.add(exception)
        exceptionsLiveData.postValue(exceptionsList)
    }

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isAnyAppUpdated(
        newApplications: List<Application>,
        oldApplications: List<Application>
    ) = applicationRepository.isAnyFusedAppUpdated(newApplications, oldApplications)

    fun isAuthObjectListSame(authObjectList: List<AuthObject>?): Boolean {
        return lastAuthObjects == authObjectList
    }

    private fun hasTrackers(app: Application): Boolean {
        return when {
            app.trackers == LIST_OF_NULL -> true        // Tracker data unavailable, don't show
            app.trackers.isNotEmpty() -> true           // Trackers present
            app.privacyScore == 0 -> true               // Manually blocked apps (Facebook etc.)
            else -> false
        }
    }

    private suspend fun fetchTrackersForApp(app: Application) {
        if (app.isPlaceHolder) return
        appPrivacyInfoRepository.getAppPrivacyInfo(app, app.package_name).let {
            val calculatedScore = privacyScoreRepository.calculatePrivacyScore(app)
            app.privacyScore = calculatedScore
        }
    }

    private suspend fun getFilteredList(): List<Application> = withContext(IO) {
        if (flagNoTrackers) {
            val deferredCheck = accumulatedList.map {
                async {
                    if (it.privacyScore == -1) {
                        fetchTrackersForApp(it)
                    }
                    it
                }
            }
            deferredCheck.awaitAll()
        }

        accumulatedList.filter {
            if (!flagNoTrackers && !flagOpenSource && !flagPWA) return@filter true
            if (flagNoTrackers && !hasTrackers(it)) return@filter true
            if (flagOpenSource && !it.is_pwa && it.origin == Origin.CLEANAPK) return@filter true
            if (flagPWA && it.is_pwa) return@filter true
            false
        }
    }

    /**
     * Pass [result] as null to re-emit already loaded search results with new filters.
     */
    private suspend fun emitFilteredResults(
        result: SearchResult? = null
    ) {

        // When filters are changed but no data is fetched yet
        if (result == null && _searchResult.value == null) {
            return
        }

        if (result != null && !result.isSuccess()) {
            _searchResult.postValue(result!!)
            return
        }

        if (result != null) {
            result.data?.first?.let {
                accumulatedList.clear()
                accumulatedList.addAll(it)
            }
        }

        val filteredList = getFilteredList()
        val isMoreDataLoading = result?.data?.second ?: _searchResult.value?.data?.second ?: false

        _searchResult.postValue(
            ResultSupreme.Success(
                Pair(filteredList.toList(), isMoreDataLoading)
            )
        )
        gplaySearchLoaded.postValue(hasGPlayBeenFetched)
    }

    fun loadMoreDataIfNeeded(searchText: String) {
        val searchList =
            searchResult.value?.data?.first?.toMutableList() ?: emptyList()
        val canLoadMore = searchResult.value?.data?.second ?: false

        if (searchList.size < MIN_SEARCH_DISPLAY_ITEMS && canLoadMore) {
            loadMore(searchText, autoTriggered = true)
        }
    }

    fun shouldIgnoreResults(): Boolean {
        val appsList = _searchResult.value?.data?.first

        if (appsList.isNullOrEmpty()) return true

        val appPackageNames = appsList.map { it.package_name }
        return appPackageNames.all { it.isBlank() }
    }
}
