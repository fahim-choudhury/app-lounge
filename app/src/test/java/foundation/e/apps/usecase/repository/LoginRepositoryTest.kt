package foundation.e.apps.usecase.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.lounge.model.AuthDataResponse
import app.lounge.networking.NetworkFetching
import foundation.e.apps.testAnonymousRequestBodyData
import foundation.e.apps.domain.login.repository.LoginRepositoryImpl
import foundation.e.apps.testAnonymousResponseData
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginRepositoryTest {

    @Mock
    lateinit var networkAPI:  NetworkFetching

    private lateinit var instrumentationContext: Context


    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        instrumentationContext= ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun testRequestAuthData() = runTest {
        Mockito.`when`(networkAPI.requestAuthData(testAnonymousRequestBodyData))
            .thenReturn(testAnonymousResponseData)

        val loginRepository = LoginRepositoryImpl(networkAPI, instrumentationContext)
        val result: AuthDataResponse = loginRepository.anonymousUser(testAnonymousRequestBodyData)
        Assert.assertNotNull(result)
        Assert.assertEquals("eOS@murena.io", result.email)
    }
}
