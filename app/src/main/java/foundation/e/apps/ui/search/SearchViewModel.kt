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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.GplaySearchResult
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.data.login.exceptions.UnknownSourceException
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository,
) : LoadingViewModel() {

    val searchSuggest: MutableLiveData<List<SearchSuggestEntry>?> = MutableLiveData()

    val searchResult: MutableLiveData<ResultSupreme<Pair<List<Application>, Boolean>>> =
        MutableLiveData()

    private var lastAuthObjects: List<AuthObject>? = null

    private var nextSubBundle: Set<SearchBundle.SubBundle>? = null

    private var isLoading: Boolean = false

    companion object {
        private const val DATA_LOAD_ERROR = "Data load error"
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

            searchResult.postValue(searchResultSupreme)

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

    fun loadMore(query: String) {
        if (isLoading) {
            Timber.d("Search result is loading....")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            fetchGplayData(query)
        }
    }

    private suspend fun fetchGplayData(query: String) {
        isLoading = true
        val gplaySearchResult = applicationRepository.getGplaySearchResults(query, nextSubBundle)

        if (!gplaySearchResult.isSuccess()) {
            handleException(gplaySearchResult.exception ?: UnknownSourceException())
        }

        val isFirstFetch = nextSubBundle == null
        nextSubBundle = gplaySearchResult.data?.second

        // first page has less data, then fetch next page data without waiting for users' scroll
        if (isFirstFetch && gplaySearchResult.isSuccess()) {
            CoroutineScope(coroutineContext).launch {
                fetchGplayData(query)
            }
        }

        val currentAppList = updateCurrentAppList(gplaySearchResult)
        val finalResult = ResultSupreme.Success(
            Pair(currentAppList.toList(), nextSubBundle?.isNotEmpty() ?: false)
        )

        this@SearchViewModel.searchResult.postValue(finalResult)
        isLoading = false
    }

    private fun updateCurrentAppList(gplaySearchResult: GplaySearchResult): List<Application> {
        val currentSearchResult = searchResult.value?.data
        val currentAppList = currentSearchResult?.first?.toMutableList() ?: mutableListOf()
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
}
