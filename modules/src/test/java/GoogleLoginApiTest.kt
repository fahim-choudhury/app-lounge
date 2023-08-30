import app.lounge.login.google.GoogleLoginApi
import app.lounge.login.google.GoogleLoginApiImpl
import app.lounge.networking.GplayHttpClient
import app.lounge.networking.NetworkResult
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.AuthValidator
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit

class GoogleLoginApiTest {

    private val gplayHttpClient = FakeGplayHttpClient()

    private var googleLoginApi: GoogleLoginApi = GoogleLoginApiImpl(gplayHttpClient)

    @Test
    fun testFetchAASTokenSuccess() = runBlocking {
        val result = googleLoginApi.fetchAASToken("aa@aa.com", "123432w3")
         assert(result is NetworkResult.Success)
    }

    @Test
    fun testFetchAASTokenError() = runBlocking {
        gplayHttpClient.shouldThrowException = true
        val result = googleLoginApi.fetchAASToken("aa@aa.com", "123432w3")
        assert(result is NetworkResult.Error)
    }
}