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


package foundation.e.apps.presentation.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import foundation.e.apps.testAnonymousRequestBodyData
import foundation.e.apps.data.login.LoginSourceRepository
import foundation.e.apps.domain.login.usecase.UserLoginUseCase
import foundation.e.apps.loginFailureMessage
import foundation.e.apps.testAnonymousResponseData
import foundation.e.apps.util.getOrAwaitValue
import foundation.e.apps.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations


@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    lateinit var mockUserLoginUseCase: UserLoginUseCase

    @Mock
    lateinit var loginRepository: LoginSourceRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun testOnSuccessReturnLogInStateTrue() = runTest{
        Mockito.`when`(mockUserLoginUseCase.invoke(
            properties = testAnonymousRequestBodyData.properties,
            userAgent = testAnonymousRequestBodyData.userAgent
        )).thenReturn(Resource.Success(testAnonymousResponseData))

        val loginViewModel = LoginViewModel(loginRepository, mockUserLoginUseCase)
        loginViewModel.authenticateAnonymousUser(
            properties = testAnonymousRequestBodyData.properties,
            userAgent = testAnonymousRequestBodyData.userAgent
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val result = loginViewModel.loginState.getOrAwaitValue()
        Assert.assertEquals(true, result.isLoggedIn)
        Assert.assertEquals(false, result.isLoading)
    }

    @Test
    fun testOnFailureReturnLogInStateFalseWithError() = runTest{
        Mockito.`when`(mockUserLoginUseCase.invoke(
            properties = testAnonymousRequestBodyData.properties,
            userAgent = testAnonymousRequestBodyData.userAgent
        )).thenReturn(Resource.Error(loginFailureMessage))

        val loginViewModel = LoginViewModel(loginRepository, mockUserLoginUseCase)
        loginViewModel.authenticateAnonymousUser(
            properties = testAnonymousRequestBodyData.properties,
            userAgent = testAnonymousRequestBodyData.userAgent
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val result = loginViewModel.loginState.getOrAwaitValue()
        Assert.assertEquals(false, result.isLoggedIn)
        Assert.assertEquals(false, result.isLoading)
        Assert.assertEquals(loginFailureMessage, result.error)
    }

    @Test
    fun testOnLoadingReturnLogInStateFalse() = runTest{
        Mockito.`when`(mockUserLoginUseCase.invoke(
            properties = testAnonymousRequestBodyData.properties,
            userAgent = testAnonymousRequestBodyData.userAgent
        )).thenReturn(Resource.Loading())

        val loginViewModel = LoginViewModel(loginRepository, mockUserLoginUseCase)
        loginViewModel.authenticateAnonymousUser(
            properties = testAnonymousRequestBodyData.properties,
            userAgent = testAnonymousRequestBodyData.userAgent
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val result = loginViewModel.loginState.getOrAwaitValue()
        Assert.assertEquals(true, result.isLoading)
        Assert.assertEquals(false, result.isLoggedIn)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

}

