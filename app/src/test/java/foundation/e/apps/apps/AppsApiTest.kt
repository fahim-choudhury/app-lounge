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

package foundation.e.apps.apps

import android.content.Context
import android.text.format.Formatter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.Constants
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.FakeAppLoungePreference
import foundation.e.apps.data.AppSourcesContainer
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.application.apps.AppsApiImpl
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.install.pkg.PWAManager
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
class AppsApiTest {

    // Run tasks synchronously
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

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

    private lateinit var appsApi: AppsApi

    private lateinit var applicationDataManager: ApplicationDataManager

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
        appsApi = AppsApiImpl(
            context,
            preferenceManagerModule,
            appSourcesContainer,
            applicationDataManager
        )
    }

    @After
    fun after() {
        formatterMocked.close()
    }

    @Test
    fun `is any app updated when new list is empty`() {
        val oldAppList = mutableListOf<Application>(
            Application(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone"
            ),
            Application(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo"
            ),
            Application(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree"
            )
        )

        val newAppList = mutableListOf<Application>()
        val isFusedAppUpdated = appsApi.isAnyFusedAppUpdated(newAppList, oldAppList)
        assertTrue("isAnyAppUpdated", isFusedAppUpdated)
    }

    @Test
    fun `is any app updated when both list are empty`() {
        val isFusedAppUpdated = appsApi.isAnyFusedAppUpdated(listOf(), listOf())
        assertFalse("isAnyAppUpdated", isFusedAppUpdated)
    }

    @Test
    fun `is any app updated when any app is uninstalled`() {
        val oldAppList = mutableListOf<Application>(
            Application(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone"
            ),
            Application(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo"
            ),
            Application(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree"
            )
        )

        val newAppList = mutableListOf<Application>(
            Application(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone"
            ),
            Application(
                _id = "112",
                status = Status.UNAVAILABLE,
                name = "Demo Two",
                package_name = "foundation.e.demotwo"
            ),
            Application(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree"
            )
        )

        val isFusedAppUpdated = appsApi.isAnyFusedAppUpdated(newAppList, oldAppList)
        assertTrue("isAnyFusedAppUpdated", isFusedAppUpdated)
    }

    @Test
    fun `has any app install status changed when changed`() {
        val oldAppList = mutableListOf<Application>(
            Application(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            Application(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            Application(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                latest_version_code = 123
            )
        )

        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                eq("foundation.e.demoone"),
                eq(123),
                eq(""),
            )
        )
            .thenReturn(
                Status.UNAVAILABLE
            )
        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                eq("foundation.e.demotwo"),
                eq(123),
                eq(""),
            )
        )
            .thenReturn(
                Status.UNAVAILABLE
            )
        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                eq("foundation.e.demothree"),
                eq(123),
                eq(""),
            )
        )
            .thenReturn(
                Status.UNAVAILABLE
            )

        val isAppStatusUpdated = appsApi.isAnyAppInstallStatusChanged(oldAppList)
        assertTrue("hasInstallStatusUpdated", isAppStatusUpdated)
    }

    @Test
    fun `has any app install status changed when not changed`() {
        val oldAppList = mutableListOf<Application>(
            Application(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            Application(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            Application(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                latest_version_code = 123
            )
        )

        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                eq("foundation.e.demoone"),
                eq(123),
                eq(""),
            )
        )
            .thenReturn(
                Status.UNAVAILABLE
            )
        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                eq("foundation.e.demotwo"),
                eq(123),
                eq(""),
            )
        )
            .thenReturn(
                Status.INSTALLED
            )
        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                eq("foundation.e.demothree"),
                eq(123),
                eq(""),
            )
        )
            .thenReturn(
                Status.UNAVAILABLE
            )

        val isAppStatusUpdated = appsApi.isAnyAppInstallStatusChanged(oldAppList)
        assertFalse("hasInstallStatusUpdated", isAppStatusUpdated)
    }

    @Test
    fun `has any app install status changed when installation_issue`() {
        val oldAppList = mutableListOf<Application>(
            Application(
                _id = "111",
                status = Status.INSTALLATION_ISSUE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            Application(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            Application(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                latest_version_code = 123
            )
        )

        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                eq("foundation.e.demoone"),
                eq(123),
                eq(""),
            )
        )
            .thenReturn(
                Status.UNAVAILABLE
            )
        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                eq("foundation.e.demotwo"),
                eq(123),
                eq(""),
            )
        )
            .thenReturn(
                Status.INSTALLED
            )
        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                eq("foundation.e.demothree"),
                eq(123),
                eq(""),
            )
        )
            .thenReturn(
                Status.UNAVAILABLE
            )

        val isAppStatusUpdated = appsApi.isAnyAppInstallStatusChanged(oldAppList)
        assertFalse("hasInstallStatusUpdated", isAppStatusUpdated)
    }


    @Test
    fun getFusedAppInstallationStatusWhenPWA() {
        val application = Application(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            is_pwa = true
        )

        Mockito.`when`(pwaManager.getPwaStatus(application)).thenReturn(application.status)

        val installationStatus = appsApi.getFusedAppInstallationStatus(application)
        assertEquals("getFusedAppInstallationStatusWhenPWA", application.status, installationStatus)
    }

    @Test
    fun getFusedAppInstallationStatus() {
        val application = Application(
            _id = "113",
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
        )

        Mockito.`when`(
            appLoungePackageManager.getPackageStatus(
                application.package_name, application.latest_version_code
            )
        ).thenReturn(Status.INSTALLED)

        val installationStatus = appsApi.getFusedAppInstallationStatus(application)
        assertEquals("getFusedAppInstallationStatusWhenPWA", Status.INSTALLED, installationStatus)
    }

    @Test
    fun `getAppFilterLevel when package name is empty`() = runTest {
        val application = Application(
            _id = "113",
            name = "Demo Three",
            package_name = "",
            latest_version_code = 123,
        )

        val filterLevel = appsApi.getAppFilterLevel(application, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.UNKNOWN, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is CleanApk`() = runTest {
        val fusedApp = getFusedAppForFilterLevelTest()

        val filterLevel = appsApi.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.NONE, filterLevel)
    }

    private fun getFusedAppForFilterLevelTest(isFree: Boolean = true) = Application(
        _id = "113",
        name = "Demo Three",
        package_name = "foundation.e.demothree",
        latest_version_code = 123,
        origin = Origin.CLEANAPK,
        originalSize = -1,
        isFree = isFree,
        price = ""
    )

    @Test
    fun `getAppFilterLevel when Authdata is NULL`() = runTest {
        val fusedApp = getFusedAppForFilterLevelTest()

        val filterLevel = appsApi.getAppFilterLevel(fusedApp, null)
        assertEquals("getAppFilterLevel", FilterLevel.NONE, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is restricted and paid and no price`() = runTest {
        val fusedApp = getFusedAppForFilterLevelTest(false).apply {
            this.origin = Origin.GPLAY
            this.restriction = Constants.Restriction.UNKNOWN
        }

        val filterLevel = appsApi.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.UI, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is not_restricted and paid and no price`() = runTest {
        val fusedApp = getFusedAppForFilterLevelTest(false).apply {
            this.origin = Origin.GPLAY
            this.restriction = Constants.Restriction.NOT_RESTRICTED
        }

        val filterLevel = appsApi.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.UI, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is restricted and getAppDetails and getDownloadDetails returns success`() =
        runTest {
            val fusedApp = getFusedAppForFilterLevelTest().apply {
                this.origin = Origin.GPLAY
                this.restriction = Constants.Restriction.UNKNOWN
            }

            Mockito.`when`(gPlayAPIRepository.getAppDetails(fusedApp.package_name))
                .thenReturn(App(fusedApp.package_name))

            Mockito.`when`(
                gPlayAPIRepository.getDownloadInfo(
                    fusedApp.package_name,
                    fusedApp.latest_version_code,
                    fusedApp.offer_type,
                )
            ).thenReturn(listOf())

            val filterLevel = appsApi.getAppFilterLevel(fusedApp, AUTH_DATA)
            assertEquals("getAppFilterLevel", FilterLevel.NONE, filterLevel)
        }

    @Test
    fun `getAppFilterLevel when app is restricted and getAppDetails throws exception`() = runTest {
        val fusedApp = getFusedAppForFilterLevelTest().apply {
            this.origin = Origin.GPLAY
            this.restriction = Constants.Restriction.UNKNOWN
        }

        Mockito.`when`(gPlayAPIRepository.getAppDetails(fusedApp.package_name))
            .thenThrow(RuntimeException())

        Mockito.`when`(
            gPlayAPIRepository.getDownloadInfo(
                fusedApp.package_name, fusedApp.latest_version_code, fusedApp.offer_type
            )
        ).thenReturn(listOf())

        val filterLevel = appsApi.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.DATA, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is restricted and getDownoadInfo throws exception`() = runTest {
        val fusedApp = getFusedAppForFilterLevelTest().apply {
            this.origin = Origin.GPLAY
            this.restriction = Constants.Restriction.UNKNOWN
        }

        Mockito.`when`(gPlayAPIRepository.getAppDetails(fusedApp.package_name))
            .thenReturn(App(fusedApp.package_name))

        Mockito.`when`(
            gPlayAPIRepository.getDownloadInfo(
                fusedApp.package_name, fusedApp.latest_version_code, fusedApp.offer_type
            )
        ).thenThrow(RuntimeException())

        val filterLevel = appsApi.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.UI, filterLevel)
    }
}
