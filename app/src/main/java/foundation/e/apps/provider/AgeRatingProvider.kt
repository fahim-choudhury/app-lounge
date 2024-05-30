package foundation.e.apps.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.provider.ProviderConstants.Companion.AGE_RATING
import foundation.e.apps.provider.ProviderConstants.Companion.AUTHORITY
import foundation.e.apps.provider.ProviderConstants.Companion.LOGIN_TYPE
import foundation.e.apps.provider.ProviderConstants.Companion.PACKAGE_NAME
import foundation.e.apps.provider.ProviderConstants.Companion.PATH_AGE_RATINGS
import foundation.e.apps.provider.ProviderConstants.Companion.PATH_LOGIN_TYPE
import javax.inject.Inject

class AgeRatingProvider @Inject constructor(
    private val authenticatorRepository: AuthenticatorRepository,
): ContentProvider() {

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
            else -> null
        }
    }

    private fun getLoginType(): Cursor {
        val cursor = MatrixCursor(arrayOf(LOGIN_TYPE))
        cursor.addRow(arrayOf(authenticatorRepository.getUserType()))
        return cursor
    }

    private fun getAgeRatings(): Cursor {
        val cursor = MatrixCursor(arrayOf(PACKAGE_NAME, AGE_RATING))
        return cursor
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

}