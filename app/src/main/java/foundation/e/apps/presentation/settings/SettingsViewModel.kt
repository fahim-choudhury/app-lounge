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

package foundation.e.apps.presentation.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.Constants
import foundation.e.apps.domain.settings.usecase.SettingsUseCase
import foundation.e.apps.utils.Resource
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsUseCase: SettingsUseCase
) : ViewModel() {

    private val _currentUserState: MutableLiveData<SettingUserState> = MutableLiveData()
    val currentUserState: LiveData<SettingUserState> = _currentUserState

    fun currentUser() {
        viewModelScope.launch {
            settingsUseCase.currentUser().onEach { result ->
                when (result) {
                    is Resource.Success -> {
                        _currentUserState.value =
                            result.data?.let { settingUserState.apply { user = it } }
                    }
                    is Resource.Error -> {
                        _currentUserState.value =
                            settingUserState.apply { error = result.message ?: Constants.UNEXPECTED_ERROR }
                    }

                    is Resource.Loading -> TODO()
                }
            }.collect()
        }
    }

    fun currentAuthData() {
        viewModelScope.launch {
            settingsUseCase.currentAuthData().onEach { result ->
                when (result) {
                    is Resource.Success -> {
                        _currentUserState.value =
                            result.data?.let { settingUserState.apply { authData = it } }
                    }
                    is Resource.Error -> {
                        _currentUserState.value =
                            settingUserState.apply { error = result.message ?: Constants.UNEXPECTED_ERROR }
                    }

                    is Resource.Loading -> {
                        // NO NEED
                    }
                }
            }.collect()
        }
    }

    fun logout() {
        settingsUseCase.logoutUser().launchIn(viewModelScope)
    }

    fun resetSettingState() {
        settingUserState = SettingUserState()
    }
}
