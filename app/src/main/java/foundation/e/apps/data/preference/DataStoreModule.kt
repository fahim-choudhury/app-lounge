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

package foundation.e.apps.data.preference

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.enums.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreModule @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val gson: Gson
) {

    companion object {
        private const val preferenceDataStoreName = "Settings"
        val Context.dataStore by preferencesDataStore(preferenceDataStoreName)
    }

    private val AUTHDATA = stringPreferencesKey("authData")
    private val EMAIL = stringPreferencesKey("email")
    private val OAUTHTOKEN = stringPreferencesKey("oauthtoken")
    private val USERTYPE = stringPreferencesKey("userType")
    private val TOCSTATUS = booleanPreferencesKey("tocStatus")
    private val TOSVERSION = stringPreferencesKey("tosversion")

    val authData = context.dataStore.data.map { it[AUTHDATA] ?: "" }
    val emailData = context.dataStore.data.map { it[EMAIL] ?: "" }
    val aasToken = context.dataStore.data.map { it[OAUTHTOKEN] ?: "" }
    val userType = context.dataStore.data.map { it[USERTYPE] ?: "" }
    val tocStatus = context.dataStore.data.map { it[TOCSTATUS] ?: false }
    val tosVersion = context.dataStore.data.map { it[TOSVERSION] ?: "" }

    /**
     * Allows to save gplay API token data into datastore
     */
    suspend fun saveCredentials(authData: AuthData) {
        context.dataStore.edit {
            it[AUTHDATA] = gson.toJson(authData)
        }
    }

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
        }
    }

    suspend fun clearUserType() {
        context.dataStore.edit {
            it.remove(USERTYPE)
        }
    }

    /**
     * TOC status
     */
    suspend fun saveTOCStatus(status: Boolean, tosVersion: String) {
        context.dataStore.edit {
            it[TOCSTATUS] = status
            it[TOSVERSION] = tosVersion
        }
    }

    fun getTOSVersion(): String {
        return runBlocking {
            tosVersion.first()
        }
    }

    /**
     * User auth type
     */
    suspend fun saveUserType(user: User) {
        context.dataStore.edit {
            it[USERTYPE] = user.name
        }
    }

    fun getAuthDataSync(): String {
        return runBlocking {
            authData.first()
        }
    }

    suspend fun saveEmail(email: String, token: String) {
        context.dataStore.edit {
            it[EMAIL] = email
            it[OAUTHTOKEN] = token
        }
    }

    fun getEmail(): String {
        return runBlocking {
            emailData.first()
        }
    }

    fun getUserType(): User {
        return runBlocking {
            userType.first().run {
                val userStrings = User.values().map { it.name }
                if (this !in userStrings) {
                    User.UNAVAILABLE
                } else {
                    User.valueOf(this)
                }
            }
        }
    }
}
