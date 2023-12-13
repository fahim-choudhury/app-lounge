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
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.application.search.FusedHomeDeferred
import foundation.e.apps.data.application.search.SearchApi
import foundation.e.apps.data.application.utils.toApplication
import foundation.e.apps.data.cleanapk.data.home.HomeScreen
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.AppLoungePreference
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import foundation.e.apps.data.cleanapk.data.home.Home as CleanApkHome

class HomeApiImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLoungePreference: AppLoungePreference,
    @Named("gplayRepository") private val gplayRepository: PlayStoreRepository,
    @Named("cleanApkAppsRepository") private val cleanApkAppsRepository: CleanApkRepository,
    @Named("cleanApkPWARepository") private val cleanApkPWARepository: CleanApkRepository,
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
        val response = if (appType == SearchApi.APP_TYPE_OPEN) {
            (cleanApkAppsRepository.getHomeScreenData() as Response<HomeScreen>).body()
        } else {
            (cleanApkPWARepository.getHomeScreenData() as Response<HomeScreen>).body()
        }

        response?.home?.let {
            priorList.addAll(generateCleanAPKHome(it, appType))
        }

        return priorList
    }

    private suspend fun generateCleanAPKHome(home: CleanApkHome, appType: String): List<Home> {
        val list = mutableListOf<Home>()
        val headings = if (appType == SearchApi.APP_TYPE_OPEN) {
            getOpenSourceHomeCategories()
        } else {
            getPWAHomeCategories()
        }

        headings.forEach { (key, value) ->
            when (key) {
                "top_updated_apps" -> {
                    applicationDataManager.prepareApps(home.top_updated_apps, list, value)
                }

                "top_updated_games" -> {
                    applicationDataManager.prepareApps(home.top_updated_games, list, value)
                }

                "popular_apps" -> {
                    applicationDataManager.prepareApps(home.popular_apps, list, value)
                }

                "popular_games" -> {
                    applicationDataManager.prepareApps(home.popular_games, list, value)
                }

                "popular_apps_in_last_24_hours" -> {
                    applicationDataManager.prepareApps(home.popular_apps_in_last_24_hours, list, value)
                }

                "popular_games_in_last_24_hours" -> {
                    applicationDataManager.prepareApps(home.popular_games_in_last_24_hours, list, value)
                }

                "discover" -> {
                    applicationDataManager.prepareApps(home.discover, list, value)
                }
            }
        }

        return list.map {
            it.source = appType
            it
        }
    }

    private fun getPWAHomeCategories() = mapOf(
        "popular_apps" to context.getString(R.string.popular_apps),
        "popular_games" to context.getString(R.string.popular_games),
        "discover" to context.getString(R.string.discover_pwa)
    )

    private fun getOpenSourceHomeCategories() = mapOf(
        "top_updated_apps" to context.getString(R.string.top_updated_apps),
        "top_updated_games" to context.getString(R.string.top_updated_games),
        "popular_apps_in_last_24_hours" to context.getString(R.string.popular_apps_in_last_24_hours),
        "popular_games_in_last_24_hours" to context.getString(R.string.popular_games_in_last_24_hours),
        "discover" to context.getString(R.string.discover)
    )

    private fun setHomeErrorMessage(apiStatus: ResultStatus, source: Source) {
        if (apiStatus != ResultStatus.OK) {
            apiStatus.message = when (source) {
                Source.GPLAY -> ("GPlay home loading error\n" + apiStatus.message).trim()
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
        val gplayHomeData = gplayRepository.getHomeScreenData() as Map<String, List<App>>
        gplayHomeData.map {
            val fusedApps = it.value.map { app ->
                app.toApplication(context).apply {
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
