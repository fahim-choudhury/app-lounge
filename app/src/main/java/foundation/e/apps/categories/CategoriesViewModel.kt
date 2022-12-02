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

package foundation.e.apps.categories

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedCategory
import foundation.e.apps.login.AuthObject
import foundation.e.apps.utils.enums.AppTag
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.User
import foundation.e.apps.utils.exceptions.CleanApkException
import foundation.e.apps.utils.exceptions.GPlayException
import foundation.e.apps.utils.exceptions.GPlayLoginException
import foundation.e.apps.utils.parentFragment.LoadingViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : LoadingViewModel() {

    val categoriesList: MutableLiveData<Triple<List<FusedCategory>, String, ResultStatus>> =
        MutableLiveData()

    fun loadData(
        type: Category.Type,
        authObjectList: List<AuthObject>,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {
        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getCategoriesList(type, result.data!! as AuthData, this.user)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getCategoriesList(type, AuthData("", ""), this.user)
                return@onLoadData
            }
        }, retryBlock)
    }

    fun getCategoriesList(type: Category.Type, authData: AuthData, user: User) {
        viewModelScope.launch {
            val categoriesData = fusedAPIRepository.getCategoriesList(type, authData)
            categoriesList.postValue(categoriesData)

            val status = categoriesData.third

            val categoriesList = categoriesData.first

            if (status == ResultStatus.OK) {
                if (categoriesList.all { it.tag is AppTag.GPlay } && isCategoriesEmpty(categoriesList)) {
                    exceptionsList.add(GPlayLoginException(false, "Received empty Categories", user))
                }
                return@launch
            }

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

    fun isCategoriesEmpty(fusedCategories: List<FusedCategory>): Boolean {
        return fusedAPIRepository.isCategoriesEmpty(fusedCategories)
    }
}
