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

package foundation.e.apps.di.network

import com.google.gson.Gson
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.cleanapk.CleanApkAppDetailsRetrofit
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.ecloud.EcloudApiInterface
import foundation.e.apps.data.exodus.ExodusTrackerApi
import foundation.e.apps.data.fdroid.FdroidApiInterface
import foundation.e.apps.data.gitlab.UpdatableSystemAppsApi
import foundation.e.apps.data.gitlab.GitlabReleaseApi
import foundation.e.apps.data.parentalcontrol.fdroid.FDroidMonitorApi
import foundation.e.apps.data.parentalcontrol.googleplay.AgeGroupApi
import foundation.e.apps.di.network.NetworkModule.getYamlFactory
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
    @Singleton
    @Provides
    fun provideCleanApkApi(okHttpClient: OkHttpClient, moshi: Moshi): CleanApkRetrofit {
        return Retrofit.Builder()
            .baseUrl(CleanApkRetrofit.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CleanApkRetrofit::class.java)
    }

    @Singleton
    @Provides
    fun provideCleanApkAppDetailsApi(
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

    @Singleton
    @Provides
    fun provideUpdatableSystemAppsApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): UpdatableSystemAppsApi {
        return Retrofit.Builder()
            .baseUrl(UpdatableSystemAppsApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(UpdatableSystemAppsApi::class.java)
    }

    @Singleton
    @Provides
    fun provideSystemAppDefinitionApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): GitlabReleaseApi {
        return Retrofit.Builder()
            .baseUrl(GitlabReleaseApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitlabReleaseApi::class.java)
    }

}
