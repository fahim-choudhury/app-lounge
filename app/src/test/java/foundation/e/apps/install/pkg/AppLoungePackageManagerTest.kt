/*
 * Copyright MURENA SAS 2024
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

package foundation.e.apps.install.pkg

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import foundation.e.apps.data.enums.Status
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

class AppLoungePackageManagerTest {

    private lateinit var appLoungePackageManager: AppLoungePackageManager

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var packageManager: PackageManager

    private val testPackageName = "foundation.e.test"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Mockito.`when`(context.packageManager).thenReturn(packageManager)
        appLoungePackageManager = AppLoungePackageManager(context)
    }

    private fun mockPackagePresence(
        expectedPackageName: String,
        expectedVersionCode: Int,
        expectedVersionName: String,
        optionalFlag: Int = 0,
    ) {
        val expectedPackageInfo = mock(PackageInfo::class.java).apply {
            packageName = expectedPackageName
            versionName = expectedVersionName
            versionCode = expectedVersionCode
        }
        Mockito.`when`(
            packageManager.getPackageInfo(expectedPackageName, optionalFlag)
        ).thenReturn(expectedPackageInfo)
    }

    private fun mockPackagePresence() {
        mockPackagePresence(testPackageName, 0, "")
    }

    private fun mockPackageAbsence() {
        Mockito.`when`(packageManager.getPackageInfo(testPackageName, PackageManager.GET_META_DATA))
            .thenThrow(PackageManager.NameNotFoundException::class.java)
    }

    @Test
    fun givenPackageInfoIsPresent_whenCheckIsInstalled_thenReturnTrue() {
        mockPackagePresence()
        assert(appLoungePackageManager.isInstalled(testPackageName))
    }

    @Test
    fun givenPackageInfoIsAbsent_whenCheckIsInstalled_thenReturnFalse() {
        mockPackageAbsence()
        assertFalse(appLoungePackageManager.isInstalled(testPackageName))
    }

    @Test
    fun givenPackageInfoIsPresent_thenReturnProperVersionCode() {
        val installedVersionCode = 40903000
        val installedVersionName = "4.9.3"

        mockPackagePresence(
            expectedPackageName = testPackageName,
            expectedVersionCode = installedVersionCode,
            expectedVersionName = installedVersionName,
        )

        assertEquals(
            installedVersionCode.toLong(),
            PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(testPackageName, 0))
        )
    }

    @Test
    fun givenPackageInfoIsAbsent_whenCheckIsUpdatable_thenReturnStatusUNAVAILABLE() {
        mockPackageAbsence()

        val newVersionCode = 40903000
        val newVersionName = "4.9.3+20240725"

        assertEquals(
            Status.UNAVAILABLE, appLoungePackageManager.getPackageStatus(
                packageName = testPackageName,
                versionCode = newVersionCode,
                versionName = newVersionName,
            )
        )
    }

    @Test
    fun givenNewVersionCode_andSameVersionName_whenCheckIsUpdatable_thenReturnStatusUPDATABLE() {
        val installedVersionCode = 40903000
        val installedVersionName = "4.9.3"

        mockPackagePresence(
            expectedPackageName = testPackageName,
            expectedVersionCode = installedVersionCode,
            expectedVersionName = installedVersionName,
        )

        val newVersionCode = 40903001
        val newVersionName = "4.9.3"

        assertEquals(
            Status.UPDATABLE, appLoungePackageManager.getPackageStatus(
                packageName = testPackageName,
                versionCode = newVersionCode,
                versionName = newVersionName,
            )
        )
    }

    @Test
    fun givenNewVersionCode_andNewVersionNameBlank_whenCheckIsUpdatable_thenReturnStatusUPDATABLE() {
        val installedVersionCode = 40903000
        val installedVersionName = "4.9.3"

        mockPackagePresence(
            expectedPackageName = testPackageName,
            expectedVersionCode = installedVersionCode,
            expectedVersionName = installedVersionName,
        )

        val newVersionCode = 40903001
        val newVersionName = ""

        assertEquals(
            Status.UPDATABLE, appLoungePackageManager.getPackageStatus(
                packageName = testPackageName,
                versionCode = newVersionCode,
                versionName = newVersionName,
            )
        )
    }

    @Test
    fun givenNewVersionName_andSameVersionCode_whenCheckIsUpdatable_thenReturnStatusUPDATABLE() {
        val installedVersionCode = 40903000
        val installedVersionName = "4.9.3"

        mockPackagePresence(
            expectedPackageName = testPackageName,
            expectedVersionCode = installedVersionCode,
            expectedVersionName = installedVersionName,
        )

        val newVersionCode = 40903000
        val newVersionName = "4.9.3+20240725"

        assertEquals(
            Status.UPDATABLE, appLoungePackageManager.getPackageStatus(
                packageName = testPackageName,
                versionCode = newVersionCode,
                versionName = newVersionName,
            )
        )
    }

    @Test
    fun givenSameVersionCode_andSameVersionName_whenCheckIsUpdatable_thenReturnStatusINSTALLED() {
        val installedVersionCode = 40903000
        val installedVersionName = "4.9.3+20240725"

        mockPackagePresence(
            expectedPackageName = testPackageName,
            expectedVersionCode = installedVersionCode,
            expectedVersionName = installedVersionName,
        )

        val newVersionCode = 40903000
        val newVersionName = "4.9.3+20240725"

        assertEquals(
            Status.INSTALLED, appLoungePackageManager.getPackageStatus(
                packageName = testPackageName,
                versionCode = newVersionCode,
                versionName = newVersionName,
            )
        )
    }

    @Test
    fun givenNewVersionName_whenCheckIsUpdatable_thenReturnStatusUPDATABLE() {
        val installedVersionCode = 40903000
        val installedVersionName = "4.9.3+20240725"

        mockPackagePresence(
            expectedPackageName = testPackageName,
            expectedVersionCode = installedVersionCode,
            expectedVersionName = installedVersionName,
        )

        var newVersionCode = 40903000
        var newVersionName = "4.9.3+20240726"

        assertEquals(
            Status.UPDATABLE, appLoungePackageManager.getPackageStatus(
                packageName = testPackageName,
                versionCode = newVersionCode,
                versionName = newVersionName,
            )
        )

        newVersionCode = 40903000
        newVersionName = "4.9.3+20240825"

        assertEquals(
            Status.UPDATABLE, appLoungePackageManager.getPackageStatus(
                packageName = testPackageName,
                versionCode = newVersionCode,
                versionName = newVersionName,
            )
        )

        newVersionCode = 40903000
        newVersionName = "4.9.3+20250725"

        assertEquals(
            Status.UPDATABLE, appLoungePackageManager.getPackageStatus(
                packageName = testPackageName,
                versionCode = newVersionCode,
                versionName = newVersionName,
            )
        )
    }

}
