package app.lounge.networking

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Implement retrofit configuration
 * 1. Try to use single instance of configuration
 * 2. Generic way to handle success or failure
 * 3. API parsing can be clean and testable
 *
 * NOTE: Try to use naming which define the action for the logic.
 * */

internal fun Retrofit.Builder.appLounge(
    baseURL: String,
    shouldFollowRedirects: Boolean,
    callTimeoutInSeconds: Long,
) : Retrofit {
    return this.baseUrl(baseURL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .addNetworkInterceptor(interceptor)
                .callTimeout(callTimeoutInSeconds, TimeUnit.SECONDS)
                .followRedirects(shouldFollowRedirects)
                .build()
        )
        .build()
}

private val interceptor = run {
    val httpLoggingInterceptor = HttpLoggingInterceptor()
    httpLoggingInterceptor.apply {
        httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
    }
}