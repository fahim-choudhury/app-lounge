// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.cleanapk.CleanApkAppDetailsRetrofit
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.cleanapk.repositories.CleanApkAppsRepositoryImpl
import foundation.e.apps.data.cleanapk.repositories.CleanApkPWARepository
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.playstore.PlayStoreRepositoryImpl
import foundation.e.apps.data.playstore.utils.GPlayHttpClient
import foundation.e.apps.data.login.AuthenticatorRepository
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NamedRepositoryModule {
    @Singleton
    @Provides
    @Named("gplayRepository")
    fun getGplayRepository(
        @ApplicationContext context: Context,
        gPlayHttpClient: GPlayHttpClient,
        authenticatorRepository: AuthenticatorRepository
    ): PlayStoreRepository {
        return PlayStoreRepositoryImpl(context, gPlayHttpClient, authenticatorRepository)
    }

    @Singleton
    @Provides
    @Named("cleanApkAppsRepository")
    fun getCleanApkAppsRepository(
        cleanAPKRetrofit: CleanApkRetrofit,
        cleanApkAppDetailsRetrofit: CleanApkAppDetailsRetrofit
    ): CleanApkRepository {
        return CleanApkAppsRepositoryImpl(cleanAPKRetrofit, cleanApkAppDetailsRetrofit)
    }

    @Singleton
    @Provides
    @Named("cleanApkPWARepository")
    fun getCleanApkPWARepository(
        cleanAPKRetrofit: CleanApkRetrofit,
        cleanApkAppDetailsRetrofit: CleanApkAppDetailsRetrofit
    ): CleanApkRepository {
        return CleanApkPWARepository(cleanAPKRetrofit, cleanApkAppDetailsRetrofit)
    }
}
