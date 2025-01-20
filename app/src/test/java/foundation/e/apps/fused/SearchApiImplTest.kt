/*
 *  Copyright (C) 2022  ECORP
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.fused

import android.content.Context
import android.text.format.Formatter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.SearchBundle
import foundation.e.apps.FakeAppLoungePreference
import foundation.e.apps.data.AppSourcesContainer
import foundation.e.apps.data.Stores
import foundation.e.apps.data.cleanapk.data.search.Search
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.application.search.SearchApiImpl
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.application.apps.AppsApiImpl
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.cleanapk.repositories.CleanApkAppsRepository
import foundation.e.apps.data.cleanapk.repositories.CleanApkPwaRepository
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.install.pkg.PwaManager
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SearchApiImplTest {

    // Run tasks synchronously
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var fusedAPIImpl: SearchApiImpl

    @Mock
    private lateinit var pwaManager: PwaManager

    @Mock
    private lateinit var appLoungePackageManager: AppLoungePackageManager

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var cleanApkAppsRepository: CleanApkAppsRepository

    @Mock
    private lateinit var cleanApkPWARepository: CleanApkPwaRepository

    @Mock
    private lateinit var gPlayAPIRepository: PlayStoreRepository

    @Mock
    private lateinit var stores: Stores

    private lateinit var appsApi: AppsApi

    private lateinit var applicationDataManager: ApplicationDataManager

    private lateinit var preferenceManagerModule: FakeAppLoungePreference

    private lateinit var formatterMocked: MockedStatic<Formatter>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        formatterMocked = Mockito.mockStatic(Formatter::class.java)
        preferenceManagerModule = FakeAppLoungePreference(context)
        applicationDataManager =
            ApplicationDataManager(appLoungePackageManager, pwaManager)
        val appSourcesContainer =
            AppSourcesContainer(gPlayAPIRepository, cleanApkAppsRepository, cleanApkPWARepository)
        appsApi = AppsApiImpl(
            stores,
            applicationDataManager,
        )

        fusedAPIImpl = SearchApiImpl(
            appsApi,
            appSourcesContainer,
            stores,
            applicationDataManager
        )
    }

    @After
    fun after() {
        formatterMocked.close()
    }

    private fun getFusedAppForFilterLevelTest(isFree: Boolean = true) = Application(
        _id = "113",
        name = "Demo Three",
        package_name = "foundation.e.demothree",
        latest_version_code = 123,
        source = Source.OPEN_SOURCE,
        originalSize = -1,
        isFree = isFree,
        price = ""
    )

    @Ignore("Dependencies are not mockable")
    @Test
    fun `getSearchResult When all sources are selected`() = runTest {
        val appList = mutableListOf(
            Application(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            Application(
                _id = "112",
                status = Status.UNAVAILABLE,
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

        val searchResult = Search(apps = appList, numberOfResults = 3, success = true)
        val packageNameSearchResponse = Response.success(searchResult)
        val gplayPackageResult = Application("com.search.package")

        preferenceManagerModule.isPWASelectedFake = true
        preferenceManagerModule.isOpenSourceelectedFake = true
        preferenceManagerModule.isGplaySelectedFake = true
        val gplayFlow: Pair<List<App>, MutableSet<SearchBundle.SubBundle>> = Pair(
            listOf(App("a.b.c"), App("c.d.e"), App("d.e.f"), App("d.e.g")), mutableSetOf()
        )

        val playStoreApps = listOf(
            Application(package_name = "a.b.c"),
            Application(package_name = "c.d.e"),
            Application(package_name = "d.e.f"),
            Application(package_name = "d.e.g"))

        setupMockingSearchApp(playStoreApps, gplayPackageResult)

        val searchResultLiveData =
            fusedAPIImpl.getCleanApkSearchResults("com.search.package")

        val size = searchResultLiveData.data?.first?.size ?: -2
        assertEquals("getSearchResult", 8, size)
    }

    private suspend fun setupMockingSearchApp(
        apps: List<Application>,
        gplayPackageResult: Application,
        willThrowException: Boolean = false
    ) {
        Mockito.`when`(pwaManager.getPwaStatus(any())).thenReturn(Status.UNAVAILABLE)
        Mockito.`when`(appLoungePackageManager.getPackageStatus(any(), any()))
            .thenReturn(Status.UNAVAILABLE)
        Mockito.`when`(
            cleanApkAppsRepository.getSearchResults("com.search.package")
        ).thenReturn(apps)
        formatterMocked.`when`<String> { Formatter.formatFileSize(any(), any()) }.thenReturn("15MB")

        if (willThrowException) {
            Mockito.`when`(gPlayAPIRepository.getAppDetails("com.search.package"))
                .thenThrow(RuntimeException())
        } else {
            Mockito.`when`(gPlayAPIRepository.getAppDetails(eq("com.search.package")))
                .thenReturn(gplayPackageResult)
        }

        Mockito.`when`(cleanApkAppsRepository.getSearchResults("com.search.package"))
            .thenReturn(apps)

        Mockito.`when`(cleanApkPWARepository.getSearchResults("com.search.package"))
            .thenReturn(apps)

        Mockito.`when`(
            cleanApkAppsRepository.getSearchResults("com.search.package")
        ).thenReturn(apps)

        Mockito.`when`(cleanApkAppsRepository.getAppDetails(any()))
            .thenReturn(Application())

        Mockito.`when`(gPlayAPIRepository.getSearchResults(eq("com.search.package")))
            .thenReturn(apps)
    }

    @Ignore("Dependencies are not mockable")
    @Test
    fun `getSearchResult When getApplicationDetailsThrowsException`() = runTest {
        val appList = mutableListOf<Application>(
            Application(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            Application(
                _id = "112",
                status = Status.UNAVAILABLE,
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

        val gplayPackageResult = Application("com.search.package")

        val playStoreApps = listOf(
            Application(package_name = "a.b.c"),
            Application(package_name = "c.d.e"),
            Application(package_name = "d.e.f"),
            Application(package_name = "d.e.g"))

        setupMockingSearchApp(
            playStoreApps, gplayPackageResult, true
        )

        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = true

        val searchResultLiveData =
            fusedAPIImpl.getCleanApkSearchResults("com.search.package")

        val size = searchResultLiveData.data?.first?.size ?: -2
        assertEquals("getSearchResult", 4, size)
    }
}
