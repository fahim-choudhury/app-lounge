/*
 * Copyright (C) 2025 e Foundation
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

package foundation.e.apps.data

import foundation.e.apps.data.cleanapk.repositories.CleanApkAppsRepository
import foundation.e.apps.data.cleanapk.repositories.CleanApkPwaRepository
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.enums.Source.OPEN_SOURCE
import foundation.e.apps.data.enums.Source.PLAY_STORE
import foundation.e.apps.data.enums.Source.PWA
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.AppLoungePreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Stores @Inject constructor(
    private val playStoreRepository: PlayStoreRepository,
    private val cleanApkAppsRepository: CleanApkAppsRepository,
    private val cleanApkPwaRepository: CleanApkPwaRepository,
    private val appLoungePreference: AppLoungePreference
) {

    /**
     * Retrieves a map of enabled store repositories based on user preferences.
     *
     * @return A map where the keys are the selected [Source] types and
     * the values are their corresponding [StoreRepository] instances.
     */
    fun getStores(): Map<Source, StoreRepository> {
        val showOpenSourceApps = appLoungePreference.isOpenSourceSelected()
        val showPwaApps = appLoungePreference.isPWASelected()
        val showPlayStoreApps = appLoungePreference.isPlayStoreSelected()

        return buildMap {
            if (showPlayStoreApps) {
                put(PLAY_STORE, playStoreRepository)
            }
            if (showOpenSourceApps) {
                put(OPEN_SOURCE, cleanApkAppsRepository)
            }
            if (showPwaApps) {
                put(PWA, cleanApkPwaRepository)
            }
        }
    }

    fun getStore(source: Source): StoreRepository? = getStores()[source]

    fun enableStore(source: Source) = when (source) {
        OPEN_SOURCE -> appLoungePreference.enableOpenSource()
        PWA -> appLoungePreference.enablePwa()
        PLAY_STORE -> appLoungePreference.enablePlayStore()
        else -> error("No matching Store found for $source.")
    }

    fun disableStore(source: Source) = when (source) {
        OPEN_SOURCE -> appLoungePreference.disableOpenSource()
        PWA -> appLoungePreference.disablePwa()
        PLAY_STORE -> appLoungePreference.disablePlayStore()
        else -> error("No matching Store found for $source.")
    }

    fun isStoreEnabled(source: Source): Boolean = getStores().containsKey(source)
}
