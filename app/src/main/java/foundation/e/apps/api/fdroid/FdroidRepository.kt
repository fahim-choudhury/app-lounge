package foundation.e.apps.api.fdroid

import foundation.e.apps.api.fdroid.models.FdroidEntity
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.utils.enums.Origin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FdroidRepository @Inject constructor(
    private val fdroidApi: FdroidApiInterface,
    private val fdroidDao: FdroidDao,
) {

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
    private suspend fun getFdroidInfo(packageName: String): FdroidEntity? {
        return fdroidDao.getFdroidEntityFromPackageName(packageName)
            ?: fdroidApi.getFdroidInfoForPackage(packageName)?.let {
                FdroidEntity(packageName, it.authorName).also {
                    fdroidDao.saveFdroidEntity(it)
                }
            }
    }

    suspend fun getAuthorName(fusedApp: FusedApp): String {
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
}
