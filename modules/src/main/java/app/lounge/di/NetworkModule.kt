package app.lounge.di

import app.lounge.networking.NetworkFetching
import app.lounge.networking.NetworkFetchingRetrofitAPI
import app.lounge.networking.NetworkFetchingRetrofitImpl
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
    ) : Retrofit {
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
            baseUrl = NetworkFetchingRetrofitAPI.tokenBaseURL
        )
    }

    @Provides
    @Singleton
    @Named("GoogleRetrofit")
    internal fun provideGoogleRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit {
        return retrofit(
            okHttpClient = okHttpClient,
            baseUrl = NetworkFetchingRetrofitAPI.googlePlayBaseURL
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
    internal fun providesHttpLogger() : HttpLoggingInterceptor {
        return run {
            val httpLoggingInterceptor = HttpLoggingInterceptor()
            httpLoggingInterceptor.apply {
                httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            }
        }
    }

    @Provides
    @Singleton
    fun provideNetworkFetching(
        @Named("ECloudRetrofit") ecloud: Retrofit,
        @Named("GoogleRetrofit") google: Retrofit,
    ) : NetworkFetching {
        return NetworkFetchingRetrofitImpl(
            eCloud = ecloud,
            google = google
        )
    }

}