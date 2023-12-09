// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 MURENA SAS <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.exodus

import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.exodus.repositories.PrivacyScoreRepositoryImpl
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.di.CommonUtilsModule
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class PrivacyScoreRepositoryImplTest {

    private lateinit var privacyScoreRepository: PrivacyScoreRepositoryImpl

    @Before
    fun setup() {
        privacyScoreRepository = PrivacyScoreRepositoryImpl()
    }

    @Test
    fun calculatePrivacyScoreWhenNoTrackers() {
        val application = Application(
            _id = "113",
            status = Status.UNAVAILABLE,
            name = "Demo Three",
            package_name = "a.b.c",
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
            package_name = "a.b.c",
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
            package_name = "a.b.c",
            latest_version_code = 123,
            is_pwa = true,
            permsFromExodus = listOf(),
            perms = listOf(),
            trackers = CommonUtilsModule.LIST_OF_NULL
        )
        val privacyScore = privacyScoreRepository.calculatePrivacyScore(application)
        Assert.assertEquals("failed to retrieve valid privacy score", 9, privacyScore)
    }
}
