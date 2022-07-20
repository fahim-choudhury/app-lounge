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

import foundation.e.apps.api.fused.FusedAPIImpl
import foundation.e.apps.api.fused.FusedAPIRepository
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any

class FusedApiRepositoryTest {
    private lateinit var fusedApiRepository: FusedAPIRepository
    @Mock
    private lateinit var fusedApiImple: FusedAPIImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        fusedApiRepository = FusedAPIRepository(fusedApiImple)
    }

    @Test
    fun isAnyAppUpdated_ReturnsTrue() {
        Mockito.`when`(fusedApiImple.isAnyFusedAppUpdated(any(), any())).thenReturn(true)
        val isAnyAppUpdated = fusedApiRepository.isAnyFusedAppUpdated(listOf(), listOf())
        assertTrue("isAnyAppUpdated", isAnyAppUpdated)
    }

    @Test
    fun isAnyInstallStatusChanged_ReturnsTrue() {
        Mockito.`when`(fusedApiImple.isAnyAppInstallStatusChanged(any())).thenReturn(true)
        val isAnyAppUpdated = fusedApiRepository.isAnyAppInstallStatusChanged(listOf())
        assertTrue("isAnyAppUpdated", isAnyAppUpdated)
    }
}
