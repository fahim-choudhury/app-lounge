/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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


package app.lounge.di

import app.lounge.gplay.GplayHttpClient
import app.lounge.login.anonymous.AnonymousUser
import app.lounge.login.anonymous.AnonymousUserRetrofitAPI
import app.lounge.login.anonymous.AnonymousUserRetrofitImpl
import app.lounge.login.google.AuthTokenFetchApi
import app.lounge.login.google.GoogleLoginApi
import app.lounge.login.google.GoogleLoginApiImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    private const val HTTP_TIMEOUT_IN_SECOND = 10L

    private fun retrofit(
        okHttpClient: OkHttpClient,
        baseUrl: String
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    @Named("ECloudRetrofit")
    internal fun provideECloudRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit {
        return retrofit(
            okHttpClient = okHttpClient,
            baseUrl = AnonymousUserRetrofitAPI.tokenBaseURL
        )
    }

    @Provides
    @Singleton
    @Named("privateOkHttpClient")
    internal fun providesOkHttpClient(
        httpLogger: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addNetworkInterceptor(httpLogger)
            .callTimeout(HTTP_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    internal fun providesHttpLogger(): HttpLoggingInterceptor {
        return run {
            val httpLoggingInterceptor = HttpLoggingInterceptor()
            httpLoggingInterceptor.apply {
                httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            }
        }
    }

    @Provides
    @Singleton
    fun provideGplayHttpClient(@Named("privateOkHttpClient") okHttpClient: OkHttpClient): GplayHttpClient {
        return GplayHttpClient(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideAnonymousUser(
        @Named("ECloudRetrofit") ecloud: Retrofit
    ): AnonymousUser {
        return AnonymousUserRetrofitImpl(
            eCloud = ecloud
        )
    }

    @Provides
    @Singleton
    fun provideAuthTokenFetchApi(gplayHttpClient: GplayHttpClient): AuthTokenFetchApi {
        return AuthTokenFetchApi(gplayHttpClient)
    }

    @Provides
    @Singleton
    fun provideGoogleLoginApi(
        gplayHttpClient: GplayHttpClient,
        authTokenFetchApi: AuthTokenFetchApi
    ): GoogleLoginApi {
        return GoogleLoginApiImpl(gplayHttpClient, authTokenFetchApi)
    }

}