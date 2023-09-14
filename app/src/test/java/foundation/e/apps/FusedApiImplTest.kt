// Copyright (C) 2022  ECORP
// SPDX-FileCopyrightText: 2023 ECORP <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps

import android.content.Context
import android.text.format.Formatter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.Constants
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.SearchBundle
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.search.Search
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fdroid.FdroidWebInterface
import foundation.e.apps.data.fused.FusedApiImpl
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fused.data.FusedHome
import foundation.e.apps.data.fused.utils.CategoryType
import foundation.e.apps.data.gplay.GplayStoreRepository
import foundation.e.apps.install.pkg.PWAManagerModule
import foundation.e.apps.install.pkg.PkgManagerModule
import foundation.e.apps.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class FusedApiImplTest {

    // Run tasks synchronously
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var fusedAPIImpl: FusedApiImpl

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
    private lateinit var gPlayAPIRepository: GplayStoreRepository

    @Mock
    private lateinit var fdroidWebInterface: FdroidWebInterface

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
        fusedAPIImpl = FusedApiImpl(
            pkgManagerModule,
            pwaManagerModule,
            preferenceManagerModule,
            fdroidWebInterface,
            gPlayAPIRepository,
            cleanApkAppsRepository,
            cleanApkPWARepository,
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

            Mockito.`when`(gPlayAPIRepository.getAppDetails(fusedApp.package_name))
                .thenReturn(App(fusedApp.package_name))

            Mockito.`when`(
                gPlayAPIRepository.getDownloadInfo(
                    fusedApp.package_name,
                    fusedApp.latest_version_code,
                    fusedApp.offer_type,
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

        Mockito.`when`(gPlayAPIRepository.getAppDetails(fusedApp.package_name))
            .thenThrow(RuntimeException())

        Mockito.`when`(
            gPlayAPIRepository.getDownloadInfo(
                fusedApp.package_name, fusedApp.latest_version_code, fusedApp.offer_type
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

        Mockito.`when`(gPlayAPIRepository.getAppDetails(fusedApp.package_name))
            .thenReturn(App(fusedApp.package_name))

        Mockito.`when`(
            gPlayAPIRepository.getDownloadInfo(
                fusedApp.package_name, fusedApp.latest_version_code, fusedApp.offer_type
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
            cleanApkPWARepository.getCategories()
        ).thenReturn(response)

        Mockito.`when`(context.getString(eq(R.string.pwa))).thenReturn("PWA")

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(CategoryType.APPLICATION)

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
            cleanApkAppsRepository.getCategories()
        ).thenReturn(response)
        Mockito.`when`(context.getString(eq(R.string.open_source))).thenReturn("Open source")

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(CategoryType.APPLICATION)

        assertEquals("getCategory", 3, categoryListResponse.first.size)
    }

    @Test
    fun `getCategory when gplay source is selected`() = runTest {
        val categories = listOf(Category(), Category(), Category(), Category())

        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = true

        Mockito.`when`(
            gPlayAPIRepository.getCategories(CategoryType.APPLICATION)
        ).thenReturn(categories)

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(CategoryType.APPLICATION)

        assertEquals("getCategory", 4, categoryListResponse.first.size)
    }

    @Test
    fun `getCategory when gplay source is selected return error`() = runTest {
        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = true

        Mockito.`when`(
            gPlayAPIRepository.getCategories(CategoryType.APPLICATION)
        ).thenThrow(RuntimeException())

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(CategoryType.APPLICATION)

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
            cleanApkAppsRepository.getCategories()
        ).thenReturn(openSourceResponse)

        Mockito.`when`(
            cleanApkPWARepository.getCategories()
        ).thenReturn(pwaResponse)

        Mockito.`when`(
            gPlayAPIRepository.getCategories(CategoryType.APPLICATION)
        ).thenReturn(gplayCategories)

        Mockito.`when`(context.getString(eq(R.string.open_source))).thenReturn("Open source")
        Mockito.`when`(context.getString(eq(R.string.pwa))).thenReturn("pwa")

        preferenceManagerModule.isPWASelectedFake = true
        preferenceManagerModule.isOpenSourceelectedFake = true
        preferenceManagerModule.isGplaySelectedFake = true

        val categoryListResponse =
            fusedAPIImpl.getCategoriesList(CategoryType.APPLICATION)

        assertEquals("getCategory", 11, categoryListResponse.first.size)
    }

    @Ignore("Dependencies are not mockable")
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

        Mockito.`when`(fdroidWebInterface.getFdroidApp(any()))
            .thenReturn(Response.error(404, "".toResponseBody(null)))

        Mockito.`when`(gPlayAPIRepository.getSearchResult(eq("com.search.package"), null))
            .thenReturn(gplayLivedata)
    }

    @Ignore("Dependencies are not mockable")
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
}
