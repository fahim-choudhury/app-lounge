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

package foundation.e.apps.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.ui.LoginViewModel
import okhttp3.Cache
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class LoginViewModelTest {

    @Mock
    private lateinit var authenticatorRepository: AuthenticatorRepository
    @Mock
    private lateinit var cache: Cache

    private lateinit var loginViewModel: LoginViewModel

    @Suppress("unused")
    @get:Rule
    val instantTaskExecutorRule: InstantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        loginViewModel = LoginViewModel(authenticatorRepository, cache)
    }

    @Test
    fun testMarkInvalidAuthObject() {
        val authObjectList = mutableListOf<AuthObject>(
            AuthObject.GPlayAuth(
                ResultSupreme.Success(AuthData("aa@aa.com", "feri4234")), User.GOOGLE
            )
        )
        loginViewModel.authObjects.value = authObjectList

        loginViewModel.markInvalidAuthObject(AuthObject.GPlayAuth::class.java.simpleName)
        val currentAuthObjectList = loginViewModel.authObjects.value as List<AuthObject>
        val invalidGplayAuth = currentAuthObjectList.find { it is AuthObject.GPlayAuth }

        assert(invalidGplayAuth != null)
        assert((invalidGplayAuth as AuthObject.GPlayAuth).result.isUnknownError())
    }
}
