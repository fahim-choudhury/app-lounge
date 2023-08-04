package foundation.e.apps.usecase.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

import foundation.e.apps.domain.login.repository.LoginRepositoryImpl
import foundation.e.apps.testAnonymousRequestBodyData
import foundation.e.apps.testAnonymousResponseData

import app.lounge.login.anonymous.AnonymousUser
import app.lounge.networking.NetworkResult

@RunWith(RobolectricTestRunner::class)
class LoginRepositoryTest {

    @Mock
    lateinit var anonymousUser:  AnonymousUser

    private lateinit var instrumentationContext: Context


    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        instrumentationContext= ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun testRequestAuthData() = runTest {
        Mockito.`when`(anonymousUser.requestAuthData(testAnonymousRequestBodyData))
            .thenReturn(NetworkResult.Success(testAnonymousResponseData))

        val loginRepository = LoginRepositoryImpl(anonymousUser, instrumentationContext)
        val result = loginRepository.anonymousUser(testAnonymousRequestBodyData)
        Assert.assertNotNull(result)
        Assert.assertEquals("eOS@murena.io", result.email)
    }
}
