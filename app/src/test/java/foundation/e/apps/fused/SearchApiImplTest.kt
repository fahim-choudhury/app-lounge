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
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import foundation.e.apps.FakePreferenceModule
import foundation.e.apps.data.cleanapk.data.search.Search
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.application.search.SearchApiImpl
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.application.apps.AppsApiImpl
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.install.pkg.PWAManagerModule
import foundation.e.apps.install.pkg.PkgManagerModule
import foundation.e.apps.util.MainCoroutineRule
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
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
    private lateinit var pwaManagerModule: PWAManagerModule

    @Mock
    private lateinit var pkgManagerModule: PkgManagerModule

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

    private lateinit var preferenceManagerModule: FakePreferenceModule

    private lateinit var formatterMocked: MockedStatic<Formatter>

    companion object {
        private val AUTH_DATA = AuthData("e@e.email", "AtadyMsIAtadyM")
    }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        formatterMocked = Mockito.mockStatic(Formatter::class.java)
        preferenceManagerModule = FakePreferenceModule(context)
        applicationDataManager =
            ApplicationDataManager(gPlayAPIRepository, pkgManagerModule, pwaManagerModule)

        appsApi = AppsApiImpl(
            context,
            preferenceManagerModule,
            gPlayAPIRepository,
            cleanApkAppsRepository,
            applicationDataManager,
        )

        fusedAPIImpl = SearchApiImpl(
            appsApi,
            preferenceManagerModule,
            gPlayAPIRepository,
            cleanApkAppsRepository,
            cleanApkPWARepository,
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
        origin = Origin.CLEANAPK,
        originalSize = -1,
        isFree = isFree,
        price = ""
    )

    @Ignore("Dependencies are not mockable")
    @Test
    fun `getSearchResult When all sources are selected`() = runTest {
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

        val searchResult = Search(apps = appList, numberOfResults = 3, success = true)
        val packageNameSearchResponse = Response.success(searchResult)
        val gplayPackageResult = App("com.search.package")

        preferenceManagerModule.isPWASelectedFake = true
        preferenceManagerModule.isOpenSourceelectedFake = true
        preferenceManagerModule.isGplaySelectedFake = true
        val gplayFlow: Pair<List<App>, MutableSet<SearchBundle.SubBundle>> = Pair(
            listOf(App("a.b.c"), App("c.d.e"), App("d.e.f"), App("d.e.g")), mutableSetOf()
        )

        setupMockingSearchApp(
            packageNameSearchResponse, gplayPackageResult, gplayFlow
        )

        val searchResultLiveData =
            fusedAPIImpl.getCleanApkSearchResults("com.search.package", AUTH_DATA)

        val size = searchResultLiveData.data?.first?.size ?: -2
        assertEquals("getSearchResult", 8, size)
    }

    private suspend fun setupMockingSearchApp(
        packageNameSearchResponse: Response<Search>?,
        gplayPackageResult: App,
        gplayLivedata: Pair<List<App>, MutableSet<SearchBundle.SubBundle>>,
        willThrowException: Boolean = false
    ) {
        Mockito.`when`(pwaManagerModule.getPwaStatus(any())).thenReturn(Status.UNAVAILABLE)
        Mockito.`when`(pkgManagerModule.getPackageStatus(any(), any()))
            .thenReturn(Status.UNAVAILABLE)
        Mockito.`when`(
            cleanApkAppsRepository.getSearchResult(
                query = "com.search.package", searchBy = "package_name"
            )
        ).thenReturn(packageNameSearchResponse)
        formatterMocked.`when`<String> { Formatter.formatFileSize(any(), any()) }.thenReturn("15MB")

        if (willThrowException) {
            Mockito.`when`(gPlayAPIRepository.getAppDetails("com.search.package"))
                .thenThrow(RuntimeException())
        } else {
            Mockito.`when`(gPlayAPIRepository.getAppDetails(eq("com.search.package")))
                .thenReturn(gplayPackageResult)
        }

        Mockito.`when`(cleanApkAppsRepository.getSearchResult(query = "com.search.package"))
            .thenReturn(packageNameSearchResponse)

        Mockito.`when`(cleanApkPWARepository.getSearchResult(query = "com.search.package"))
            .thenReturn(packageNameSearchResponse)

        Mockito.`when`(
            cleanApkAppsRepository.getSearchResult(
                query = "com.search.package"
            )
        ).thenReturn(packageNameSearchResponse)

        Mockito.`when`(cleanApkAppsRepository.getAppDetails(any()))
            .thenReturn(Response.error<ResponseBody>(404, "".toResponseBody()))

        Mockito.`when`(gPlayAPIRepository.getSearchResult(eq("com.search.package"), null))
            .thenReturn(gplayLivedata)
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

        val searchResult = Search(apps = appList, numberOfResults = 1, success = true)
        val packageNameSearchResponse = Response.success(searchResult)
        val gplayPackageResult = App("com.search.package")

        val gplayFlow: Pair<List<App>, MutableSet<SearchBundle.SubBundle>> = Pair(
            listOf(App("a.b.c"), App("c.d.e"), App("d.e.f"), App("d.e.g")), mutableSetOf()
        )

        setupMockingSearchApp(
            packageNameSearchResponse, gplayPackageResult, gplayFlow, true
        )

        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = true

        val searchResultLiveData =
            fusedAPIImpl.getCleanApkSearchResults("com.search.package", AUTH_DATA)

        val size = searchResultLiveData.data?.first?.size ?: -2
        assertEquals("getSearchResult", 4, size)
    }

    @Test
    fun testSearchResultWhenDataIsLimited() = runTest {
        preferenceManagerModule.isGplaySelectedFake = true
        formatterMocked.`when`<String> { Formatter.formatFileSize(any(), any()) }.thenReturn("15MB")
        Mockito.`when`(gPlayAPIRepository.getSearchResult(anyString(), eq(null)))
            .thenReturn(Pair(emptyList(), mutableSetOf()))
        Mockito.`when`(cleanApkAppsRepository.getAppDetails(any())).thenReturn(null)

        var isEventBusTriggered = false
        val job = launch {
            EventBus.events.collect {
                isEventBusTriggered = true
            }
        }

        fusedAPIImpl.getGplaySearchResult("anything", null)
        delay(500)
        job.cancel()

        assert(isEventBusTriggered)
    }
}
