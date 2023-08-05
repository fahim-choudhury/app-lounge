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
