package app.lounge

import app.lounge.login.google.GoogleLoginApi
import app.lounge.login.google.GoogleLoginApiImpl
import app.lounge.networking.GplayHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import java.util.concurrent.TimeUnit

class GoogleLoginApiTest {

    private val gplayHttpClient = GplayHttpClient(getOkHttpClient())

    private fun getOkHttpClient(
        timeoutInMillisecond: Long = 10000L
    ): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(timeoutInMillisecond, TimeUnit.MILLISECONDS)
        .build()

    private val googleLoginApi: GoogleLoginApi = GoogleLoginApiImpl(gplayHttpClient)

    @Test
    fun testFetchAASTokenSuccess() = runBlocking {
         
    }
}