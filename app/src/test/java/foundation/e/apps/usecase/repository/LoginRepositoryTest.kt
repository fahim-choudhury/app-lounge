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
import foundation.e.apps.loginFailureMessage
import foundation.e.apps.testFailureException

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
    fun testOnSuccessReturnAuthData() = runTest {
        Mockito.`when`(anonymousUser.requestAuthData(testAnonymousRequestBodyData))
            .thenReturn(NetworkResult.Success(testAnonymousResponseData))

        val result = LoginRepositoryImpl(anonymousUser, instrumentationContext)
            .run {
                anonymousUser(testAnonymousRequestBodyData)
            }

        Assert.assertNotNull(result)
        Assert.assertEquals("eOS@murena.io", result.email)
    }

    @Test
    fun testOnFailureReturnErrorWithException() = runTest {
        Mockito.`when`(anonymousUser.requestAuthData(testAnonymousRequestBodyData))
            .thenReturn(NetworkResult.Error(
                exception = testFailureException,
                code = 1,
                errorMessage = loginFailureMessage
            ))
        runCatching {
            LoginRepositoryImpl(anonymousUser, instrumentationContext)
                .run { anonymousUser(testAnonymousRequestBodyData) }
        }.onFailure { error ->
            Assert.assertEquals(testFailureException.message, error.message)
        }
    }
}
