package foundation.e.apps.setup.signin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.gplay.utils.AC2DMTask
import foundation.e.apps.utils.enums.User
import foundation.e.apps.utils.modules.DataStoreModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val dataStoreModule: DataStoreModule,
    private val aC2DMTask: AC2DMTask,
    private val fusedAPIRepository: FusedAPIRepository
) : ViewModel() {

    val userType: LiveData<String> = dataStoreModule.userType.asLiveData()

    private val _authLiveData: MutableLiveData<AuthData> = MutableLiveData()
    val authLiveData: LiveData<AuthData> = _authLiveData
    fun saveUserType(user: User) {
        viewModelScope.launch {
            dataStoreModule.saveUserType(user)
        }
    }

    fun saveEmailToken(email: String, token: String) {
        viewModelScope.launch {
            dataStoreModule.saveEmail(email, token)
        }
    }

    private suspend fun fetchAuthData(email: String, oauthToken: String) {
        var responseMap: Map<String, String>
        withContext(Dispatchers.IO) {
            val response = aC2DMTask.getAC2DMResponse(email, oauthToken)
            responseMap = response
            responseMap["Token"]?.let {
                val value = fusedAPIRepository.fetchAuthData(email, it)
                _authLiveData.postValue(value)
                dataStoreModule.saveCredentials(value)
            }
        }
    }

    companion object {
        var isGoogleLoginRunning = false
    }

    fun fetchAuthData() {
        viewModelScope.launch {
            isGoogleLoginRunning = true
            val email = dataStoreModule.getEmail()
            val oauthToken = dataStoreModule.getAASToken()
            if (email.isNotEmpty() && oauthToken.isNotEmpty()) {
                fetchAuthData(email, oauthToken)
            }
            isGoogleLoginRunning = false
        }
    }
}
