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
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : ViewModel() {

    val searchSuggest: MutableLiveData<List<SearchSuggestEntry>?> = MutableLiveData()
    val searchResult: MutableLiveData<ResultSupreme<Pair<List<FusedApp>, Boolean>>> =
        MutableLiveData()
    private var searchResultLiveData: MutableLiveData<ResultSupreme<Pair<List<FusedApp>, Boolean>>> =
        MutableLiveData()

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
    private fun getSearchResults(query: String, authData: AuthData, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch(Dispatchers.Main) {
            searchResultLiveData.removeObservers(lifecycleOwner)
            searchResultLiveData.value = fusedAPIRepository.getCleanApkSearchResults(query, authData)
            searchResultLiveData.observe(lifecycleOwner) {
                searchResult.postValue(it)
            }
        }
    }

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isAnyAppUpdated(
        newFusedApps: List<FusedApp>,
        oldFusedApps: List<FusedApp>
    ) = fusedAPIRepository.isAnyFusedAppUpdated(newFusedApps, oldFusedApps)
}
