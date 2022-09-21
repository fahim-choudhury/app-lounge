/*
 * Copyright (C) 2019-2022  E FOUNDATION
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

package foundation.e.apps.login

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.utils.enums.User
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel to handle all login related operations.
 * Use it as shared view model across all fragments.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginSourceRepository: LoginSourceRepository,
) : ViewModel() {

    /**
     * List of authentication objects which will determine from where to load the data.
     * (i.e. GPlay, CleanApk)
     */
    val authObjects: MutableLiveData<List<AuthObject>?> = MutableLiveData(null)

    /**
     * Main point of starting of entire authentication process.
     */
    fun startLoginFlow(clearList: List<String> = listOf()) {
        viewModelScope.launch {
            val authObjectsLocal = loginSourceRepository.getAuthObjects(clearList)
            authObjects.postValue(authObjectsLocal)
        }
    }

    /**
     * Call this to use ANONYMOUS mode.
     * This method is called only for the first time when logging in the user.
     * @param onUserSaved Code to execute once user is saved. Ends the sign in screen.
     */
    fun initialAnonymousLogin(onUserSaved: () -> Unit) {
        viewModelScope.launch {
            loginSourceRepository.saveUserType(User.ANONYMOUS)
            onUserSaved()
            startLoginFlow()
        }
    }

    /**
     * Call this to use GOOGLE login mode.
     * This method is called only for the first time when logging in the user.
     * @param onUserSaved Code to execute once email, oauthToken and user is saved.
     * Ends the sign in screen.
     */
    fun initialGoogleLogin(email: String, oauthToken: String, onUserSaved: () -> Unit) {
        viewModelScope.launch {
            loginSourceRepository.saveGoogleLogin(email, oauthToken)
            loginSourceRepository.saveUserType(User.GOOGLE)
            onUserSaved()
            startLoginFlow()
        }
    }

    /**
     * Clears all saved data and logs out the user to the sign in screen.
     */
    fun logout() {
        viewModelScope.launch {
            loginSourceRepository.logout()
        }
    }
}
