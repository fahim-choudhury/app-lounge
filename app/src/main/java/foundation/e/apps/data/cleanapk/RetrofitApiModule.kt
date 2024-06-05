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

package foundation.e.apps.data.cleanapk

import com.google.gson.Gson
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.cleanapk.NetworkModule.getYamlFactory
import foundation.e.apps.data.ecloud.EcloudApiInterface
import foundation.e.apps.data.exodus.ExodusTrackerApi
import foundation.e.apps.data.fdroid.FdroidApiInterface
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RetrofitApiModule {
    /**
     * Provides an instance of Retrofit to work with CleanAPK API
     * @return instance of [CleanApkRetrofit]
     */
    @Singleton
    @Provides
    fun provideCleanApkInterface(okHttpClient: OkHttpClient, moshi: Moshi): CleanApkRetrofit {
        return Retrofit.Builder()
            .baseUrl(CleanApkRetrofit.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CleanApkRetrofit::class.java)
    }

    /**
     * Provides an instance of Retrofit to work with CleanAPK API
     * @return instance of [CleanApkAppDetailsRetrofit]
     */
    @Singleton
    @Provides
    fun provideCleanApkDetailApi(
        okHttpClient: OkHttpClient,
        @Named("gsonCustomAdapter") gson: Gson
    ): CleanApkAppDetailsRetrofit {
        return Retrofit.Builder()
            .baseUrl(CleanApkRetrofit.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(CleanApkAppDetailsRetrofit::class.java)
    }

    @Singleton
    @Provides
    fun provideExodusApi(okHttpClient: OkHttpClient, moshi: Moshi): ExodusTrackerApi {
        return Retrofit.Builder()
            .baseUrl(ExodusTrackerApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ExodusTrackerApi::class.java)
    }

    /**
     * The fdroid api returns results in .yaml format.
     * Hence we need a yaml convertor.
     * Convertor is being provided by [getYamlFactory].
     */
    @Singleton
    @Provides
    fun provideFdroidApi(
        okHttpClient: OkHttpClient,
        @Named("yamlFactory") yamlFactory: JacksonConverterFactory
    ): FdroidApiInterface {
        return Retrofit.Builder()
            .baseUrl(FdroidApiInterface.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(yamlFactory)
            .build()
            .create(FdroidApiInterface::class.java)
    }

    @Singleton
    @Provides
    fun provideEcloudApi(okHttpClient: OkHttpClient, moshi: Moshi): EcloudApiInterface {
        return Retrofit.Builder()
            .baseUrl(EcloudApiInterface.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(EcloudApiInterface::class.java)
    }
}
