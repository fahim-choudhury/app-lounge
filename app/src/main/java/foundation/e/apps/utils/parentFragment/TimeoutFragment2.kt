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
import foundation.e.apps.utils.enums.User
import foundation.e.apps.utils.exceptions.GPlayValidationException
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
     * Override to contain code to execute in case of other sign in error.
     * This can only happen for GPlay data as cleanapk does not need any login.
     * Do not call this function directly, use [showSignInError] for that.
     *
     * @param predefinedDialog An AlertDialog builder, already having some properties,
     * Fragment can change the dialog properties and return as the result.
     * By default:
     * 1. Dialog title set to [R.string.anonymous_login_failed] or [R.string.sign_in_failed_title]
     * 2. Content set to [R.string.anonymous_login_failed_desc] or [R.string.sign_in_failed_desc]
     * 3. Dialog can show technical error info on clicking "More Info"
     * 4. Has a positive button "Retry" which calls [LoginViewModel.startLoginFlow],
     *    passing the list of failed auth types.
     * 5. Has a negative button "Logout" which logs the user out of App Lounge.
     * 6. Dialog is cancellable.
     *
     * @return An alert dialog (created from [predefinedDialog]) to show a timeout dialog,
     * or null to not show anything.
     */
    abstract fun onSignInError(
        exception: GPlayValidationException,
        predefinedDialog: AlertDialog.Builder,
    ): AlertDialog.Builder?

    /**
     * Override to contain code to execute for error during loading data.
     * Do not call this function directly, use [showDataLoadError] for that.
     *
     * @param predefinedDialog An AlertDialog builder, already having some properties,
     * Fragment can change the dialog properties and return as the result.
     * By default:
     * 1. Dialog title set to [R.string.data_load_error].
     * 2. Dialog content set to [R.string.data_load_error_desc].
     * 3. Dialog can show technical error info on clicking "More Info"
     * 4. Has a positive button "Retry" which calls [loadData].
     * 5. Has a negative button "Close" which just closes the dialog.
     * 6. Dialog is cancellable.
     */
    abstract fun onDataLoadError(
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


    /**
     * Call to trigger [onSignInError].
     * Only works if last loginUiAction was a failure case. Else nothing happens.
     *
     * Calls [onSignInError], which may return a [AlertDialog.Builder]
     * instance if it deems fit. Else it may return null, at which case no error dialog
     * is shown to the user.
     */
    fun showSignInError(exception: GPlayValidationException) {

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
            if (exception.user == User.GOOGLE) {
                setTitle(R.string.sign_in_failed_title)
                setMessage(R.string.sign_in_failed_desc)
            } else {
                setTitle(R.string.anonymous_login_failed)
                setMessage(R.string.anonymous_login_failed_desc)
            }

            setView(dialogView.root)

            setPositiveButton(R.string.retry) { _, _ ->
                showLoadingUI()
                loginViewModel.startLoginFlow(listOf(AuthObject.GPlayAuth::class.java.simpleName))
            }
            setNegativeButton(R.string.logout) { _, _ ->
                loginViewModel.logout()
            }
            setCancelable(true)
        }

        onSignInError(
            exception,
            predefinedDialog,
        )?.run {
            stopLoadingUI()
            showAndSetDialog(this)
        }
    }

    /**
     * Call when there is an error during loading data (not error during authentication.)
     *
     * Calls [onDataLoadError] which may return a [AlertDialog.Builder]
     * instance if it deems fit. Else it may return null, at which case no error dialog
     * is shown to the user.
     */
    fun showDataLoadError(
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
            setTitle(R.string.data_load_error)
            setMessage(R.string.data_load_error_desc)
            setView(dialogView.root)
            setPositiveButton(R.string.retry) { _, _ ->
                showLoadingUI()
                authObjects.value?.let { loadData(it) }
            }
            setNegativeButton(R.string.close, null)
            setCancelable(true)
        }

        onDataLoadError(
            exception,
            predefinedDialog,
        )?.run {
            stopLoadingUI()
            showAndSetDialog(this)
        }
    }
}