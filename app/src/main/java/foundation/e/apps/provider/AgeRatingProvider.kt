/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.provider

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.BuildConfig
import foundation.e.apps.R
import foundation.e.apps.contract.ParentalControlContract.COLUMN_LOGIN_TYPE
import foundation.e.apps.contract.ParentalControlContract.COLUMN_PACKAGE_NAME
import foundation.e.apps.contract.ParentalControlContract.PATH_BLOCKLIST
import foundation.e.apps.contract.ParentalControlContract.PATH_LOGIN_TYPE
import foundation.e.apps.contract.ParentalControlContract.getAppLoungeProviderAuthority
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import foundation.e.apps.data.parentalcontrol.ContentRatingDao
import foundation.e.apps.data.parentalcontrol.ContentRatingEntity
import foundation.e.apps.data.parentalcontrol.fdroid.FDroidAntiFeatureRepository
import foundation.e.apps.data.parentalcontrol.googleplay.GPlayContentRatingRepository
import foundation.e.apps.data.preference.DataStoreManager
import foundation.e.apps.domain.ValidateAppAgeLimitUseCase
import foundation.e.apps.domain.model.ContentRatingValidity
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.utils.isNetworkAvailable
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
        fun provideGPlayContentRatingsRepository(): GPlayContentRatingRepository
        fun provideFDroidAntiFeatureRepository(): FDroidAntiFeatureRepository
        fun provideValidateAppAgeLimitUseCase(): ValidateAppAgeLimitUseCase
        fun provideDataStoreManager(): DataStoreManager
        fun provideNotificationManager(): NotificationManager
        fun provideContentRatingDao(): ContentRatingDao
    }

    companion object {
        private const val CHANNEL_ID = "applounge_provider"
        private const val NOTIFICATION_ID = 77
    }

    private lateinit var authenticatorRepository: AuthenticatorRepository
    private lateinit var appLoungePackageManager: AppLoungePackageManager
    private lateinit var gPlayContentRatingRepository: GPlayContentRatingRepository
    private lateinit var fDroidAntiFeatureRepository: FDroidAntiFeatureRepository
    private lateinit var validateAppAgeLimitUseCase: ValidateAppAgeLimitUseCase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var contentRatingDao: ContentRatingDao

    private enum class UriCode(val code: Int) {
        LoginType(1),
        AgeRating(2),
        ;
    }

    private val authority = getAppLoungeProviderAuthority(BuildConfig.DEBUG)

    private val uriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, PATH_LOGIN_TYPE, UriCode.LoginType.code)
            addURI(authority, PATH_BLOCKLIST, UriCode.AgeRating.code)
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
            UriCode.LoginType.code -> getLoginType()
            UriCode.AgeRating.code -> getAgeRatings()
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

        runBlocking {
            showNotification()
            val packageNames = appLoungePackageManager.getAllUserApps().map { it.packageName }
            Timber.d("Start preparing blocklist from ${packageNames.size} apps.")
            withContext(IO) {
                try {
                    if (packageNames.isEmpty()) return@withContext cursor
                    canSetupAuthData()

                    ensureAgeGroupDataExists()
                    compileAppBlockList(cursor, packageNames)
                } catch (e: Exception) {
                    Timber.e("AgeRatingProvider", "Error fetching age ratings", e)
                }
            }

            hideNotification()
        }

        return cursor
    }

    private fun showNotification() {
        val context = context ?: return
        val title = context.getString(R.string.app_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                title,
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.setSound(null, null)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_lounge_notification_icon)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.message_fetching_content_rating))
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private suspend fun ensureAgeGroupDataExists() {
        withContext(IO) {
            val deferredFetchRatings = async {
                if (gPlayContentRatingRepository.contentRatingGroups.isEmpty()) {
                    gPlayContentRatingRepository.fetchContentRatingData()
                }
            }
            val deferredFetchNSFW = async {
                if (fDroidAntiFeatureRepository.fDroidNsfwApps.isEmpty()) {
                    fDroidAntiFeatureRepository.fetchNsfwApps()
                }
            }
            listOf(deferredFetchRatings, deferredFetchNSFW).awaitAll()
        }
    }

    /**
     * Setup AuthData for other APIs to access,
     * if user has logged in with Google or Anonymous mode.
     */
    private fun canSetupAuthData() {
        val authData = dataStoreManager.getAuthData()
        if (authData.email.isNotBlank() && authData.authToken.isNotBlank()) {
            authenticatorRepository.gplayAuth = authData
        }
    }

    private suspend fun isAppValidRegardingAge(packageName: String): Boolean? {
        val fakeAppInstall = AppInstall(
            packageName = packageName,
            origin = Origin.GPLAY
        )
        val validateResult = validateAppAgeLimitUseCase.invoke(fakeAppInstall)
        saveContentRatingIfInvalid(validateResult, packageName)

        return validateResult.data?.isValid
    }

    private suspend fun saveContentRatingIfInvalid(
        validateResult: ResultSupreme<ContentRatingValidity>,
        packageName: String
    ) {
        val resultData = validateResult.data ?: return

        if (resultData.isValid || resultData.contentRating == null) return

        val ratingId = resultData.contentRating.id
        val ratingTitle = resultData.contentRating.title
        contentRatingDao.insertContentRating(
            ContentRatingEntity(packageName, ratingId, ratingTitle)
        )
    }

    private suspend fun isAppValidRegardingNSWF(packageName: String): Boolean {
        val fakeAppInstall = AppInstall(
            packageName = packageName,
            origin = Origin.CLEANAPK,
        )
        val validateResult = validateAppAgeLimitUseCase.invoke(fakeAppInstall)
        return validateResult.data?.isValid ?: false
    }

    private suspend fun shouldAllowToRun(packageName: String): Boolean {
        return when {
            validateAppAgeLimitUseCase.isParentalControlDisabled() -> true
            !isInitialized() -> false
            !isAppValidRegardingNSWF(packageName) -> false
            isAppValidRegardingAge(packageName) == false -> false
            else -> true
        }
    }

    private suspend fun compileAppBlockList(
        cursor: MatrixCursor,
        packageNames: List<String>,
    ) {
        withContext(IO) {
            val validityList = packageNames.map { packageName ->
                async {
                    shouldAllowToRun(packageName)
                }
            }.awaitAll()
            validityList.forEachIndexed { index: Int, isValid: Boolean? ->
                if (isValid != true) {

                    // Collect package names for blocklist
                    cursor.addRow(arrayOf(packageNames[index]))
                }
            }
            Timber.d("Finished compiling blocklist - ${cursor.count} apps blocked.")
        }
    }

    private suspend fun hasContentRatings(): Boolean {
        return contentRatingDao.getContentRatingCount() > 0
    }

    private fun hasNetwork(): Boolean {
        return context?.isNetworkAvailable() ?: false
    }

    private fun hasAuthData(): Boolean {
        return try {
            authenticatorRepository.gplayAuth != null
        } catch (e: GPlayLoginException) {
            Timber.e("No AuthData to check content rating")
            false
        }
    }

    private suspend fun isInitialized(): Boolean {
        return (hasNetwork() && hasAuthData()) || hasContentRatings()
    }

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: error("Null context in ${this::class.java.name}")
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(appContext, ContentProviderEntryPoint::class.java)

        authenticatorRepository = hiltEntryPoint.provideAuthenticationRepository()
        appLoungePackageManager = hiltEntryPoint.providePackageManager()
        gPlayContentRatingRepository = hiltEntryPoint.provideGPlayContentRatingsRepository()
        fDroidAntiFeatureRepository = hiltEntryPoint.provideFDroidAntiFeatureRepository()
        validateAppAgeLimitUseCase = hiltEntryPoint.provideValidateAppAgeLimitUseCase()
        dataStoreManager = hiltEntryPoint.provideDataStoreManager()
        notificationManager = hiltEntryPoint.provideNotificationManager()
        contentRatingDao = hiltEntryPoint.provideContentRatingDao()

        return true
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Update operation is not supported by AgeRatingProvider")
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            UriCode.LoginType.code ->
                "vnd.android.cursor.item/${authority}.${UriCode.LoginType.code}"
            UriCode.AgeRating.code ->
                "vnd.android.cursor.item/${authority}.${UriCode.AgeRating.code}"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert operation is not supported by AgeRatingProvider")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Delete operation is not supported by AgeRatingProvider")
    }

}
