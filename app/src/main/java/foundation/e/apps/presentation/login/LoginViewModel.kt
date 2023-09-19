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

package foundation.e.apps.presentation.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.enums.User.NO_GOOGLE
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.LoginSourceRepository
import foundation.e.apps.domain.login.usecase.NoGoogleModeUseCase
import foundation.e.apps.domain.login.usecase.UserLoginUseCase
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import foundation.e.apps.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import javax.inject.Inject

/**
 * ViewModel to handle all login related operations.
 * Use it as shared view model across all fragments.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginSourceRepository: LoginSourceRepository,
    private val userLoginUseCase: UserLoginUseCase,
    private val noGoogleModeUseCase: NoGoogleModeUseCase,
    private val cache: Cache
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
            val authObjectsLocal = loginSourceRepository.getAuthObjects(listOf())
            authObjects.postValue(authObjectsLocal)

            if (authObjectsLocal.isNotEmpty() &&
                authObjectsLocal[0] is AuthObject.GPlayAuth
            ) {
                val authObject = authObjectsLocal[0] as AuthObject.GPlayAuth
                authObject.result.data?.let { authData ->
                    userLoginUseCase.googleUser(authData, oauthToken).collect()
                    _loginState.value = LoginState(isLoading = false, isLoggedIn = true)
                } ?: kotlin.run {
                    _loginState.value =
                        LoginState(
                            isLoading = false,
                            isLoggedIn = false,
                            error = "Google login failed"
                        )
                }
            } else {
                _loginState.value =
                    LoginState(
                        isLoading = false,
                        isLoggedIn = false,
                        error = "Google login failed"
                    )
            }

            onUserSaved()
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
            val authObject = noGoogleModeUseCase.performNoGoogleLogin()
            _loginState.value = LoginState(isLoading = false, isLoggedIn = true)
            authObjects.postValue(listOf(authObject))
            onUserSaved()
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
        userLoginUseCase.logoutUser()
        _loginState.value = LoginState()
    }

    private val _loginState: MutableLiveData<LoginState> = MutableLiveData()
    val loginState: LiveData<LoginState> = _loginState

    fun authenticateAnonymousUser() {
        viewModelScope.launch(Dispatchers.IO) {
            userLoginUseCase.performAnonymousUserAuthentication().onEach { result ->
                withContext(Dispatchers.Main) {
                    when (result) {
                        is Resource.Success -> {
                            result.data?.let { updateSavedAuthData() }
                        }

                        is Resource.Error -> {
                            _loginState.value = LoginState(
                                error = result.message ?: "An unexpected error occured"
                            )
                        }

                        is Resource.Loading -> {
                            _loginState.value = LoginState(isLoading = true)
                        }
                    }
                }
            }.collect()
        }
    }

    fun checkLogin() {
        viewModelScope.launch {
            val user = userLoginUseCase.currentUser()
            if (user == NO_GOOGLE) {
                _loginState.value =
                    LoginState(isLoggedIn = true, authData = null, user = user)
            } else {
                updateSavedAuthData()
            }
        }
    }

    private suspend fun updateSavedAuthData() {
        userLoginUseCase.retrieveCachedAuthData().onEach {
            when (it) {
                is Resource.Error -> {
                    val error = it.message.let { message ->
                        when (message) {
                            null -> "An unexpected error occurred"
                            else -> message
                        }
                    }
                    _loginState.value = LoginState(error = error)
                }
                is Resource.Loading -> _loginState.value = LoginState(isLoading = true)
                is Resource.Success -> {
                    // TODO
                    it.data?.let { it1 -> updateAuthObjectForAnonymousUser(it1) }

                    _loginState.value =
                        LoginState(isLoggedIn = true, authData = it.data, user = User.ANONYMOUS)
                }
            }
        }.collect()
    }

    fun updateAuthObjectForAnonymousUser(authData: AuthData) {
        // TODO : Refine after Google User API is refactored.
        loginSourceRepository.gplayAuth = authData

        authObjects.postValue(
            listOf(
                AuthObject.GPlayAuth(ResultSupreme.Success(authData), User.ANONYMOUS)
            )
        )
    }
}
