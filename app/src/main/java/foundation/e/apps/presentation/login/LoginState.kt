package foundation.e.apps.presentation.login


data class LoginState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String = ""
)
