/*
 *  Copyright MURENA SAS 2024
 *  Apps  Quickly and easily install Android apps onto your device!
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.di

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.ageRating.AgeGroupApi
import foundation.e.apps.data.ageRating.FDroidMonitorApi
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AgeRatingModule {

    @Singleton
    @Provides
    fun provideAgeGroupApi(okHttpClient: OkHttpClient, moshi: Moshi): AgeGroupApi {
        return Retrofit.Builder()
            .baseUrl(AgeGroupApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AgeGroupApi::class.java)
    }

    @Singleton
    @Provides
    fun provideFDroidMonitorApi(okHttpClient: OkHttpClient, moshi: Moshi): FDroidMonitorApi {
        return Retrofit.Builder()
            .baseUrl(FDroidMonitorApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FDroidMonitorApi::class.java)
    }
}
