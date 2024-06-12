/*
 *  Copyright MURENA SAS 2024
 *  Apps  Quickly and easily install Android apps onto your device!
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.BuildConfig
import foundation.e.apps.contract.ParentalControlContract.COLUMN_LOGIN_TYPE
import foundation.e.apps.contract.ParentalControlContract.COLUMN_PACKAGE_NAME
import foundation.e.apps.contract.ParentalControlContract.PATH_BLOCKLIST
import foundation.e.apps.contract.ParentalControlContract.PATH_LOGIN_TYPE
import foundation.e.apps.contract.ParentalControlContract.getAppLoungeProviderAuthority
import foundation.e.apps.data.blockedApps.ContentRatingsRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.data.preference.DataStoreManager
import foundation.e.apps.domain.ValidateAppAgeLimitUseCase
import foundation.e.apps.install.pkg.AppLoungePackageManager
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

class AgeRatingProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ContentProviderEntryPoint {
        fun provideAuthenticationRepository(): AuthenticatorRepository
        fun providePackageManager(): AppLoungePackageManager
        fun provideContentRatingsRepository(): ContentRatingsRepository
        fun provideValidateAppAgeLimitUseCase(): ValidateAppAgeLimitUseCase
        fun provideDataStoreManager(): DataStoreManager
    }

    private lateinit var authenticatorRepository: AuthenticatorRepository
    private lateinit var appLoungePackageManager: AppLoungePackageManager
    private lateinit var contentRatingsRepository: ContentRatingsRepository
    private lateinit var validateAppAgeLimitUseCase: ValidateAppAgeLimitUseCase
    private lateinit var dataStoreManager: DataStoreManager

    private val CODE_LOGIN_TYPE = 1
    private val CODE_AGE_RATING = 2

    private val AUTHORITY = getAppLoungeProviderAuthority(BuildConfig.DEBUG)

    private val uriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_LOGIN_TYPE, CODE_LOGIN_TYPE)
            addURI(AUTHORITY, PATH_BLOCKLIST, CODE_AGE_RATING)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val code = uriMatcher.match(uri)
        return when (code) {
            CODE_LOGIN_TYPE -> getLoginType()
            CODE_AGE_RATING -> getAgeRatings()
            else -> null
        }
    }

    private fun getLoginType(): Cursor {
        val cursor = MatrixCursor(arrayOf(COLUMN_LOGIN_TYPE))
        cursor.addRow(arrayOf(dataStoreManager.getUserType()))
        return cursor
    }

    private fun getAgeRatings(): Cursor {
        val cursor = MatrixCursor(arrayOf(COLUMN_PACKAGE_NAME))
        val packageNames = appLoungePackageManager.getAllUserApps().map { it.packageName }
        runBlocking {
            withContext(IO) {
                try {
                    if (packageNames.isEmpty()) return@withContext cursor

                    ensureAgeGroupDataExists()

                    val ageValidityDeferred = packageNames.map { packageName ->
                        async {
                            if (!setupAuthDataIfExists()) return@async null
                            getAppAgeValidity(packageName)
                        }
                    }
                    val validityList = ageValidityDeferred.awaitAll()
                    compileAppBlockList(cursor, validityList, packageNames)
                } catch (e: Exception) {
                    Timber.e("AgeRatingProvider", "Error fetching age ratings", e)
                }
            }
        }
        return cursor
    }

    private suspend fun ensureAgeGroupDataExists() {
        if (contentRatingsRepository.contentRatingGroups.isEmpty()) {
            contentRatingsRepository.fetchContentRatingData()
        }
    }

    /**
     * Return true if valid AuthData could be fetched from data store, false otherwise.
     */
    private fun setupAuthDataIfExists(): Boolean {
        val authData = dataStoreManager.getAuthData()
        if (authData.email.isNotBlank() && authData.authToken.isNotBlank()) {
            authenticatorRepository.gplayAuth = authData
            return true
        }
        Timber.e("Blank AuthData, cannot fetch ratings from provider.")
        return false
    }

    private suspend fun getAppAgeValidity(packageName: String): Boolean {
        val fakeAppInstall = AppInstall(
            packageName = packageName,
            origin = Origin.GPLAY
        )
        val validateResult = validateAppAgeLimitUseCase(fakeAppInstall)
        return validateResult.data ?: false
    }

    private fun compileAppBlockList(
        cursor: MatrixCursor,
        validityList: List<Boolean?>,
        packageNames: List<String>,
    ) {
        validityList.forEachIndexed { index: Int, isValid: Boolean? ->
            if (isValid != true) {
                // Collect package names for blocklist
                cursor.addRow(arrayOf(packageNames[index]))
            }
        }
    }

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: error("Null context in ${this::class.java.name}")
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(appContext, ContentProviderEntryPoint::class.java)

        authenticatorRepository = hiltEntryPoint.provideAuthenticationRepository()
        appLoungePackageManager = hiltEntryPoint.providePackageManager()
        contentRatingsRepository = hiltEntryPoint.provideContentRatingsRepository()
        validateAppAgeLimitUseCase = hiltEntryPoint.provideValidateAppAgeLimitUseCase()
        dataStoreManager = hiltEntryPoint.provideDataStoreManager()


        return true
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Not supported")
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            CODE_LOGIN_TYPE -> "vnd.android.cursor.item/${AUTHORITY}.$CODE_LOGIN_TYPE"
            CODE_AGE_RATING -> "vnd.android.cursor.item/${AUTHORITY}.$CODE_AGE_RATING"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Not supported")
    }

}
