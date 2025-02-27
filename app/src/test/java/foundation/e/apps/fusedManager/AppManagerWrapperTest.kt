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

package foundation.e.apps.fusedManager

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.install.AppManagerWrapper
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.install.workmanager.InstallWorkManager
import foundation.e.apps.installProcessor.FakeAppInstallDAO
import foundation.e.apps.util.MainCoroutineRule
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class AppManagerWrapperTest {
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var appInstallDAO: FakeAppInstallDAO
    private lateinit var fakeAppManager: FakeAppManager

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var fdroidRepository: FdroidRepository

    private lateinit var appManagerWrapper: AppManagerWrapper

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        InstallWorkManager.context = application
        appInstallDAO = FakeAppInstallDAO()
        fakeAppManager = FakeAppManager(appInstallDAO)
        appManagerWrapper = AppManagerWrapper(fakeAppManager, fdroidRepository)
    }

    @Test
    fun addDownload() = runTest {
        val fusedDownload = initTest()

        val isSuccessful = appManagerWrapper.addDownload(fusedDownload)
        assertTrue("addDownload", isSuccessful)
        assertEquals("addDownload", 1, appInstallDAO.getDownloadList().size)
    }

    private fun initTest(hasAnyExistingWork: Boolean = false): AppInstall {
        mockkObject(InstallWorkManager)
        every { InstallWorkManager.checkWorkIsAlreadyAvailable(any()) } returns hasAnyExistingWork
        return createFusedDownload()
    }

    @Test
    fun `addDownload when work and FusedDownload Both are available`() = runTest {
        val fusedDownload = initTest(true)
        appInstallDAO.appInstallList.add(fusedDownload)

        val isSuccessful = appManagerWrapper.addDownload(fusedDownload)
        assertFalse("addDownload", isSuccessful)
    }

    @Test
    fun `addDownload when only work exists`() = runTest {
        val fusedDownload = initTest(true)

        val isSuccessful = appManagerWrapper.addDownload(fusedDownload)
        assertTrue("addDownload", isSuccessful)
    }

    @Test
    fun `addDownload when on FusedDownload exists`() = runTest {
        val fusedDownload = initTest()
        appInstallDAO.addDownload(fusedDownload)

        val isSuccessful = appManagerWrapper.addDownload(fusedDownload)
        assertFalse("addDownload", isSuccessful)
    }

    @Test
    fun `addDownload when fusedDownload already exists And has installation issue`() = runTest {
        val fusedDownload = initTest()
        fusedDownload.status = Status.INSTALLATION_ISSUE
        appInstallDAO.addDownload(fusedDownload)

        val isSuccessful = appManagerWrapper.addDownload(fusedDownload)
        assertTrue("addDownload", isSuccessful)
    }

    private fun createFusedDownload(
        packageName: String? = null,
        downloadUrlList: MutableList<String>? = null
    ) = AppInstall(
        id = "121",
        status = Status.AWAITING,
        downloadURLList = downloadUrlList ?: mutableListOf("apk1", "apk2"),
        packageName = packageName ?: "com.unit.test"
    )
}
