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

import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.exodus.repositories.PrivacyScoreRepositoryImpl
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.blockedApps.BlockedAppRepository
import foundation.e.apps.di.CommonUtilsModule
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

class PrivacyScoreRepositoryImplTest {

    private lateinit var privacyScoreRepository: PrivacyScoreRepositoryImpl

    @Mock
    private lateinit var blockedAppRepository: BlockedAppRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        privacyScoreRepository = PrivacyScoreRepositoryImpl(blockedAppRepository)
    }

    @Test
    fun calculatePrivacyScoreWhenNoTrackers() {
        val application = Application(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "com.test.fakePackage",
            latest_version_code = 123,
            is_pwa = true,
            permsFromExodus = listOf(),
            perms = listOf(),
            trackers = listOf()
        )
        val privacyScore = privacyScoreRepository.calculatePrivacyScore(application)
        Assert.assertEquals("failed to retrieve valid privacy score", 10, privacyScore)
    }

    @Test
    fun calculatePrivacyScoreWhenPermsAreNotAvailable() {
        val application = Application(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "com.test.fakePackage",
            latest_version_code = 123,
            is_pwa = true,
            perms = listOf(),
            trackers = listOf()
        )
        val privacyScore = privacyScoreRepository.calculatePrivacyScore(application)
        Assert.assertEquals("failed to retrieve valid privacy score", -1, privacyScore)
    }

    @Test
    fun calculatePrivacyScoreWhenTrackersAreNotAvailable() {
        val application = Application(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "com.test.fakePackage",
            latest_version_code = 123,
            is_pwa = true,
            permsFromExodus = listOf(),
            perms = listOf(),
            trackers = CommonUtilsModule.LIST_OF_NULL
        )
        val privacyScore = privacyScoreRepository.calculatePrivacyScore(application)
        Assert.assertEquals("failed to retrieve valid privacy score", 9, privacyScore)
    }

    @Test
    fun `test calculate privacyScore for the app are available in privacyScoreZero applist`() {
        val application = Application(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "com.test.fakePackage.privacyZero",
            latest_version_code = 123,
            is_pwa = true,
            permsFromExodus = listOf(),
            perms = listOf(),
            trackers = CommonUtilsModule.LIST_OF_NULL
        )
        Mockito.`when`(blockedAppRepository.isPrivacyScoreZero(eq("com.test.fakePackage.privacyZero"))).thenReturn(true)

        val privacyScore = privacyScoreRepository.calculatePrivacyScore(application)
        Assert.assertEquals("privacy score zero", 0, privacyScore)
    }
}
