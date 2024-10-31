/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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
 */

package foundation.e.apps.data.application.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.Stores
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.application.search.FusedHomeDeferred
import foundation.e.apps.data.application.search.SearchApi
import foundation.e.apps.data.cleanapk.repositories.CleanApkAppsRepository
import foundation.e.apps.data.cleanapk.repositories.CleanApkPwaRepository
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.AppLoungePreference
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class HomeApiImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLoungePreference: AppLoungePreference,
    private val stores: Stores
) : HomeApi {

    private enum class AppSourceWeight {
        GPLAY,
        OPEN_SOURCE,
        PWA
    }

    override suspend fun fetchHomeScreenData(): LiveData<ResultSupreme<List<Home>>> {
        val list = mutableListOf<Home>()
        var resultGplay: FusedHomeDeferred? = null
        var resultOpenSource: FusedHomeDeferred? = null
        var resultPWA: FusedHomeDeferred? = null

        return liveData {
            coroutineScope {

                if (appLoungePreference.isGplaySelected()) {
                    resultGplay = async { loadHomeData(list, Source.GPLAY) }
                }

                if (appLoungePreference.isOpenSourceSelected()) {
                    resultOpenSource = async { loadHomeData(list, Source.OPEN) }
                }

                if (appLoungePreference.isPWASelected()) {
                    resultPWA = async { loadHomeData(list, Source.PWA) }
                }

                resultGplay?.await()?.let {
                    emit(it)
                }

                resultOpenSource?.await()?.let {
                    emit(it)
                }

                resultPWA?.await()?.let {
                    emit(it)
                }
            }
        }
    }

    private suspend fun loadHomeData(
        priorList: MutableList<Home>,
        source: Source
    ): ResultSupreme<List<Home>> {
        val result = handleNetworkResult {
            val homeDataBuilder = stores.getStores()[source]
            homeDataBuilder?.getHomeScreenData(priorList)
                ?: throw IllegalStateException("Could not find store for $source")
        }

        setHomeErrorMessage(result.getResultStatus(), source)
        priorList.sortBy {
            when (it.source) {
                SearchApi.APP_TYPE_OPEN -> AppSourceWeight.OPEN_SOURCE.ordinal
                SearchApi.APP_TYPE_PWA -> AppSourceWeight.PWA.ordinal
                else -> AppSourceWeight.GPLAY.ordinal
            }
        }

        return ResultSupreme.create(result.getResultStatus(), priorList)
    }

    private fun setHomeErrorMessage(apiStatus: ResultStatus, source: Source) {
        if (apiStatus != ResultStatus.OK) {
            apiStatus.message = when (source) {
                Source.GPLAY -> ("GPlay home loading error\n" + apiStatus.message).trim()
                Source.GITLAB_RELEASES -> ("Gitlab home not allowed\n" + apiStatus.message).trim()
                Source.OPEN -> ("Open Source home loading error\n" + apiStatus.message).trim()
                Source.PWA -> ("PWA home loading error\n" + apiStatus.message).trim()
            }
        }
    }
}
