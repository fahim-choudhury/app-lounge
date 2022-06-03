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

package foundation.e.apps.updates

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.updates.manager.UpdatesManagerRepository
import foundation.e.apps.utils.enums.ResultStatus
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val updatesManagerRepository: UpdatesManagerRepository
) : ViewModel() {

    val updatesList: MutableLiveData<Pair<List<FusedApp>, ResultStatus?>> = MutableLiveData()

    fun getUpdates(authData: AuthData) {
        viewModelScope.launch {
            val updatesResult = updatesManagerRepository.getUpdates(authData)
            updatesList.postValue(
                Pair(
                    updatesResult.first.filter { !(!it.isFree && authData.isAnonymous) },
                    updatesResult.second
                )
            )
        }
    }

    fun getApplicationCategoryPreference(): String {
        return updatesManagerRepository.getApplicationCategoryPreference()
    }
}
