// Copyright (C) 2022  ECORP
// SPDX-FileCopyrightText: 2023 ECORP <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps

import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.FusedApiImpl
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
    private lateinit var fusedAPIImpl: FusedApiImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        fusedApiRepository = FusedAPIRepository(fusedAPIImpl)
    }

    @Test
    fun isAnyAppUpdated_ReturnsTrue() {
        Mockito.`when`(fusedAPIImpl.isAnyFusedAppUpdated(any(), any())).thenReturn(true)
        val isAnyAppUpdated = fusedApiRepository.isAnyFusedAppUpdated(listOf(), listOf())
        assertTrue("isAnyAppUpdated", isAnyAppUpdated)
    }

    @Test
    fun isAnyInstallStatusChanged_ReturnsTrue() {
        Mockito.`when`(fusedAPIImpl.isAnyAppInstallStatusChanged(any())).thenReturn(true)
        val isAnyAppUpdated = fusedApiRepository.isAnyAppInstallStatusChanged(listOf())
        assertTrue("isAnyAppUpdated", isAnyAppUpdated)
    }
}
