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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.GplaySearchResult
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : ViewModel() {

    val searchSuggest: MutableLiveData<List<SearchSuggestEntry>?> = MutableLiveData()
    val searchResult: MutableLiveData<ResultSupreme<Pair<List<FusedApp>, Boolean>>> =
        MutableLiveData()

    private var lastAuthObjects: List<AuthObject>? = null

    private var nextSubBundle: Set<SearchBundle.SubBundle>? = null

    private var isLoading: Boolean = false

    companion object {
        private const val DATA_LOAD_ERROR = "Data load error"
    }

    fun getSearchSuggestions(query: String, authData: AuthData?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (query.isNotBlank() && authData != null) {
                searchSuggest.postValue(
                    fusedAPIRepository.getSearchSuggestions(
                        query,
                        authData
                    )
                )
            }
        }
    }

    fun loadData(
        query: String,
        lifecycleOwner: LifecycleOwner,
        authData: AuthData?
    ) {
        if (query.isBlank()) return
        getSearchResults(query, authData ?: AuthData("", ""), lifecycleOwner)
    }

    /*
     * Observe data from Fused API and publish the result in searchResult.
     * This allows us to show apps as they are being fetched from the network,
     * without having to wait for all of the apps.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
     */
    private fun getSearchResults(
        query: String,
        authData: AuthData,
        lifecycleOwner: LifecycleOwner
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val searchResultSupreme = fusedAPIRepository.getCleanApkSearchResults(query, authData)

            searchResult.postValue(searchResultSupreme)

            if (!searchResultSupreme.isSuccess()) {
                val exception =
                    if (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank()) {
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

//                handleException(exception)
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
        val gplaySearchResult = fusedAPIRepository.getGplaySearchResults(query, nextSubBundle)

        if (!gplaySearchResult.isSuccess()) {
//            handleException(gplaySearchResult.exception ?: UnknownSourceException())
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

    private fun updateCurrentAppList(gplaySearchResult: GplaySearchResult): List<FusedApp> {
        val currentSearchResult = searchResult.value?.data
        val currentAppList = currentSearchResult?.first?.toMutableList() ?: mutableListOf()
        currentAppList.removeIf { item -> item.isPlaceHolder }
        currentAppList.addAll(gplaySearchResult.data?.first ?: emptyList())
        return currentAppList.distinctBy { it.package_name }
    }

//    private fun handleException(exception: Exception) {
//        exceptionsList.add(exception)
//        exceptionsLiveData.postValue(exceptionsList)
//    }

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isAnyAppUpdated(
        newFusedApps: List<FusedApp>,
        oldFusedApps: List<FusedApp>
    ) = fusedAPIRepository.isAnyFusedAppUpdated(newFusedApps, oldFusedApps)
}
