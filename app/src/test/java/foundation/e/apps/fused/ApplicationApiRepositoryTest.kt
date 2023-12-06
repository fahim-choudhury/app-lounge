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

package foundation.e.apps.fused

import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.ApplicationApiImpl
import foundation.e.apps.data.application.AppsApi
import foundation.e.apps.data.application.CategoryApi
import foundation.e.apps.data.application.HomeApi
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any

class ApplicationApiRepositoryTest {
    private lateinit var applicationRepository: ApplicationRepository
    @Mock
    private lateinit var fusedAPIImpl: ApplicationApiImpl

    @Mock
    private lateinit var homeApi: HomeApi

    @Mock
    private lateinit var categoryApi: CategoryApi

    @Mock
    private lateinit var appsApi: AppsApi

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        applicationRepository = ApplicationRepository(fusedAPIImpl, homeApi, categoryApi, appsApi)
    }

    @Test
    fun isAnyAppUpdated_ReturnsTrue() {
        Mockito.`when`(appsApi.isAnyFusedAppUpdated(any(), any())).thenReturn(true)
        val isAnyAppUpdated = applicationRepository.isAnyFusedAppUpdated(listOf(), listOf())
        assertTrue("isAnyAppUpdated", isAnyAppUpdated)
    }

    @Test
    fun isAnyInstallStatusChanged_ReturnsTrue() {
        Mockito.`when`(appsApi.isAnyAppInstallStatusChanged(any())).thenReturn(true)
        val isAnyAppUpdated = applicationRepository.isAnyAppInstallStatusChanged(listOf())
        assertTrue("isAnyAppUpdated", isAnyAppUpdated)
    }
}
