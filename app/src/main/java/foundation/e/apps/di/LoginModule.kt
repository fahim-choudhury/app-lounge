// Copyright (C) 2019-2022  E FOUNDATION
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.login.LoginSourceCleanApk
import foundation.e.apps.data.login.LoginSourceGPlay
import foundation.e.apps.data.login.LoginSourceInterface

@InstallIn(SingletonComponent::class)
@Module
object LoginModule {

    @Provides
    fun providesLoginSources(
        gPlay: LoginSourceGPlay,
        cleanApk: LoginSourceCleanApk,
    ): List<LoginSourceInterface> {
        return listOf(gPlay, cleanApk)
    }
}
