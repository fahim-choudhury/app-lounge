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
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.AppSourcesContainer
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.application.search.FusedHomeDeferred
import foundation.e.apps.data.application.search.SearchApi
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.preference.AppLoungePreference
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class HomeApiImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLoungePreference: AppLoungePreference,
    private val appSources: AppSourcesContainer,
    private val applicationDataManager: ApplicationDataManager
) : HomeApi {

    companion object {
        private const val THRESHOLD_LIMITED_RESULT_HOME_PAGE = 4
    }

    private enum class AppSourceWeight {
        GPLAY,
        OPEN_SOURCE,
        PWA
    }

    override suspend fun fetchHomeScreenData(authData: AuthData): LiveData<ResultSupreme<List<Home>>> {
        val list = mutableListOf<Home>()
        var resultGplay: FusedHomeDeferred? = null
        var resultOpenSource: FusedHomeDeferred? = null
        var resultPWA: FusedHomeDeferred? = null

        return liveData {
            coroutineScope {

                if (appLoungePreference.isGplaySelected()) {
                    resultGplay = async { loadHomeData(list, Source.GPLAY, authData) }
                }

                if (appLoungePreference.isOpenSourceSelected()) {
                    resultOpenSource = async { loadHomeData(list, Source.OPEN, authData) }
                }

                if (appLoungePreference.isPWASelected()) {
                    resultPWA = async { loadHomeData(list, Source.PWA, authData) }
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
        source: Source,
        authData: AuthData,
    ): ResultSupreme<List<Home>> {

        val result = when (source) {
            Source.GPLAY -> handleNetworkResult {
                fetchGPlayHome(authData, priorList)
            }

            Source.OPEN -> handleNetworkResult {
                handleCleanApkHomes(priorList, SearchApi.APP_TYPE_OPEN)
            }

            Source.PWA -> handleNetworkResult {
                handleCleanApkHomes(priorList, SearchApi.APP_TYPE_PWA)
            }

            Source.GITLAB_RELEASES -> {
                ResultSupreme.Error(message = "Gitlab source not allowed")
            }
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

    private suspend fun handleCleanApkHomes(
        priorList: MutableList<Home>,
        appType: String
    ): MutableList<Home> {
        val homes = if (appType == SearchApi.APP_TYPE_OPEN) {
            appSources.cleanApkAppsRepo.getHomeScreenData()
        } else {
            appSources.cleanApkPWARepo.getHomeScreenData()
        }

        homes.forEach { (title, list) ->
            priorList.add(Home(title, list, appType))
        }

        return priorList
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

    private suspend fun fetchGPlayHome(
        authData: AuthData,
        priorList: MutableList<Home>
    ): List<Home> {
        val list = mutableListOf<Home>()
        val gplayHomeData = appSources.gplayRepo.getHomeScreenData()

        gplayHomeData.map {
            val fusedApps = it.value.map { app ->
                app.apply {
                    applicationDataManager.updateStatus(this)
                    applicationDataManager.updateFilterLevel(authData, this)
                }
            }
            list.add(Home(it.key, fusedApps))
        }

        handleLimitedResult(list)
        Timber.d("HomePageData: $list")

        priorList.addAll(list)
        return priorList
    }

    private fun handleLimitedResult(homeList: List<Home>) {
        val gplayHomes = homeList.filter { fusedHome -> fusedHome.source.isEmpty() }
        val hasGplayLimitedResult = gplayHomes.any { fusedHome ->
            fusedHome.list.size < THRESHOLD_LIMITED_RESULT_HOME_PAGE
        }

        if (hasGplayLimitedResult) {
            Timber.w("Limited result is found for homepage...")
            refreshToken()
        }
    }

    private fun refreshToken() {
        MainScope().launch {
            EventBus.invokeEvent(
                AppEvent.InvalidAuthEvent(AuthObject.GPlayAuth::class.java.simpleName)
            )
        }
    }
}
