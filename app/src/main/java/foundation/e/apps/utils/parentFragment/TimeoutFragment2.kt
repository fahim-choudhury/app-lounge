package foundation.e.apps.utils.parentFragment

import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import foundation.e.apps.R
import foundation.e.apps.databinding.DialogErrorLogBinding
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
     * Override to contain code to execute in case of timeout.
     * Do not call this function directly, use [showTimeout] for that.
     *
     * @param predefinedDialog An AlertDialog builder, already having some properties,
     * Fragment can change the dialog properties and return as the result.
     * By default:
     * 1. Dialog title set to [R.string.timeout_title]
     * 2. Dialog content set to [R.string.timeout_desc_cleanapk].
     * 3. Dialog can show technical error info on clicking "More Info"
     * 4. Has a positive button "Retry" which calls [LoginViewModel.startLoginFlow].
     * 5. Has a negative button "Close" which just closes the dialog.
     * 6. Dialog is cancellable.
     *
     * @return An alert dialog (created from [predefinedDialog]) to show a timeout dialog,
     * or null to not show anything.
     */
    abstract fun onTimeout(
        exception: Exception,
        predefinedDialog: AlertDialog.Builder,
    ): AlertDialog.Builder?

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

    /**
     * Store the last shown dialog, so that when a new dialog is to be shown,
     * the old dialog can be automatically dismissed.
     */
    private var lastDialog: AlertDialog? = null

    /**
     * Show a dialog, dismiss previously shown dialog in [lastDialog].
     */
    private fun showAndSetDialog(alertDialogBuilder: AlertDialog.Builder) {
        alertDialogBuilder.create().run {
            if (lastDialog?.isShowing == true) {
                lastDialog?.dismiss()
            }
            this.show()
            lastDialog = this
        }
    }


    /**
     * Call to trigger [onTimeout].
     * Can be called from anywhere in the fragment.
     *
     * Calls [onTimeout], which may return a [AlertDialog.Builder]
     * instance if it deems fit. Else it may return null, in which case no timeout dialog
     * is shown to the user.
     */
    fun showTimeout(
        exception: Exception,
    ) {
        val dialogView = DialogErrorLogBinding.inflate(requireActivity().layoutInflater)
        dialogView.apply {
            moreInfo.setOnClickListener {
                logDisplay.isVisible = true
                moreInfo.isVisible = false
            }

            val logToDisplay = exception.message ?: ""

            if (logToDisplay.isNotBlank()) {
                logDisplay.text = logToDisplay
                moreInfo.isVisible = true
            }
        }
        val predefinedDialog = AlertDialog.Builder(requireActivity()).apply {
            setTitle(R.string.timeout_title)
            setMessage(R.string.timeout_desc_cleanapk)
            setView(dialogView.root)
            setPositiveButton(R.string.retry) { _, _ ->
                showLoadingUI()
                loginViewModel.startLoginFlow()
            }
            setNegativeButton(R.string.close, null)
            setCancelable(true)
        }

        onTimeout(
            exception,
            predefinedDialog,
        )?.run {
            stopLoadingUI()
            showAndSetDialog(this)
        }
    }
}