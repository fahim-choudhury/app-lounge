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
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.blockedApps.ContentRatingsRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.DataStoreManager
import foundation.e.apps.domain.ValidateAppAgeLimitUseCase
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.provider.ProviderConstants.Companion.AUTHORITY
import foundation.e.apps.provider.ProviderConstants.Companion.LOGIN_TYPE
import foundation.e.apps.provider.ProviderConstants.Companion.PACKAGE_NAME
import foundation.e.apps.provider.ProviderConstants.Companion.PATH_AGE_RATINGS
import foundation.e.apps.provider.ProviderConstants.Companion.PATH_LOGIN_TYPE
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class AgeRatingProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ContentProviderEntryPoint {
        fun getAuthenticationRepository(): AuthenticatorRepository
        fun getPlayStoreRepository(): PlayStoreRepository
        fun getApplicationRepository(): ApplicationRepository
        fun getPackageManager(): AppLoungePackageManager
        fun getContentRatingsRepository(): ContentRatingsRepository
        fun getValidateAppAgeLimitUseCase(): ValidateAppAgeLimitUseCase
        fun getDataStoreManager(): DataStoreManager
    }

    private lateinit var authenticatorRepository: AuthenticatorRepository
    private lateinit var playStoreRepository: PlayStoreRepository
    private lateinit var applicationRepository: ApplicationRepository
    private lateinit var appLoungePackageManager: AppLoungePackageManager
    private lateinit var contentRatingsRepository: ContentRatingsRepository
    private lateinit var validateAppAgeLimitUseCase: ValidateAppAgeLimitUseCase
    private lateinit var dataStoreManager: DataStoreManager

    private val CODE_LOGIN_TYPE = 1
    private val CODE_AGE_RATING = 2

    private val uriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_LOGIN_TYPE, CODE_LOGIN_TYPE)
            addURI(AUTHORITY, PATH_AGE_RATINGS, CODE_AGE_RATING)
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
        val cursor = MatrixCursor(arrayOf(LOGIN_TYPE))
        cursor.addRow(arrayOf(dataStoreManager.getUserType()))
        return cursor
    }

    private fun getAgeRatings(): Cursor {
        val cursor = MatrixCursor(arrayOf(PACKAGE_NAME))
        val packagesNames = appLoungePackageManager.getAllUserApps().map { it.packageName }
        runBlocking {
            withContext(IO) {

                if (contentRatingsRepository.contentRatingGroups.isEmpty()) {
                    contentRatingsRepository.fetchContentRatingData()
                }

                val contentRatingsDeferred = packagesNames.map { packageName ->
                    async {
                        val authData = dataStoreManager.getAuthData()
                        if (authData.email.isBlank() && authData.aasToken.isBlank()) {
                            return@async null
                        } else {
                            authenticatorRepository.gplayAuth = authData
                        }
                        val fakeAppInstall = AppInstall(
                            packageName = packageName,
                            origin = Origin.GPLAY
                        )
                        val validateResult = validateAppAgeLimitUseCase(fakeAppInstall)
                        validateResult.data ?: false
                    }
                }
                val contentsRatings = contentRatingsDeferred.awaitAll()
                contentsRatings.forEachIndexed { index: Int, isValid: Boolean? ->
                    if (isValid == true) {
                        cursor.addRow(arrayOf(packagesNames[index]))
                    }
                }
            }
        }
        return cursor
    }

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: throw IllegalStateException()
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(appContext, ContentProviderEntryPoint::class.java)

        authenticatorRepository = hiltEntryPoint.getAuthenticationRepository()
        playStoreRepository = hiltEntryPoint.getPlayStoreRepository()
        applicationRepository = hiltEntryPoint.getApplicationRepository()
        appLoungePackageManager = hiltEntryPoint.getPackageManager()
        contentRatingsRepository = hiltEntryPoint.getContentRatingsRepository()
        validateAppAgeLimitUseCase = hiltEntryPoint.getValidateAppAgeLimitUseCase()
        dataStoreManager = hiltEntryPoint.getDataStoreManager()


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