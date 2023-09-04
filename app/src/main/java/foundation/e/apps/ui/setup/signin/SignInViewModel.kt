package foundation.e.apps.ui.setup.signin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.preference.DataStoreModule
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val dataStoreModule: DataStoreModule
) : ViewModel() {

    val userType: LiveData<String> = dataStoreModule.userType.asLiveData()

    private val _authLiveData: MutableLiveData<AuthData> = MutableLiveData()
    val authLiveData: LiveData<AuthData> = _authLiveData
    fun saveUserType(user: User) {
        viewModelScope.launch {
            dataStoreModule.saveUserType(user)
            if (user == User.UNAVAILABLE) {
                dataStoreModule.destroyCredentials()
            }
        }
    }

    fun saveEmailToken(email: String, token: String) {
        viewModelScope.launch {
            dataStoreModule.saveEmail(email, token)
        }
    }
}
