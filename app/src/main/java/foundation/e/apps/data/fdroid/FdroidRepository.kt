package foundation.e.apps.data.fdroid

import android.content.Context
import foundation.e.apps.data.cleanapk.ApkSignatureManager
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.fdroid.models.BuildInfo
import foundation.e.apps.data.fdroid.models.FdroidEntity
import foundation.e.apps.data.fused.data.FusedApp
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FdroidRepository @Inject constructor(
    private val fdroidApi: FdroidApiInterface,
    private val fdroidDao: FdroidDao
) : IFdroidRepository {

    companion object {
        const val UNKNOWN = "unknown"
    }

    private val fdroidEntries = mutableMapOf<String, FdroidEntity?>()

    /**
     * Get Fdroid entity from DB is present.
     * If not present then make an API call, store the fetched result and return the result.
     *
     * Result may be null.
     */
    override suspend fun getFdroidInfo(packageName: String): FdroidEntity? {
        return fdroidDao.getFdroidEntityFromPackageName(packageName)
            ?: fdroidApi.getFdroidInfoForPackage(packageName).body()?.let {
                FdroidEntity(packageName, it.authorName).also {
                    fdroidDao.saveFdroidEntity(it)
                }
            }
    }

    suspend fun getBuildVersionInfo(packageName: String): List<BuildInfo>? {
        return fdroidApi.getFdroidInfoForPackage(packageName).body()?.builds
    }

    override suspend fun getAuthorName(fusedApp: FusedApp): String {
        if (fusedApp.author != UNKNOWN || fusedApp.origin != Origin.CLEANAPK) {
            return fusedApp.author.ifEmpty { UNKNOWN }
        }

        var result = fdroidEntries[fusedApp.package_name]
        if (result == null) {
            result = getFdroidInfo(fusedApp.package_name)?.also {
                fdroidEntries[fusedApp.package_name] = it
            }
        }
        return result?.authorName ?: FdroidEntity.DEFAULT_FDROID_AUTHOR_NAME
    }

    override suspend fun isFdroidApplicationSigned(context: Context, packageName: String, apkFilePath: String, signature: String): Boolean {
        if (isFdroidApplication(packageName)) {
            return ApkSignatureManager.verifyFdroidSignature(context, apkFilePath, signature)
        }
        return false
    }

    override suspend fun isFdroidApplication(packageName: String): Boolean {
        return fdroidApi.getFdroidInfoForPackage(packageName).isSuccessful
    }
}
