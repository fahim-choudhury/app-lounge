package foundation.e.apps.utils.parentFragment

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import foundation.e.apps.login.AuthObject
import foundation.e.apps.login.LoginViewModel
import kotlinx.coroutines.launch

/**
 * Parent class of all fragments.
 *
 * Mostly contains UI related code regarding dialogs to display.
 * Does also provide some interaction with [LoginViewModel].
 *
 * https://gitlab.e.foundation/e/backlog/-/issues/5680
 */
abstract class TimeoutFragment2(@LayoutRes layoutId: Int) : Fragment(layoutId) {

    val loginViewModel: LoginViewModel by lazy {
        ViewModelProvider(requireActivity())[LoginViewModel::class.java]
    }

    /**
     * Fragments observe this list to load data.
     * Fragments should not observe [loginViewModel]'s authObjects.
     */
    val authObjects: MutableLiveData<List<AuthObject>?> = MutableLiveData()

    abstract fun loadData(authObjectList: List<AuthObject>)

    abstract fun showLoadingUI()

    abstract fun stopLoadingUI()

    /**
     * Crucial to call this, other wise fragments will never receive any authentications.
     */
    fun setupListening() {
        loginViewModel.authObjects.observe(viewLifecycleOwner) {
            authObjects.postValue(it)
        }
    }

    /**
     * Call this to repopulate authObjects with old data, this can be used to refresh data
     * from inside the observer on [authObjects].
     */
    fun repostAuthObjects() {
        authObjects.postValue(loginViewModel.authObjects.value)
    }
}