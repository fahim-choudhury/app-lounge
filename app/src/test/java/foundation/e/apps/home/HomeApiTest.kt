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

package foundation.e.apps.home

import android.content.Context
import android.text.format.Formatter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.FakeAppLoungePreference
import foundation.e.apps.data.AppSourcesContainer
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.home.HomeApi
import foundation.e.apps.data.application.home.HomeApiImpl
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.install.pkg.PWAManager
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.util.MainCoroutineRule
import foundation.e.apps.util.getOrAwaitValue
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any

class HomeApiTest {

    // Run tasks synchronously
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var homeApi: HomeApi

    private lateinit var applicationDataManager: ApplicationDataManager

    @Mock
    private lateinit var pwaManager: PWAManager

    @Mock
    private lateinit var appLoungePackageManager: AppLoungePackageManager

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var cleanApkAppsRepository: CleanApkRepository

    @Mock
    private lateinit var cleanApkPWARepository: CleanApkRepository

    @Mock
    private lateinit var gPlayAPIRepository: PlayStoreRepository

    private lateinit var preferenceManagerModule: FakeAppLoungePreference

    private lateinit var formatterMocked: MockedStatic<Formatter>

    companion object {
        private val AUTH_DATA = AuthData("e@e.email", "AtadyMsIAtadyM")
    }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        formatterMocked = Mockito.mockStatic(Formatter::class.java)
        preferenceManagerModule = FakeAppLoungePreference(context)
        applicationDataManager =
            ApplicationDataManager(gPlayAPIRepository, appLoungePackageManager, pwaManager)
        val appSourcesContainer =
            AppSourcesContainer(gPlayAPIRepository, cleanApkAppsRepository, cleanApkPWARepository)
        homeApi = HomeApiImpl(
            context,
            preferenceManagerModule,
            appSourcesContainer,
            applicationDataManager
        )
    }

    @Test
    fun testHomeScreenDataWhenDataIsLimited() = runTest {
        val newAppList = mutableListOf<App>(
            App("foundation.e.demoone"),
            App("foundation.e.demotwo"),
            App("foundation.e.demothree"),
        )

        var newHomeData = mapOf<String, List<App>>(Pair("Top Free Apps", newAppList))
        preferenceManagerModule.isGplaySelectedFake = true

        formatterMocked.`when`<String> { Formatter.formatFileSize(any(), any()) }.thenReturn("15MB")
        Mockito.`when`(gPlayAPIRepository.getHomeScreenData()).thenReturn(newHomeData)
        Mockito.`when`(gPlayAPIRepository.getAppDetails(ArgumentMatchers.anyString())).thenReturn(
            App("foundation.e.demothree")
        )
        Mockito.`when`(
            gPlayAPIRepository.getDownloadInfo(
                ArgumentMatchers.anyString(),
                any(),
                any()
            )
        ).thenReturn(listOf())
        Mockito.`when`(appLoungePackageManager.getPackageStatus(any(), any(), any()))
            .thenReturn(Status.UNAVAILABLE)

        var hasLimitedDataFound = false
        val job = launch {
            EventBus.events.collect {
                hasLimitedDataFound = true
            }
        }

        homeApi.fetchHomeScreenData(AUTH_DATA).getOrAwaitValue()
        delay(500)
        job.cancel()

        assert(hasLimitedDataFound)
    }
}