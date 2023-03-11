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

package foundation.e.apps

import android.content.Context
import android.text.format.Formatter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.aurora.gplayapi.Constants
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import foundation.e.apps.api.cleanapk.CleanAPKInterface
import foundation.e.apps.api.cleanapk.CleanAPKRepository
import foundation.e.apps.api.cleanapk.data.categories.Categories
import foundation.e.apps.api.cleanapk.data.search.Search
import foundation.e.apps.api.fdroid.FdroidWebInterface
import foundation.e.apps.api.fused.FusedAPIImpl
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.fused.data.FusedHome
import foundation.e.apps.api.gplay.GPlayAPIRepository
import foundation.e.apps.login.LoginSourceRepository
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.util.MainCoroutineRule
import foundation.e.apps.util.getOrAwaitValue
import foundation.e.apps.utils.enums.FilterLevel
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.modules.PWAManagerModule
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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class FusedApiImplTest {

    // Run tasks synchronously
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var fusedAPIImpl: FusedAPIImpl

    @Mock
    private lateinit var pwaManagerModule: PWAManagerModule

    @Mock
    private lateinit var pkgManagerModule: PkgManagerModule

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var cleanApkRepository: CleanAPKRepository

    @Mock
    private lateinit var gPlayAPIRepository: GPlayAPIRepository

    @Mock
    private lateinit var fdroidWebInterface: FdroidWebInterface

    @Mock
    private lateinit var loginSourceRepository: LoginSourceRepository

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
        fusedAPIImpl = FusedAPIImpl(
            cleanApkRepository,
            gPlayAPIRepository,
            pkgManagerModule,
            pwaManagerModule,
            preferenceManagerModule,
            fdroidWebInterface,
            loginSourceRepository,
            context
        )
    }

    @After
    fun after() {
        formatterMocked.close()
    }

    @Test
    fun `is any app updated when new list is empty`() {
        val oldAppList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone"
            ),
            FusedApp(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo"
            ),
            FusedApp(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree"
            )
        )

        val newAppList = mutableListOf<FusedApp>()
        val isFusedAppUpdated = fusedAPIImpl.isAnyFusedAppUpdated(newAppList, oldAppList)
        assertTrue("isAnyAppUpdated", isFusedAppUpdated)
    }

    @Test
    fun `is any app updated when both list are empty`() {
        val isFusedAppUpdated = fusedAPIImpl.isAnyFusedAppUpdated(listOf(), listOf())
        assertFalse("isAnyAppUpdated", isFusedAppUpdated)
    }

    @Test
    fun `is any app updated when any app is uninstalled`() {
        val oldAppList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone"
            ),
            FusedApp(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo"
            ),
            FusedApp(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree"
            )
        )

        val newAppList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone"
            ),
            FusedApp(
                _id = "112",
                status = Status.UNAVAILABLE,
                name = "Demo Two",
                package_name = "foundation.e.demotwo"
            ),
            FusedApp(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree"
            )
        )

        val isFusedAppUpdated = fusedAPIImpl.isAnyFusedAppUpdated(newAppList, oldAppList)
        assertTrue("isAnyFusedAppUpdated", isFusedAppUpdated)
    }

    @Test
    fun `has any app install status changed when changed`() {
        val oldAppList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                latest_version_code = 123
            )
        )

        Mockito.`when`(pkgManagerModule.getPackageStatus(eq("foundation.e.demoone"), eq(123)))
            .thenReturn(
                Status.UNAVAILABLE
            )
        Mockito.`when`(pkgManagerModule.getPackageStatus(eq("foundation.e.demotwo"), eq(123)))
            .thenReturn(
                Status.UNAVAILABLE
            )
        Mockito.`when`(pkgManagerModule.getPackageStatus(eq("foundation.e.demothree"), eq(123)))
            .thenReturn(
                Status.UNAVAILABLE
            )

        val isAppStatusUpdated = fusedAPIImpl.isAnyAppInstallStatusChanged(oldAppList)
        assertTrue("hasInstallStatusUpdated", isAppStatusUpdated)
    }

    @Test
    fun `has any app install status changed when not changed`() {
        val oldAppList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                latest_version_code = 123
            )
        )

        Mockito.`when`(pkgManagerModule.getPackageStatus(eq("foundation.e.demoone"), eq(123)))
            .thenReturn(
                Status.UNAVAILABLE
            )
        Mockito.`when`(pkgManagerModule.getPackageStatus(eq("foundation.e.demotwo"), eq(123)))
            .thenReturn(
                Status.INSTALLED
            )
        Mockito.`when`(pkgManagerModule.getPackageStatus(eq("foundation.e.demothree"), eq(123)))
            .thenReturn(
                Status.UNAVAILABLE
            )

        val isAppStatusUpdated = fusedAPIImpl.isAnyAppInstallStatusChanged(oldAppList)
        assertFalse("hasInstallStatusUpdated", isAppStatusUpdated)
    }

    @Test
    fun `has any app install status changed when installation_issue`() {
        val oldAppList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.INSTALLATION_ISSUE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                latest_version_code = 123
            )
        )

        Mockito.`when`(pkgManagerModule.getPackageStatus(eq("foundation.e.demoone"), eq(123)))
            .thenReturn(
                Status.UNAVAILABLE
            )
        Mockito.`when`(pkgManagerModule.getPackageStatus(eq("foundation.e.demotwo"), eq(123)))
            .thenReturn(
                Status.INSTALLED
            )
        Mockito.`when`(pkgManagerModule.getPackageStatus(eq("foundation.e.demothree"), eq(123)))
            .thenReturn(
                Status.UNAVAILABLE
            )

        val isAppStatusUpdated = fusedAPIImpl.isAnyAppInstallStatusChanged(oldAppList)
        assertFalse("hasInstallStatusUpdated", isAppStatusUpdated)
    }

    @Test
    fun isHomeDataUpdated() {
        val oldAppList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.INSTALLATION_ISSUE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                latest_version_code = 123
            )
        )

        val newAppList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.INSTALLATION_ISSUE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "112",
                status = Status.UNAVAILABLE,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "113",
                status = Status.UNAVAILABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                latest_version_code = 123
            )
        )

        val oldHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", oldAppList))
        var newHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", oldAppList))
        var isHomeDataUpdated = fusedAPIImpl.isHomeDataUpdated(newHomeData, oldHomeData)
        assertFalse("isHomeDataUpdated/NO", isHomeDataUpdated)
        newHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", newAppList))

        isHomeDataUpdated = fusedAPIImpl.isHomeDataUpdated(newHomeData, oldHomeData)
        assertTrue("isHomeDataUpdated/YES", isHomeDataUpdated)
    }

    @Test
    fun isHomeDataUpdatedWhenBothAreEmpty() {
        val oldHomeData = listOf<FusedHome>()
        val newHomeData = listOf<FusedHome>()
        val isHomeDataUpdated = fusedAPIImpl.isHomeDataUpdated(oldHomeData, newHomeData)
        assertFalse("isHomeDataUpdated", isHomeDataUpdated)
    }

    @Test
    fun `is home data updated when fusedapp list size is not same`() {
        val oldAppList = mutableListOf(FusedApp(), FusedApp(), FusedApp())
        val newAppList = mutableListOf(FusedApp(), FusedApp())

        val oldHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", oldAppList))
        var newHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", newAppList))

        val isHomeDataUpdated = fusedAPIImpl.isHomeDataUpdated(newHomeData, oldHomeData)
        assertTrue("isHomeDataUpdated/YES", isHomeDataUpdated)
    }

    @Test
    fun getFusedAppInstallationStatusWhenPWA() {
        val fusedApp = FusedApp(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            is_pwa = true
        )

        Mockito.`when`(pwaManagerModule.getPwaStatus(fusedApp)).thenReturn(fusedApp.status)

        val installationStatus = fusedAPIImpl.getFusedAppInstallationStatus(fusedApp)
        assertEquals("getFusedAppInstallationStatusWhenPWA", fusedApp.status, installationStatus)
    }

    @Test
    fun getFusedAppInstallationStatus() {
        val fusedApp = FusedApp(
            _id = "113",
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
        )

        Mockito.`when`(
            pkgManagerModule.getPackageStatus(
                fusedApp.package_name, fusedApp.latest_version_code
            )
        ).thenReturn(Status.INSTALLED)

        val installationStatus = fusedAPIImpl.getFusedAppInstallationStatus(fusedApp)
        assertEquals("getFusedAppInstallationStatusWhenPWA", Status.INSTALLED, installationStatus)
    }

    @Test
    fun `getAppFilterLevel when package name is empty`() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            name = "Demo Three",
            package_name = "",
            latest_version_code = 123,
        )

        val filterLevel = fusedAPIImpl.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.UNKNOWN, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is CleanApk`() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            origin = Origin.CLEANAPK
        )

        val filterLevel = fusedAPIImpl.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.NONE, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when Authdata is NULL`() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            origin = Origin.CLEANAPK
        )

        val filterLevel = fusedAPIImpl.getAppFilterLevel(fusedApp, null)
        assertEquals("getAppFilterLevel", FilterLevel.NONE, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is restricted and paid and no price`() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            origin = Origin.GPLAY,
            restriction = Constants.Restriction.UNKNOWN,
            isFree = false,
            price = ""
        )

        val filterLevel = fusedAPIImpl.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.UI, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is not_restricted and paid and no price`() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            origin = Origin.GPLAY,
            restriction = Constants.Restriction.NOT_RESTRICTED,
            isFree = false,
            price = ""
        )

        val filterLevel = fusedAPIImpl.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.UI, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is restricted and getAppDetails and getDownloadDetails returns success`() =
        runTest {
            val fusedApp = FusedApp(
                _id = "113",
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                latest_version_code = 123,
                origin = Origin.GPLAY,
                restriction = Constants.Restriction.UNKNOWN,
                isFree = true,
                price = ""
            )

            Mockito.`when`(gPlayAPIRepository.getAppDetails(fusedApp.package_name, AUTH_DATA))
                .thenReturn(App(fusedApp.package_name))

            Mockito.`when`(
                gPlayAPIRepository.getDownloadInfo(
                    fusedApp.package_name,
                    fusedApp.latest_version_code,
                    fusedApp.offer_type,
                    AUTH_DATA
                )
            ).thenReturn(listOf())

            val filterLevel = fusedAPIImpl.getAppFilterLevel(fusedApp, AUTH_DATA)
            assertEquals("getAppFilterLevel", FilterLevel.NONE, filterLevel)
        }

    @Test
    fun `getAppFilterLevel when app is restricted and getAppDetails throws exception`() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            origin = Origin.GPLAY,
            restriction = Constants.Restriction.UNKNOWN,
            isFree = true,
            price = ""
        )

        Mockito.`when`(gPlayAPIRepository.getAppDetails(fusedApp.package_name, AUTH_DATA))
            .thenThrow(RuntimeException())

        Mockito.`when`(
            gPlayAPIRepository.getDownloadInfo(
                fusedApp.package_name, fusedApp.latest_version_code, fusedApp.offer_type, AUTH_DATA
            )
        ).thenReturn(listOf())

        val filterLevel = fusedAPIImpl.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.DATA, filterLevel)
    }

    @Test
    fun `getAppFilterLevel when app is restricted and getDownoadInfo throws exception`() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            origin = Origin.GPLAY,
            restriction = Constants.Restriction.UNKNOWN,
            isFree = true,
            price = ""
        )

        Mockito.`when`(gPlayAPIRepository.getAppDetails(fusedApp.package_name, AUTH_DATA))
            .thenReturn(App(fusedApp.package_name))

        Mockito.`when`(
            gPlayAPIRepository.getDownloadInfo(
                fusedApp.package_name, fusedApp.latest_version_code, fusedApp.offer_type, AUTH_DATA
            )
        ).thenThrow(RuntimeException())

        val filterLevel = fusedAPIImpl.getAppFilterLevel(fusedApp, AUTH_DATA)
        assertEquals("getAppFilterLevel", FilterLevel.UI, filterLevel)
    }

    @Test
    fun `getCategory when only pwa is selected`() = runTest {
        val categories =
            Categories(listOf("app one", "app two", "app three"), listOf("game 1", "game 2"), true)
        val response = Response.success(categories)
        preferenceManagerModule.isPWASelectedFake = true
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = false

        Mockito.`when`(
            cleanApkRepository.getCategoriesList(
                eq(CleanAPKInterface.APP_TYPE_PWA), eq(CleanAPKInterface.APP_SOURCE_ANY)
            )
        ).thenReturn(response)

        Mockito.`when`(context.getString(eq(R.string.pwa))).thenReturn("PWA")

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(Category.Type.APPLICATION, AUTH_DATA)

        assertEquals("getCategory", 3, categoryListResponse.first.size)
    }

    @Test
    fun `getCategory when only open source is selected`() = runTest {
        val categories =
            Categories(listOf("app one", "app two", "app three"), listOf("game 1", "game 2"), true)
        val response = Response.success(categories)

        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = true
        preferenceManagerModule.isGplaySelectedFake = false

        Mockito.`when`(
            cleanApkRepository.getCategoriesList(
                eq(CleanAPKInterface.APP_TYPE_ANY), eq(CleanAPKInterface.APP_SOURCE_FOSS)
            )
        ).thenReturn(response)
        Mockito.`when`(context.getString(eq(R.string.open_source))).thenReturn("Open source")

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(Category.Type.APPLICATION, AUTH_DATA)

        assertEquals("getCategory", 3, categoryListResponse.first.size)
    }

    @Test
    fun `getCategory when gplay source is selected`() = runTest {
        val categories = listOf(Category(), Category(), Category(), Category())

        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = true

        Mockito.`when`(
            gPlayAPIRepository.getCategoriesList(Category.Type.APPLICATION, AUTH_DATA)
        ).thenReturn(categories)

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(Category.Type.APPLICATION, AUTH_DATA)

        assertEquals("getCategory", 4, categoryListResponse.first.size)
    }

    @Test
    fun `getCategory when gplay source is selected return error`() = runTest {
        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = true

        Mockito.`when`(
            gPlayAPIRepository.getCategoriesList(Category.Type.APPLICATION, AUTH_DATA)
        ).thenThrow(RuntimeException())

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(Category.Type.APPLICATION, AUTH_DATA)

        assertEquals("getCategory", 0, categoryListResponse.first.size)
        assertEquals("getCategory", ResultStatus.UNKNOWN, categoryListResponse.third)
    }

    @Test
    fun `getCategory when All source is selected`() = runTest {
        val gplayCategories = listOf(Category(), Category(), Category(), Category())
        val openSourcecategories = Categories(
            listOf("app one", "app two", "app three", "app four"), listOf("game 1", "game 2"), true
        )
        val openSourceResponse = Response.success(openSourcecategories)
        val pwaCategories =
            Categories(listOf("app one", "app two", "app three"), listOf("game 1", "game 2"), true)
        val pwaResponse = Response.success(pwaCategories)

        Mockito.`when`(
            cleanApkRepository.getCategoriesList(
                eq(CleanAPKInterface.APP_TYPE_ANY), eq(CleanAPKInterface.APP_SOURCE_FOSS)
            )
        ).thenReturn(openSourceResponse)

        Mockito.`when`(
            cleanApkRepository.getCategoriesList(
                eq(CleanAPKInterface.APP_TYPE_PWA), eq(CleanAPKInterface.APP_SOURCE_ANY)
            )
        ).thenReturn(pwaResponse)

        Mockito.`when`(
            gPlayAPIRepository.getCategoriesList(Category.Type.APPLICATION, AUTH_DATA)
        ).thenReturn(gplayCategories)

        Mockito.`when`(context.getString(eq(R.string.open_source))).thenReturn("Open source")
        Mockito.`when`(context.getString(eq(R.string.pwa))).thenReturn("pwa")

        preferenceManagerModule.isPWASelectedFake = true
        preferenceManagerModule.isOpenSourceelectedFake = true
        preferenceManagerModule.isGplaySelectedFake = true

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(Category.Type.APPLICATION, AUTH_DATA)

        assertEquals("getCategory", 11, categoryListResponse.first.size)
    }

    @Test
    fun `getSearchResult When all sources are selected`() = runTest {
        val appList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "112",
                status = Status.UNAVAILABLE,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            FusedApp(
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

        preferenceManagerModule.isPWASelectedFake = true
        preferenceManagerModule.isOpenSourceelectedFake = true
        preferenceManagerModule.isGplaySelectedFake = true
        val gplayLivedata: LiveData<Pair<List<FusedApp>, Boolean>> = MutableLiveData(
            Pair(
                listOf(FusedApp("a.b.c"), FusedApp("c.d.e"), FusedApp("d.e.f"), FusedApp("d.e.g")),
                false
            )
        )

        setupMockingSearchApp(
            packageNameSearchResponse, AUTH_DATA, gplayPackageResult, gplayLivedata
        )

        val searchResultLiveData =
            fusedAPIImpl.getSearchResults("com.search.package", AUTH_DATA).getOrAwaitValue(12)

        val size = searchResultLiveData.data?.first?.size ?: -2
        assertEquals("getSearchResult", 1, size)
    }

    private suspend fun setupMockingSearchApp(
        packageNameSearchResponse: Response<Search>?,
        authData: AuthData,
        gplayPackageResult: App,
        gplayLivedata: LiveData<Pair<List<FusedApp>, Boolean>>?,
        willThrowException: Boolean = false
    ) {
        Mockito.`when`(pwaManagerModule.getPwaStatus(any())).thenReturn(Status.UNAVAILABLE)
        Mockito.`when`(pkgManagerModule.getPackageStatus(any(), any()))
            .thenReturn(Status.UNAVAILABLE)
        Mockito.`when`(
            cleanApkRepository.searchApps(
                keyword = "com.search.package", by = "package_name"
            )
        ).thenReturn(packageNameSearchResponse)
        formatterMocked.`when`<String> { Formatter.formatFileSize(any(), any()) }.thenReturn("15MB")

        if (willThrowException) {
            Mockito.`when`(gPlayAPIRepository.getAppDetails("com.search.package", authData))
                .thenThrow(RuntimeException())
        } else {
            Mockito.`when`(gPlayAPIRepository.getAppDetails(eq("com.search.package"), eq(authData)))
                .thenReturn(gplayPackageResult)
        }

        Mockito.`when`(cleanApkRepository.searchApps(keyword = "com.search.package"))
            .thenReturn(packageNameSearchResponse)

        Mockito.`when`(
            cleanApkRepository.searchApps(
                keyword = "com.search.package",
                type = CleanAPKInterface.APP_TYPE_PWA,
                source = CleanAPKInterface.APP_SOURCE_ANY
            )
        ).thenReturn(packageNameSearchResponse)

        suspend fun replaceWithFDroid(gPlayApp: App): FusedApp {
            return FusedApp(gPlayApp.id.toString(), gPlayApp.packageName)
        }

        Mockito.`when`(
            gPlayAPIRepository.getSearchResults(
                eq("com.search.package"),
                eq(authData),
                eq(::replaceWithFDroid),
                any()
            )
        )
            .thenReturn(gplayLivedata)
    }

    @Test
    fun `getSearchResult When getApplicationDetailsThrowsException`() = runTest {
        val appList = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.UNAVAILABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                latest_version_code = 123
            ),
            FusedApp(
                _id = "112",
                status = Status.UNAVAILABLE,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                latest_version_code = 123
            ),
            FusedApp(
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

        val gplayLivedata =
            MutableLiveData(
                Pair(
                    listOf(FusedApp("a.b.c"), FusedApp("c.d.e"), FusedApp("d.e.f")),
                    false
                )
            )

        setupMockingSearchApp(
            packageNameSearchResponse, AUTH_DATA, gplayPackageResult, gplayLivedata, true
        )

        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = true
        preferenceManagerModule.isGplaySelectedFake = false

        val searchResultLiveData =
            fusedAPIImpl.getSearchResults("com.search.package", AUTH_DATA).getOrAwaitValue()
        val size = searchResultLiveData.data?.first?.size ?: -2
        assertEquals("getSearchResult", 1, size)
    }
}
