// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 MURENA SAS <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.fusedManager

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.install.workmanager.InstallWorkManager
import foundation.e.apps.installProcessor.FakeFusedDownloadDAO
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
class FusedManagerRepositoryTest {
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var fusedDownloadDAO: FakeFusedDownloadDAO
    private lateinit var fakeFusedManager: FakeFusedManager

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var fdroidRepository: FdroidRepository

    private lateinit var fusedManagerRepository: FusedManagerRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        InstallWorkManager.context = application
        fusedDownloadDAO = FakeFusedDownloadDAO()
        fakeFusedManager = FakeFusedManager(fusedDownloadDAO)
        fusedManagerRepository = FusedManagerRepository(fakeFusedManager, fdroidRepository)
    }

    @Test
    fun addDownload() = runTest {
        val fusedDownload = initTest()

        val isSuccessful = fusedManagerRepository.addDownload(fusedDownload)
        assertTrue("addDownload", isSuccessful)
        assertEquals("addDownload", 1, fusedDownloadDAO.getDownloadList().size)
    }

    private fun initTest(hasAnyExistingWork: Boolean = false): FusedDownload {
        mockkObject(InstallWorkManager)
        every { InstallWorkManager.checkWorkIsAlreadyAvailable(any()) } returns hasAnyExistingWork
        return createFusedDownload()
    }

    @Test
    fun `addDownload when work and FusedDownload Both are available`() = runTest {
        val fusedDownload = initTest(true)
        fusedDownloadDAO.fusedDownloadList.add(fusedDownload)

        val isSuccessful = fusedManagerRepository.addDownload(fusedDownload)
        assertFalse("addDownload", isSuccessful)
    }

    @Test
    fun `addDownload when only work exists`() = runTest {
        val fusedDownload = initTest(true)

        val isSuccessful = fusedManagerRepository.addDownload(fusedDownload)
        assertTrue("addDownload", isSuccessful)
    }

    @Test
    fun `addDownload when on FusedDownload exists`() = runTest {
        val fusedDownload = initTest()
        fusedDownloadDAO.addDownload(fusedDownload)

        val isSuccessful = fusedManagerRepository.addDownload(fusedDownload)
        assertFalse("addDownload", isSuccessful)
    }

    @Test
    fun `addDownload when fusedDownload already exists And has installation issue`() = runTest {
        val fusedDownload = initTest()
        fusedDownload.status = Status.INSTALLATION_ISSUE
        fusedDownloadDAO.addDownload(fusedDownload)

        val isSuccessful = fusedManagerRepository.addDownload(fusedDownload)
        assertTrue("addDownload", isSuccessful)
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
