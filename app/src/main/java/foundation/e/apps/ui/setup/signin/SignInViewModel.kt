package foundation.e.apps.ui.setup.signin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.preference.AppLoungeDataStore
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val appLoungeDataStore: AppLoungeDataStore,
) : ViewModel() {

    val userType: LiveData<String> = appLoungeDataStore.userType.asLiveData()

    private val _authLiveData: MutableLiveData<AuthData> = MutableLiveData()
    val authLiveData: LiveData<AuthData> = _authLiveData
}
