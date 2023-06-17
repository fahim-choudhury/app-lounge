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

package foundation.e.apps.exodus

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.exodus.repositories.AppPrivacyInfoRepositoryImpl
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AppPrivacyInfoRepositoryImplTest {

    // Run tasks synchronously
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var fakeTrackerDao: FakeTrackerDao
    private lateinit var fakeExodusTrackerApi: FakeExoudsTrackerApi
    private lateinit var appPrivacyInfoRepository: AppPrivacyInfoRepositoryImpl

    @Before
    fun setup() {
        fakeExodusTrackerApi = FakeExoudsTrackerApi()
        fakeTrackerDao = FakeTrackerDao()
        appPrivacyInfoRepository =
            AppPrivacyInfoRepositoryImpl(fakeExodusTrackerApi, fakeTrackerDao)
    }

    @Test
    fun getAppPrivacyInfoWhenSuccess() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            is_pwa = true,
        )
        val result = appPrivacyInfoRepository.getAppPrivacyInfo(fusedApp, fusedApp.package_name)
        assertEquals("getAppPrivacyInfo", true, result.isSuccess())
        assertEquals("getAppPrivacyInfo", 3, result.data?.trackerList?.size)
    }

    @Test
    fun getAppPrivacyInfoWhenError() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "",
            latest_version_code = 123,
            is_pwa = true,
        )
        val result = appPrivacyInfoRepository.getAppPrivacyInfo(fusedApp, fusedApp.package_name)
        assertEquals("getAppPrivacyInfo", false, result.isSuccess())
    }

    @Test
    fun getAppPrivacyInfoWhenTrackerDaoIsEmpty() = runTest {
        val fusedApp = FusedApp(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "a.b.c",
            latest_version_code = 123,
            is_pwa = true,
        )
        fakeTrackerDao.trackers.clear()
        val result = appPrivacyInfoRepository.getAppPrivacyInfo(fusedApp, fusedApp.package_name)
        assertEquals("getAppPrivacyInfo", 2, result.data?.trackerList?.size)
    }
}
