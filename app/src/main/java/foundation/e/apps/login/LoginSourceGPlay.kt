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

import android.content.Context
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.login.api.GPlayApiFactory
import foundation.e.apps.login.api.GPlayLoginInterface
import foundation.e.apps.login.api.GoogleLoginApi
import foundation.e.apps.login.api.LoginApiRepository
import foundation.e.apps.utils.enums.User
import foundation.e.apps.utils.exceptions.GPlayValidationException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Class to get GPlay auth data. Call [getAuthObject] to get an already saved auth data
 * or to fetch a new one for first use. Handles auth validation internally.
 *
 * https://gitlab.e.foundation/e/backlog/-/issues/5680
 */
@Singleton
class LoginSourceGPlay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val loginDataStore: LoginDataStore,
) : LoginSourceInterface, AuthValidator {

    @Inject
    lateinit var gPlayApiFactory: GPlayApiFactory

    private val user: User
        get() = loginDataStore.getUserType()

    private val gPlayLoginInterface: GPlayLoginInterface
        get() = gPlayApiFactory.getGPlayApi(user)

    private val loginApiRepository: LoginApiRepository
        get() = LoginApiRepository(gPlayLoginInterface, user)

    private val locale: Locale
        get() = context.resources.configuration.locales[0]

    override fun isActive(): Boolean {
        if (user == User.UNAVAILABLE) {
            /*
             * UNAVAILABLE user means first login is not completed.
             */
            return false
        }
        return loginDataStore.isGplaySelected()
    }

    /**
     * Main entry point to get GPlay auth data.
     */
    override suspend fun getAuthObject(): AuthObject.GPlayAuth {
        val savedAuth = getSavedAuthData()

        val authData = (
            savedAuth ?: run {
                // if no saved data, then generate new auth data.
                generateAuthData().let {
                    if (it.isSuccess()) it.data!!
                    else return AuthObject.GPlayAuth(it, user)
                }
            }
            )

        loginDataStore.saveAuthData(authData)
        return AuthObject.GPlayAuth(ResultSupreme.Success(authData), user)
    }

    override suspend fun clearSavedAuth() {
        loginDataStore.clearAuthData()
    }

    /**
     * Get authData stored as JSON and convert to AuthData class.
     * Returns null if nothing is saved.
     */
    private fun getSavedAuthData(): AuthData? {
        val authJson = loginDataStore.getAuthData()
        return if (authJson.isBlank()) null
        else try {
            gson.fromJson(authJson, AuthData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun saveAuthData(authData: AuthData) {
        loginDataStore.saveAuthData(authData)
    }

    /**
     * Generate new AuthData based on the user type.
     */
    private suspend fun generateAuthData(): ResultSupreme<AuthData?> {
        return when (loginDataStore.getUserType()) {
            User.ANONYMOUS -> getAuthData()
            User.GOOGLE -> {
                getAuthData(
                    loginDataStore.getEmail(),
                    loginDataStore.getOAuthToken(),
                    loginDataStore.getAASToken()
                )
            }
            else -> ResultSupreme.Error("User type not ANONYMOUS or GOOGLE")
        }
    }

    /**
     * Aurora OSS GPlay API complains of missing headers sometimes.
     * Converting [authData] to Json and back to [AuthData] fixed it.
     */
    private fun formattedAuthData(authData: AuthData): AuthData {
        val localAuthDataJson = gson.toJson(authData)
        return gson.fromJson(localAuthDataJson, AuthData::class.java)
    }

    /**
     * Get AuthData for ANONYMOUS mode.
     */
    private suspend fun getAuthData(): ResultSupreme<AuthData?> {
        return loginApiRepository.fetchAuthData("", "", locale).run {
            if (isSuccess()) ResultSupreme.Success(formattedAuthData(this.data!!))
            else this
        }
    }

    /**
     * Get AuthData for GOOGLE login mode.
     */
    private suspend fun getAuthData(
        email: String,
        oauthToken: String,
        aasToken: String,
    ): ResultSupreme<AuthData?> {

        /*
         * If aasToken is not blank, means it was stored successfully from a previous Google login.
         * Use it to fetch auth data.
         */
        if (aasToken.isNotBlank()) {
            return loginApiRepository.fetchAuthData(email, aasToken, locale)
        }

        /*
         * If aasToken is not yet saved / made, fetch it from email and oauthToken.
         */
        val aasTokenResponse = loginApiRepository.getAasToken(
            gPlayLoginInterface as GoogleLoginApi,
            email,
            oauthToken
        )

        /*
         * If fetch was unsuccessful, return blank auth data.
         * We replicate from the response, so that it will carry on any error message if present
         * in the aasTokenResponse.
         */
        if (!aasTokenResponse.isSuccess()) {
            return ResultSupreme.replicate(aasTokenResponse, null)
        }

        val aasTokenFetched = aasTokenResponse.data ?: ""

        if (aasTokenFetched.isBlank()) {
            return ResultSupreme.Error("Fetched AAS Token is blank")
        }

        /*
         * Finally save the aasToken and create auth data.
         */
        loginDataStore.saveAasToken(aasTokenFetched)
        return loginApiRepository.fetchAuthData(email, aasTokenFetched, locale).run {
            if (isSuccess()) ResultSupreme.Success(formattedAuthData(this.data!!))
            else this
        }
    }

    /**
     * Check if a given [AuthData] from Google login or Anonymous login is valid or not.
     * If valid, return the AuthData wrapped in [ResultSupreme], else return null,
     * with error message.
     */
    private suspend fun validateAuthData(
        authData: AuthData,
    ): ResultSupreme<AuthData?> {

        val formattedAuthData = formattedAuthData(authData)
        formattedAuthData.locale = locale

        val validityResponse = loginApiRepository.login(formattedAuthData)

        /*
         * Send the email as payload. This is sent to ecloud in case of failure.
         * See MainActivityViewModel.uploadFaultyTokenToEcloud.
         */
        validityResponse.otherPayload = formattedAuthData.email

        val playResponse = validityResponse.data
        return if (validityResponse.isSuccess() && playResponse?.code == 200 && playResponse.isSuccessful) {
            ResultSupreme.Success(formattedAuthData)
        } else {
            val message =
                "Validating AuthData failed.\n" +
                    "Network code: ${playResponse?.code}\n" +
                    "Success: ${playResponse?.isSuccessful}" +
                    playResponse?.errorString?.run {
                        if (isNotBlank()) "\nError message: $this"
                        else ""
                    }

            ResultSupreme.Error(
                message,
                GPlayValidationException(message, user, playResponse?.code ?: -1)
            )
        }
    }

    override suspend fun validateAuth(): AuthObject? {
        val authData = getSavedAuthData()

        // validate authData and save it if nothing is saved (first time use.)
        authData?.let {
            validateAuthData(authData).run {
                if (isSuccess()) {
                    saveAuthData(authData)
                }
                return AuthObject.GPlayAuth(this, user)
            }
        }

        return null
    }
}
