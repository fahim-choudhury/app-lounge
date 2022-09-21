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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceManager
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.utils.Constants.PREFERENCE_SHOW_FOSS
import foundation.e.apps.utils.Constants.PREFERENCE_SHOW_GPLAY
import foundation.e.apps.utils.Constants.PREFERENCE_SHOW_PWA
import foundation.e.apps.utils.enums.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginDataStore @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val gson: Gson
) {

    private val preferenceDataStoreName = "Settings"
    private val Context.dataStore by preferencesDataStore(preferenceDataStoreName)

    private val preferenceManager = PreferenceManager.getDefaultSharedPreferences(context)

    private val AUTHDATA = stringPreferencesKey("authData")
    private val EMAIL = stringPreferencesKey("email")
    private val OAUTHTOKEN = stringPreferencesKey("oauthtoken")
    private val AASTOKEN = stringPreferencesKey("aasToken")
    private val USERTYPE = stringPreferencesKey("userType")

    /*
     * Difference between OAUTHTOKEN and AASTOKEN:
     *
     * These two are used only for Google login, not for Anonymous login.
     * OAuthToken is obtained from the Google Login web page, from the cookies.
     * This OAuthToken is then used by AC2DMTask in GPlayAPIImpl class
     * to generate AasToken.
     *
     * To get Google Play Store data, we need to create an AuthData instance.
     * For Google user, this can only be done using AasToken, not OAuthToken.
     *
     * Very important: AasToken can be generated only ONCE from one OAuthToken.
     * We cannot get AasToken again from the same OAuthToken. Thus it is
     * important to safely store the AasToken to regenerate AuthData if needed.
     * If AasToken is not stored, user has to logout and login again.
     */

    val authData = context.dataStore.data.map { it[AUTHDATA] ?: "" }
    val emailData = context.dataStore.data.map { it[EMAIL] ?: "" }
    val aasToken = context.dataStore.data.map { it[AASTOKEN] ?: "" }
    val oauthToken = context.dataStore.data.map { it[OAUTHTOKEN] ?: "" }
    val userType = context.dataStore.data.map { it[USERTYPE] ?: "" }

    // Setters

    suspend fun saveAuthData(authData: AuthData) {
        context.dataStore.edit {
            it[AUTHDATA] = gson.toJson(authData)
        }
    }

    suspend fun saveUserType(user: User) {
        context.dataStore.edit {
            it[USERTYPE] = user.name
        }
    }

    suspend fun saveGoogleLogin(email: String, token: String) {
        context.dataStore.edit {
            it[EMAIL] = email
            it[OAUTHTOKEN] = token
        }
    }

    suspend fun saveAasToken(aasToken: String) {
        context.dataStore.edit {
            it[AASTOKEN] = aasToken
        }
    }

    // Getters

    fun getAuthData(): String {
        return runBlocking {
            authData.first()
        }
    }

    /**
     * Get the [User] type stored in the data store.
     * In case nothing is stored, returns [User.UNAVAILABLE].
     *
     * No need to wrap this function in try-catch block.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5680
     */
    fun getUserType(): User {
        return runBlocking {
            userType.first().run {
                val userStrings = User.values().map { it.name }
                if (this !in userStrings) User.UNAVAILABLE
                else User.valueOf(this)
            }
        }
    }

    fun getEmail(): String {
        return runBlocking {
            emailData.first()
        }
    }

    fun getOAuthToken(): String {
        return runBlocking {
            oauthToken.first()
        }
    }

    fun getAASToken(): String {
        return runBlocking {
            aasToken.first()
        }
    }

    fun isOpenSourceSelected() = preferenceManager.getBoolean(PREFERENCE_SHOW_FOSS, true)
    fun isPWASelected() = preferenceManager.getBoolean(PREFERENCE_SHOW_PWA, true)
    fun isGplaySelected() = preferenceManager.getBoolean(PREFERENCE_SHOW_GPLAY, true)

    // Clear data

    /**
     * Destroy auth credentials if they are no longer valid.
     *
     * Modification for issue: https://gitlab.e.foundation/e/backlog/-/issues/5168
     * Previously this method would also remove [USERTYPE].
     * To clear this value, call [clearUserType].
     */
    suspend fun destroyCredentials() {
        context.dataStore.edit {
            it.remove(AUTHDATA)
            it.remove(EMAIL)
            it.remove(OAUTHTOKEN)
            it.remove(AASTOKEN)
        }
    }

    suspend fun clearAuthData() {
        context.dataStore.edit {
            it.remove(AUTHDATA)
        }
    }

    suspend fun clearUserType() {
        context.dataStore.edit {
            it.remove(USERTYPE)
        }
    }
}
