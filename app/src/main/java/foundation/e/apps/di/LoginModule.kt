// Copyright (C) 2019-2022  E FOUNDATION
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.login.CleanApkAuthenticator
import foundation.e.apps.data.login.PlayStoreAuthenticator
import foundation.e.apps.data.login.StoreAuthenticator

@InstallIn(SingletonComponent::class)
@Module
object LoginModule {

    @Provides
    fun providesAuthenticators(
        gPlay: PlayStoreAuthenticator,
        cleanApk: CleanApkAuthenticator,
    ): List<StoreAuthenticator> {
        return listOf(gPlay, cleanApk)
    }
}
