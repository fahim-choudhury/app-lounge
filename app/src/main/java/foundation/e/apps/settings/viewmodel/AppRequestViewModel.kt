/*
    Copyright (C) 2019  e Foundation

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.settings.viewmodel

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.Context
import foundation.e.apps.settings.model.AppRequestModel
import foundation.e.apps.utils.Error

class AppRequestViewModel : ViewModel(), AppRequestViewModelInterface {
    private val model = AppRequestModel()
    private var packageName = "";
    var isSubmitButtonEnabled = MutableLiveData<Boolean>()

    init {
        isSubmitButtonEnabled.value = false
    }

    override fun getScreenError(): MutableLiveData<Error> {
        return model.screenError
    }

    override fun onPackageNameChanged(newPackageName: String) {
        packageName = newPackageName
        isSubmitButtonEnabled.value = packageName.isNotEmpty()
    }

    override fun onSubmit(context: Context) {
        model.onSubmit(context, packageName)
    }
}
