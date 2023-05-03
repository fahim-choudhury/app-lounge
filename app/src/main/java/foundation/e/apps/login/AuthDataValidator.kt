package foundation.e.apps.login

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.api.ResultSupreme

interface AuthDataValidator {
    suspend fun fetchAuthData(): ResultSupreme<AuthData?>
}