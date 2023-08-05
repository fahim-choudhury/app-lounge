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

