package foundation.e.apps.data

import foundation.e.apps.data.cleanapk.repositories.CleanApkAppsRepository
import foundation.e.apps.data.cleanapk.repositories.CleanApkPwaRepository
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.playstore.PlayStoreRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Stores @Inject constructor(
    playStoreRepository: PlayStoreRepository,
    cleanApkAppsRepository: CleanApkAppsRepository,
    cleanApkPwaRepository: CleanApkPwaRepository,
) {

    private val defaultStores = mapOf(
        Source.OPEN_SOURCE to cleanApkAppsRepository,
        Source.PWA to cleanApkPwaRepository,
        Source.PLAY_STORE to playStoreRepository
    )

    private val stores = defaultStores.toMutableMap()

    fun getStores(): Map<Source, StoreRepository> = stores

    fun getStore(source: Source): StoreRepository? = stores[source]

    fun enableStore(source: Source) {
        val repository = defaultStores[source] ?: throw IllegalStateException("store not found")
        stores[source] = repository
    }

    fun disableStore(source: Source) {
        stores.remove(source)
    }

    fun isStoreEnabled(source: Source): Boolean = stores.containsKey(source)
}