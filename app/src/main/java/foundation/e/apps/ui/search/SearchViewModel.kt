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
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.utils.exceptions.CleanApkException
import foundation.e.apps.utils.exceptions.GPlayException
import foundation.e.apps.utils.parentFragment.LoadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository,
) : LoadingViewModel() {

    val searchSuggest: MutableLiveData<List<SearchSuggestEntry>?> = MutableLiveData()
    val searchResult: MutableLiveData<ResultSupreme<Pair<List<FusedApp>, Boolean>>> =
        MutableLiveData()
    private var searchResultLiveData: LiveData<ResultSupreme<Pair<List<FusedApp>, Boolean>>> =
        MutableLiveData()

    fun getSearchSuggestions(query: String, gPlayAuth: AuthObject.GPlayAuth) {
        viewModelScope.launch(Dispatchers.IO) {
            if (gPlayAuth.result.isSuccess())
                searchSuggest.postValue(
                    fusedAPIRepository.getSearchSuggestions(
                        query,
                        gPlayAuth.result.data!!
                    )
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

        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getSearchResults(query, result.data!! as AuthData, lifecycleOwner)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getSearchResults(query, AuthData("", ""), lifecycleOwner)
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
    fun getSearchResults(query: String, authData: AuthData, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch(Dispatchers.Main) {
            searchResultLiveData.removeObservers(lifecycleOwner)
            searchResultLiveData = fusedAPIRepository.getSearchResults(query, authData)
            searchResultLiveData.observe(lifecycleOwner) {
                searchResult.postValue(it)

                if (!it.isSuccess()) {
                    val exception =
                        if (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank()) {
                            GPlayException(
                                it.isTimeout(),
                                it.message.ifBlank { "Data load error" }
                            )
                        } else {
                            CleanApkException(
                                it.isTimeout(),
                                it.message.ifBlank { "Data load error" }
                            )
                        }

                    exceptionsList.add(exception)
                    exceptionsLiveData.postValue(exceptionsList)
                }
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

    fun hasAnyAppInstallStatusChanged(currentList: List<FusedApp>) =
        fusedAPIRepository.isAnyAppInstallStatusChanged(currentList)
}
