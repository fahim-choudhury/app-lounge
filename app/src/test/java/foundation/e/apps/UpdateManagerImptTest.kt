/*
 *  Copyright (C) 2022  MURENA SAS
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

import android.content.pm.ApplicationInfo
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.api.faultyApps.FaultyAppRepository
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.updates.manager.UpdatesManagerImpl
import foundation.e.apps.util.MainCoroutineRule
import foundation.e.apps.utils.enums.FilterLevel
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateManagerImptTest {

    // Run tasks synchronously
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var updatesManagerImpl: UpdatesManagerImpl

    @Mock
    private lateinit var pkgManagerModule: PkgManagerModule

    @Mock
    private lateinit var fusedAPIRepository: FusedAPIRepository

    private lateinit var faultyAppRepository: FaultyAppRepository

    val authData = AuthData("e@e.email", "AtadyMsIAtadyM")

    val applicationInfo = mutableListOf<ApplicationInfo>(
        ApplicationInfo().apply { this.packageName = "foundation.e.demoone" },
        ApplicationInfo().apply { this.packageName = "foundation.e.demotwo" },
        ApplicationInfo().apply { this.packageName = "foundation.e.demothree" }
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        faultyAppRepository = FaultyAppRepository(FakeFaultyAppDao())
        updatesManagerImpl =
            UpdatesManagerImpl(pkgManagerModule, fusedAPIRepository, faultyAppRepository)
    }

    @Test
    fun getUpdateWhenUpdateIsAvailable() = runTest {
        val gplayApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.UPDATABLE,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
            FusedApp(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
        )

        val openSourceApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "113",
                status = Status.UPDATABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                origin = Origin.CLEANAPK,
                filterLevel = FilterLevel.NONE
            )
        )

        val appList = gplayApps + openSourceApps
        val openSourceUpdates = Pair(openSourceApps, ResultStatus.OK)
        val gplayUpdates = Pair(gplayApps, ResultStatus.OK)

        setupMockingForFetchingUpdates(
            applicationInfo,
            appList,
            authData,
            openSourceUpdates,
            gplayUpdates
        )

        val updateResult = updatesManagerImpl.getUpdates(authData)
        System.out.println("===> updates: ${updateResult.first.map { it.package_name }}")

        assertEquals("fetchUpdate", 2, updateResult.first.size)
    }

    @Test
    fun getUpdateWhenInstalledPackageListIsEmpty() = runTest {
        val authData = AuthData("e@e.email", "AtadyMsIAtadyM")
        val applicationInfo = mutableListOf<ApplicationInfo>()
        Mockito.`when`(pkgManagerModule.getAllUserApps()).thenReturn(applicationInfo)

        val updateResult = updatesManagerImpl.getUpdates(authData)
        System.out.println("===> updates: ${updateResult.first.map { it.package_name }}")

        assertEquals("fetchUpdate", 0, updateResult.first.size)
    }

    @Test
    fun getUpdateWhenUpdateIsUnavailable() = runTest {
        val gplayApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.INSTALLED,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
            FusedApp(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
        )

        val openSourceApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "113",
                status = Status.INSTALLED,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                origin = Origin.CLEANAPK,
                filterLevel = FilterLevel.NONE
            )
        )

        val appList = gplayApps + openSourceApps
        val openSourceUpdates = Pair(openSourceApps, ResultStatus.OK)
        val gplayUpdates = Pair(gplayApps, ResultStatus.OK)

        setupMockingForFetchingUpdates(
            applicationInfo,
            appList,
            authData,
            openSourceUpdates,
            gplayUpdates
        )

        val updateResult = updatesManagerImpl.getUpdates(authData)
        System.out.println("===> updates: ${updateResult.first.map { it.package_name }}")

        assertEquals("fetchUpdate", 0, updateResult.first.size)
    }

    @Test
    fun getUpdateWhenUpdateHasOnlyForOpenSourceApps() = runTest {
        val gplayApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.INSTALLED,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
            FusedApp(
                _id = "112",
                status = Status.INSTALLED,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
        )

        val openSourceApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "113",
                status = Status.UPDATABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                origin = Origin.CLEANAPK,
                filterLevel = FilterLevel.NONE
            )
        )

        val appList = gplayApps + openSourceApps
        val openSourceUpdates = Pair(openSourceApps, ResultStatus.OK)
        val gplayUpdates = Pair(gplayApps, ResultStatus.OK)

        setupMockingForFetchingUpdates(
            applicationInfo,
            appList,
            authData,
            openSourceUpdates,
            gplayUpdates
        )

        val updateResult = updatesManagerImpl.getUpdates(authData)
        System.out.println("===> updates: ${updateResult.first.map { it.package_name }}")

        assertFalse("fetchupdate", updateResult.first.any { it.origin == Origin.GPLAY })
    }

    @Test
    fun getUpdateWhenUpdateHasOnlyForGplayApps() = runTest {
        val gplayApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.INSTALLED,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
            FusedApp(
                _id = "112",
                status = Status.UPDATABLE,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
        )

        val openSourceApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "113",
                status = Status.INSTALLED,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                origin = Origin.CLEANAPK,
                filterLevel = FilterLevel.NONE
            )
        )

        val appList = gplayApps + openSourceApps
        val openSourceUpdates = Pair(openSourceApps, ResultStatus.OK)
        val gplayUpdates = Pair(gplayApps, ResultStatus.OK)

        setupMockingForFetchingUpdates(
            applicationInfo,
            appList,
            authData,
            openSourceUpdates,
            gplayUpdates
        )

        val updateResult = updatesManagerImpl.getUpdates(authData)
        System.out.println("===> updates: ${updateResult.first.map { it.package_name }}")

        assertFalse("fetchupdate", updateResult.first.any { it.origin == Origin.CLEANAPK })
    }

    @Test
    fun getUpdateWhenFetchingOpenSourceIsFailed() = runTest {
        val gplayApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "111",
                status = Status.INSTALLED,
                name = "Demo One",
                package_name = "foundation.e.demoone",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
            FusedApp(
                _id = "112",
                status = Status.UPDATABLE,
                name = "Demo Two",
                package_name = "foundation.e.demotwo",
                origin = Origin.GPLAY,
                filterLevel = FilterLevel.NONE
            ),
        )

        val openSourceApps = mutableListOf<FusedApp>()

        val appList = gplayApps + openSourceApps
        val openSourceUpdates = Pair(openSourceApps, ResultStatus.TIMEOUT)
        val gplayUpdates = Pair(gplayApps, ResultStatus.OK)

        setupMockingForFetchingUpdates(
            applicationInfo,
            appList,
            authData,
            openSourceUpdates,
            gplayUpdates
        )

        val updateResult = updatesManagerImpl.getUpdates(authData)
        System.out.println("===> updates: ${updateResult.first.map { it.package_name }}")

        assertEquals("fetchupdate", 1, updateResult.first.size)
        assertEquals("fetchupdate", ResultStatus.TIMEOUT, updateResult.second)
    }

    @Test
    fun getUpdateWhenFetchingGplayIsFailed() = runTest {
        val gplayApps = mutableListOf<FusedApp>()

        val openSourceApps = mutableListOf<FusedApp>(
            FusedApp(
                _id = "113",
                status = Status.UPDATABLE,
                name = "Demo Three",
                package_name = "foundation.e.demothree",
                origin = Origin.CLEANAPK,
                filterLevel = FilterLevel.NONE
            )
        )

        val appList = gplayApps + openSourceApps
        val openSourceUpdates = Pair(openSourceApps, ResultStatus.OK)
        val gplayUpdates = Pair(gplayApps, ResultStatus.TIMEOUT)

        setupMockingForFetchingUpdates(
            applicationInfo,
            appList,
            authData,
            openSourceUpdates,
            gplayUpdates
        )

        val updateResult = updatesManagerImpl.getUpdates(authData)
        System.out.println("===> updates: ${updateResult.first.map { it.package_name }}")

        assertEquals("fetchupdate", 1, updateResult.first.size)
        assertEquals("fetchupdate", ResultStatus.TIMEOUT, updateResult.second)
    }

    private suspend fun setupMockingForFetchingUpdates(
        applicationInfo: MutableList<ApplicationInfo>,
        appList: List<FusedApp>,
        authData: AuthData,
        openSourceUpdates: Pair<MutableList<FusedApp>, ResultStatus>,
        gplayUpdates: Pair<MutableList<FusedApp>, ResultStatus>
    ) {
        Mockito.`when`(pkgManagerModule.getAllUserApps()).thenReturn(applicationInfo)
        Mockito.`when`(
            fusedAPIRepository.getApplicationDetails(
                any(),
                eq(authData),
                eq(Origin.CLEANAPK)
            )
        ).thenReturn(openSourceUpdates)

        Mockito.`when`(
            fusedAPIRepository.getApplicationDetails(
                any(),
                eq(authData),
                eq(Origin.GPLAY)
            )
        ).thenReturn(gplayUpdates)
    }
}
