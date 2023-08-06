/*
 * Copyright (C) 2019-2022  E FOUNDATION
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

package foundation.e.apps.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.login.LoginSourceCleanApk
import foundation.e.apps.data.login.LoginSourceGPlay
import foundation.e.apps.data.login.LoginSourceInterface
import foundation.e.apps.domain.login.repository.LoginRepositoryImpl
import foundation.e.apps.domain.login.usecase.UserLoginUseCase

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

    @Provides
    fun provideLoginUserCase(
        loginRepositoryImpl: LoginRepositoryImpl
    ): UserLoginUseCase {
        return UserLoginUseCase(loginRepositoryImpl)
    }
}
