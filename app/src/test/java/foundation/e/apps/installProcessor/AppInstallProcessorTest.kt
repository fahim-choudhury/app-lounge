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

package foundation.e.apps.installProcessor

import android.app.DownloadManager.Query
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.api.DownloadManager
import foundation.e.apps.api.fdroid.FdroidRepository
import foundation.e.apps.manager.database.DatabaseRepository
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.fused.IFusedManager
import foundation.e.apps.manager.workmanager.AppInstallProcessor
import foundation.e.apps.util.MainCoroutineRule
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.modules.DataStoreManager
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

    private lateinit var fakeFusedDownloadDAO: FakeFusedDownloadDAO
    private lateinit var databaseRepository: DatabaseRepository
    private lateinit var fakeFusedManagerRepository: FakeFusedManagerRepository

    @Mock
    private lateinit var fakeFusedManager: IFusedManager

    @Mock
    private lateinit var fakeFdroidRepository: FdroidRepository

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var downloadManager: android.app.DownloadManager

    @Mock
    private lateinit var query: Query

    @Mock
    private lateinit var dataStoreManager: DataStoreManager

    private lateinit var appInstallProcessor: AppInstallProcessor

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        fakeFusedDownloadDAO = FakeFusedDownloadDAO()
        databaseRepository = DatabaseRepository(fakeFusedDownloadDAO)
        fakeFusedManagerRepository =
            FakeFusedManagerRepository(fakeFusedDownloadDAO, fakeFusedManager, fakeFdroidRepository)

        appInstallProcessor = AppInstallProcessor(
            context,
            databaseRepository,
            fakeFusedManagerRepository,
            dataStoreManager
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
    ): FusedDownload {
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
        assertEquals("processInstall", Status.INSTALLATION_ISSUE, finalFusedDownload?.status)
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

    private suspend fun runProcessInstall(fusedDownload: FusedDownload): FusedDownload? {
        appInstallProcessor.processInstall(fusedDownload.id, false)
        return fakeFusedDownloadDAO.getDownloadById(fusedDownload.id)
    }

    private fun createFusedDownload(
        packageName: String? = null,
        downloadUrlList: MutableList<String>? = null
    ) = FusedDownload(
        id = "121",
        status = Status.AWAITING,
        downloadURLList = downloadUrlList ?: mutableListOf("apk1", "apk2"),
        packageName = packageName ?: "com.unit.test"
    )
}
