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

package foundation.e.apps.ui.categories

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.Category
import foundation.e.apps.data.fused.utils.CategoryType
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : LoadingViewModel() {

    val categoriesList: MutableLiveData<Triple<List<Category>, String, ResultStatus>> =
        MutableLiveData()

    fun loadData(
        type: CategoryType,
        authObjectList: List<AuthObject>,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {
        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getCategoriesList(type, result.data!! as AuthData)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getCategoriesList(type, AuthData("", ""))
                return@onLoadData
            }
        }, retryBlock)
    }

    fun getCategoriesList(type: CategoryType, authData: AuthData) {
        viewModelScope.launch {
            val categoriesData = fusedAPIRepository.getCategoriesList(type)
            categoriesList.postValue(categoriesData)

            val status = categoriesData.third

            if (status != ResultStatus.OK) {
                val exception =
                    if (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank())
                        GPlayException(
                            categoriesData.third == ResultStatus.TIMEOUT,
                            status.message.ifBlank { "Data load error" }
                        )
                    else CleanApkException(
                        categoriesData.third == ResultStatus.TIMEOUT,
                        status.message.ifBlank { "Data load error" }
                    )

                exceptionsList.add(exception)
                exceptionsLiveData.postValue(exceptionsList)
            }
        }
    }
}
