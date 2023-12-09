// Copyright (C) 2022  ECORP
// SPDX-FileCopyrightText: 2023 ECORP <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.fused

import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.ApplicationApiImpl
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

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        applicationRepository = ApplicationRepository(fusedAPIImpl, homeApi, categoryApi)
    }

    @Test
    fun isAnyAppUpdated_ReturnsTrue() {
        Mockito.`when`(fusedAPIImpl.isAnyFusedAppUpdated(any(), any())).thenReturn(true)
        val isAnyAppUpdated = applicationRepository.isAnyFusedAppUpdated(listOf(), listOf())
        assertTrue("isAnyAppUpdated", isAnyAppUpdated)
    }

    @Test
    fun isAnyInstallStatusChanged_ReturnsTrue() {
        Mockito.`when`(fusedAPIImpl.isAnyAppInstallStatusChanged(any())).thenReturn(true)
        val isAnyAppUpdated = applicationRepository.isAnyAppInstallStatusChanged(listOf())
        assertTrue("isAnyAppUpdated", isAnyAppUpdated)
    }
}
