package foundation.e.apps.api.fdroid

import android.content.Context
import foundation.e.apps.api.cleanapk.ApkSignatureManager
import foundation.e.apps.api.fdroid.models.FdroidEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FdroidRepository @Inject constructor(
    private val fdroidApi: FdroidApiInterface,
    private val fdroidDao: FdroidDao,
) {

    /**
     * Get Fdroid entity from DB is present.
     * If not present then make an API call, store the fetched result and return the result.
     *
     * Result may be null.
     */
    suspend fun getFdroidInfo(packageName: String): FdroidEntity? {
        return fdroidDao.getFdroidEntityFromPackageName(packageName)
            ?: fdroidApi.getFdroidInfoForPackage(packageName).body()?.let {
                FdroidEntity(packageName, it.authorName).also {
                    fdroidDao.saveFdroidEntity(it)
                }
            }
    }

    suspend fun isFdroidApplication(packageName: String): Boolean {
        return fdroidApi.getFdroidInfoForPackage(packageName).isSuccessful
    }

    suspend fun isFdroidApplicationSigned(context: Context, packageName: String, apkFilePath: String, signature: String): Boolean {
        if (isFdroidApplication(packageName)) {
            return ApkSignatureManager.verifyFdroidSignature(context, apkFilePath, signature)
        }
        return false
    }
}
