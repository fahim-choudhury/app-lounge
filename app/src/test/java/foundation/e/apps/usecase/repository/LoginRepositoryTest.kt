package foundation.e.apps.usecase.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.model.AuthDataResponse
import app.lounge.networking.NetworkFetching
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.domain.login.repository.LoginRepository
import foundation.e.apps.domain.login.repository.LoginRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.util.Properties

@RunWith(RobolectricTestRunner::class)
class LoginRepositoryTest {

    @Mock
    lateinit var networkAPI:  NetworkFetching

    lateinit var instrumentationContext: Context


    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        instrumentationContext= ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun testRequestAuthData() = runTest {
        Mockito.`when`(networkAPI.requestAuthData(
            requestBodyData
        )).thenReturn(AuthData("nisdande@murena.io", ""))

        val sut = LoginRepositoryImpl(
            networkAPI, instrumentationContext)
        val result = sut.anonymousUser(requestBodyData)
        Assert.assertEquals(true, result is AuthDataResponse)
        Assert.assertEquals("nisdande@murena.io", result.email)
    }
}

private val requestBodyData = AnonymousAuthDataRequestBody(
    properties = Properties(),
    userAgent = "testUserAgent"
)