/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.installProcessor

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.install.AppInstallRepository
import foundation.e.apps.data.install.AppManager
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.parentalcontrol.AppInstallationPermissionState
import foundation.e.apps.data.preference.DataStoreManager
import foundation.e.apps.domain.parentalcontrol.GetAppInstallationPermissionUseCase
import foundation.e.apps.install.AppInstallComponents
import foundation.e.apps.install.notification.StorageNotificationManager
import foundation.e.apps.install.workmanager.AppInstallProcessor
import foundation.e.apps.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class AppInstallProcessorTest {
    // Run tasks synchronously
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var fakeFusedDownloadDAO: FakeAppInstallDAO
    private lateinit var appInstallRepository: AppInstallRepository
    private lateinit var fakeFusedManagerRepository: FakeAppManagerWrapper

    @Mock
    private lateinit var fakeFusedManager: AppManager

    @Mock
    private lateinit var fakeFdroidRepository: FdroidRepository

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var dataStoreManager: DataStoreManager

    @Mock
    private lateinit var applicationRepository: ApplicationRepository

    private lateinit var appInstallProcessor: AppInstallProcessor

    @Mock
    private lateinit var getAppInstallationPermissionUseCase: GetAppInstallationPermissionUseCase

    @Mock
    private lateinit var storageNotificationManager: StorageNotificationManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        fakeFusedDownloadDAO = FakeAppInstallDAO()
        appInstallRepository = AppInstallRepository(fakeFusedDownloadDAO)
        fakeFusedManagerRepository =
            FakeAppManagerWrapper(fakeFusedDownloadDAO, fakeFusedManager, fakeFdroidRepository)
        val appInstallComponents =
            AppInstallComponents(appInstallRepository, fakeFusedManagerRepository)

        appInstallProcessor = AppInstallProcessor(
            context,
            appInstallComponents,
            applicationRepository,
            getAppInstallationPermissionUseCase,
            dataStoreManager,
            storageNotificationManager
        )
    }

    @Test
    fun processInstallTest() = runTest {
        val fusedDownload = initTest()

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertTrue("processInstall", finalFusedDownload == null)
    }

    private suspend fun initTest(
        packageName: String? = null,
        downloadUrlList: MutableList<String>? = null
    ): AppInstall {
        val fusedDownload = createFusedDownload(packageName, downloadUrlList)
        fakeFusedDownloadDAO.addDownload(fusedDownload)
        Mockito.`when`(dataStoreManager.getAuthData()).thenReturn(AuthData("", ""))
        return fusedDownload
    }

    @Test
    fun `processInstallTest when FusedDownload is already failed`() = runTest {
        val fusedDownload = initTest()
        fusedDownload.status = Status.BLOCKED

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertEquals("processInstall", Status.BLOCKED, finalFusedDownload?.status)
    }

    @Test
    fun `processInstallTest when files are downloaded but not installed`() = runTest {
        val fusedDownload = initTest()
        fusedDownload.downloadIdMap = mutableMapOf(Pair(231, true))

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertTrue("processInstall", finalFusedDownload == null)
    }

    @Test
    fun `processInstallTest when packageName is empty and files are downloaded`() = runTest {
        val fusedDownload = initTest(packageName = "")
        fusedDownload.downloadIdMap = mutableMapOf(Pair(231, true))

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertEquals("processInstall", Status.INSTALLATION_ISSUE, finalFusedDownload?.status)
    }

    @Test
    fun `processInstallTest when downloadUrls are not available`() = runTest {
        val fusedDownload = initTest(downloadUrlList = mutableListOf())

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertEquals("processInstall", Status.INSTALLATION_ISSUE, finalFusedDownload?.status)
    }

    @Test
    fun `processInstallTest when exception is occurred`() = runTest {
        val fusedDownload = initTest()
        fakeFusedManagerRepository.forceCrash = true

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertTrue(
            "processInstall",
            finalFusedDownload == null || fusedDownload.status == Status.INSTALLATION_ISSUE
        )
    }

    @Test
    fun `processInstallTest when download is failed`() = runTest {
        val fusedDownload = initTest()
        fakeFusedManagerRepository.willDownloadFail = true

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertEquals("processInstall", Status.INSTALLATION_ISSUE, finalFusedDownload?.status)
    }

    @Test
    fun `processInstallTest when install is failed`() = runTest {
        val fusedDownload = initTest()
        fakeFusedManagerRepository.willInstallFail = true

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertEquals("processInstall", Status.INSTALLATION_ISSUE, finalFusedDownload?.status)
    }

    @Test
    fun `processInstallTest when age limit is satisfied`() = runTest {
        val fusedDownload = initTest()
        Mockito.`when`(getAppInstallationPermissionUseCase.invoke(fusedDownload))
            .thenReturn(AppInstallationPermissionState.Allowed)

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertEquals("processInstall", finalFusedDownload, null)
    }

    @Test
    fun `processInstallTest when age limit is not satisfied`() = runTest {
        val fusedDownload = initTest()
        Mockito.`when`(getAppInstallationPermissionUseCase.invoke(fusedDownload))
            .thenReturn(AppInstallationPermissionState.Denied)

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertEquals("processInstall", finalFusedDownload, null)
    }
    @Test
    fun `processInstallTest when installation denied for data loading error`() = runTest {
        val fusedDownload = initTest()
        Mockito.`when`(getAppInstallationPermissionUseCase.invoke(fusedDownload))
            .thenReturn(AppInstallationPermissionState.DeniedOnDataLoadError)

        val finalFusedDownload = runProcessInstall(fusedDownload)
        assertEquals("processInstall", finalFusedDownload, null)
    }

    private suspend fun runProcessInstall(appInstall: AppInstall): AppInstall? {
        appInstallProcessor.processInstall(appInstall.id, false)
        return fakeFusedDownloadDAO.getDownloadById(appInstall.id)
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
