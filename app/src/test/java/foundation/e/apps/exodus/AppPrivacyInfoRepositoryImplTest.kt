// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 MURENA SAS <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.exodus

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.exodus.repositories.AppPrivacyInfoRepositoryImpl
import foundation.e.apps.data.application.data.Application
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
        val application = Application(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "foundation.e.demothree",
            latest_version_code = 123,
            is_pwa = true,
        )
        val result = appPrivacyInfoRepository.getAppPrivacyInfo(application, application.package_name)
        assertEquals("getAppPrivacyInfo", true, result.isSuccess())
        assertEquals("getAppPrivacyInfo", 3, result.data?.trackerList?.size)
    }

    @Test
    fun getAppPrivacyInfoWhenError() = runTest {
        val application = Application(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "",
            latest_version_code = 123,
            is_pwa = true,
        )
        val result = appPrivacyInfoRepository.getAppPrivacyInfo(application, application.package_name)
        assertEquals("getAppPrivacyInfo", false, result.isSuccess())
    }

    @Test
    fun getAppPrivacyInfoWhenTrackerDaoIsEmpty() = runTest {
        val application = Application(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "a.b.c",
            latest_version_code = 123,
            is_pwa = true,
        )
        fakeTrackerDao.trackers.clear()
        val result = appPrivacyInfoRepository.getAppPrivacyInfo(application, application.package_name)
        assertEquals("getAppPrivacyInfo", 2, result.data?.trackerList?.size)
    }
}
