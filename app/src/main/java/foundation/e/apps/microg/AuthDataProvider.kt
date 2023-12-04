/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2024  E FOUNDATION
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

package foundation.e.apps.microg

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.authdata.AuthDataContract
import foundation.e.apps.data.preference.DataStoreManager

/**
 * Content provider dedicated to share the Google auth data with
 * other applications. Other applications need to own the following permission:
 * `foundation.e.apps.permission.AUTH_DATA_PROVIDER`
 */
class AuthDataProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DataStoreManagerEntryPoint {
        fun provideDataStoreManager(): DataStoreManager
    }

    private lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(): Boolean {
        val context = context ?: return false

        val dataStoreEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DataStoreManagerEntryPoint::class.java
        )

        dataStoreManager = dataStoreEntryPoint.provideDataStoreManager()
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                              selectionArgs: Array<String>?, sortOrder: String?): Cursor {

        if (context?.checkCallingOrSelfPermission(
                AUTH_DATA_PROVIDER_PERMISSION
        ) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Permission denied: $AUTH_DATA_PROVIDER_PERMISSION required")
        }

        val cursor = MatrixCursor(
            arrayOf(
                AuthDataContract.EMAIL_KEY,
                AuthDataContract.AUTH_TOKEN_KEY,
                AuthDataContract.GSF_ID_KEY,
                AuthDataContract.CONSISTENCY_TOKEN_KEY,
                AuthDataContract.DEVICE_CONFIG_TOKEN_KEY,
                AuthDataContract.EXPERIMENTS_CONFIG_TOKEN_KEY,
                AuthDataContract.DFE_COOKIE_KEY
            )
        )

        val row = cursor.newRow()
        dataStoreManager.getAuthData().let {
            row.add(AuthDataContract.EMAIL_KEY, it.email)
            row.add(AuthDataContract.AUTH_TOKEN_KEY, it.authToken)
            row.add(AuthDataContract.GSF_ID_KEY, it.gsfId)
            row.add(AuthDataContract.CONSISTENCY_TOKEN_KEY, it.deviceCheckInConsistencyToken)
            row.add(AuthDataContract.DEVICE_CONFIG_TOKEN_KEY, it.deviceConfigToken)
            row.add(AuthDataContract.EXPERIMENTS_CONFIG_TOKEN_KEY, it.experimentsConfigToken)
            row.add(AuthDataContract.DFE_COOKIE_KEY, it.dfeCookie)
        }

        cursor.setNotificationUri(context?.contentResolver, uri)

        return cursor
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                               selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Update operation is not supported by the provider")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Delete operation is not supported by the provider")
    }

    override fun getType(uri: Uri): String? {
        return "vnd.android.cursor.dir/vnd.foundation.e.apps.authdata.provider.strings";
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        throw UnsupportedOperationException("Insert operation is not supported by the provider")
    }

    companion object {
        const val AUTH_DATA_PROVIDER_PERMISSION = "foundation.e.apps.permission.AUTH_DATA_PROVIDER"
    }
}

