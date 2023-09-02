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

package foundation.e.apps.data.login

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.enums.User
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import okhttp3.Cache

/**
 * ViewModel to handle all login related operations.
 * Use it as shared view model across all fragments.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginSourceRepository: LoginSourceRepository,
    private val cache: Cache,
) : ViewModel() {

    /**
     * List of authentication objects which will determine from where to load the data.
     * (i.e. GPlay, CleanApk)
     *
     * Allow null as initial value.
     * This prevents showing login screen before TOS is accepted.
     * This also allows to set null immediately when sources are changed in Settings,
     * and when [startLoginFlow] is called, fragments wait till actual AuthObjects
     * are loaded instead of null.
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
     * Call this to use No-Google mode, i.e. show only PWAs and F-droid apps,
     * without contacting Google servers.
     * This method is called only for the first time when logging in the user.
     * @param onUserSaved Code to execute once email, oauthToken and user is saved.
     * Ends the sign in screen.
     */
    fun initialNoGoogleLogin(onUserSaved: () -> Unit) {
        viewModelScope.launch {
            loginSourceRepository.setNoGoogleMode()
            onUserSaved()
            startLoginFlow()
        }
    }

    /**
     * Once an AuthObject is marked as invalid, it will be refreshed
     * automatically by LoadingViewModel.
     * If GPlay auth is invalid, [LoadingViewModel.onLoadData] has a retry block,
     * this block will clear existing GPlay AuthData and freshly start the login flow.
     */
    fun markInvalidAuthObject(authObjectName: String) {
        val authObjectsLocal = authObjects.value?.toMutableList()
        val invalidObject = authObjectsLocal?.find { it::class.java.simpleName == authObjectName }

        val replacedObject = invalidObject?.createInvalidAuthObject()

        authObjectsLocal?.apply {
            if (invalidObject != null && replacedObject != null) {
                remove(invalidObject)
                add(replacedObject)
            }
        }

        authObjects.postValue(authObjectsLocal)
        cache.evictAll()
    }

    /**
     * Clears all saved data and logs out the user to the sign in screen.
     */
    fun logout() {
        viewModelScope.launch {
            cache.evictAll()
            loginSourceRepository.logout()
            authObjects.postValue(listOf())
        }
    }
}
