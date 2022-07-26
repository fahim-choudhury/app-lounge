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
import foundation.e.apps.api.cleanapk.CleanAPKRepository
import foundation.e.apps.api.fused.FusedAPIImpl
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.gplay.GPlayAPIRepository
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.modules.PWAManagerModule
import foundation.e.apps.utils.modules.PreferenceManagerModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq

class FusedApiImplTest {
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
    private lateinit var preferenceManagerModule: PreferenceManagerModule

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        fusedAPIImpl = FusedAPIImpl(
            cleanApkRepository,
            gPlayAPIRepository,
            pkgManagerModule,
            pwaManagerModule,
            preferenceManagerModule,
            context
        )
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
}
