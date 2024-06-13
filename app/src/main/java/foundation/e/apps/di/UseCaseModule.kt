/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.parentalcontrol.GetParentalControlStateUseCaseImpl
import foundation.e.apps.domain.parentalcontrol.GetParentalControlStateUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface UseCaseModule {

    @Singleton
    @Binds
    fun bindGetParentalControlStateUseCase(
        useCase: GetParentalControlStateUseCaseImpl
    ): GetParentalControlStateUseCase
}
