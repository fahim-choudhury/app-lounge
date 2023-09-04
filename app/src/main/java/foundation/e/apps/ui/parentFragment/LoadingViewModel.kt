/*
 * Copyright (C) 2019-2022  MURENA SAS
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

package foundation.e.apps.ui.parentFragment

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.GPlayValidationException
import foundation.e.apps.data.login.exceptions.UnknownSourceException

abstract class LoadingViewModel : ViewModel() {

    companion object {
        private var autoRetried = false
    }

    val exceptionsLiveData: MutableLiveData<List<Exception>> = MutableLiveData()
    val exceptionsList = ArrayList<Exception>()

    /**
     * Call this method from ViewModel.
     *
     * @param authObjectList List obtained from login process.
     * @param loadingBlock Define how to load data in this method.
     * @param retryBlock Define retry mechanism for failed AuthObject.
     * Return `true` to signify the failure event is consumed by the block and no further
     * processing on failed AuthObject is needed.
     */
    fun onLoadData(
        authObjectList: List<AuthObject>,
        loadingBlock: (successObjects: List<AuthObject>, failedObjects: List<AuthObject>) -> Unit,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean
    ) {
        exceptionsList.clear()

        val successAuthList = authObjectList.filter { it.result.isSuccess() }
        val failedAuthList = authObjectList.filter { !it.result.isSuccess() }

        failedAuthList.forEach {
            exceptionsList.add(it.result.exception ?: UnknownSourceException())
        }

        exceptionsList.find {
            it is GPlayValidationException
        }?.run {
            if (!autoRetried && retryBlock(failedAuthList)) {
                autoRetried = true
                exceptionsList.clear()
                return
            }
        }

        loadingBlock(successAuthList, failedAuthList)

        if (successAuthList.isEmpty() && exceptionsList.isNotEmpty()) {
            /*
             * As no authentication is successful, nothing can be loaded,
             * post the exceptions.
             */
            exceptionsLiveData.postValue(exceptionsList)
        }
    }
}
