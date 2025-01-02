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
    private val stores = mutableMapOf<Source, StoreRepository>()

    fun getStores(): Map<Source, StoreRepository> {
        return stores
    }

    init {
        stores[Source.OPEN_SOURCE] = cleanApkAppsRepository
        stores[Source.PWA] = cleanApkPwaRepository
        stores[Source.PLAY_STORE] = playStoreRepository
    }
}