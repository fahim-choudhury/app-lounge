/*
 * Copyright (C) 2022  ECORP
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

package foundation.e.apps.utils.parentFragment

import android.app.Activity
import android.view.KeyEvent
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.R

/*
 * Parent class (extending fragment) for fragments which can display a timeout dialog
 * for network calls exceeding timeout limit.
 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
 */
abstract class TimeoutFragment(@LayoutRes layoutId: Int) : Fragment(layoutId) {

    /*
     * Alert dialog to show to user if App Lounge times out.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    private var timeoutAlertDialog: AlertDialog? = null

    abstract fun onTimeout()

    /*
     * Set this to true when timeout dialog is once shown.
     * Set to false if user clicks "Retry".
     * Use this to prevent repeatedly showing timeout dialog.
     *
     * Setting the value to true is automatically done from displayTimeoutAlertDialog().
     * To set it as false, call resetTimeoutDialogLock().
     *
     * Timeout dialog maybe shown multiple times from MainActivity authData observer,
     * MainActivityViewModel.downloadList observer, or simply from timing out while
     * fetch the information for the fragment.
     */
    private var timeoutDialogShownLock: Boolean = false

    /*
     * Do call this in the "Retry" button block of timeout dialog.
     * Also call this in onResume(), otherwise after screen off, the timeout dialog may not appear.
     */
    fun resetTimeoutDialogLock() {
        timeoutDialogShownLock = false
    }

    /*
     * Recommended to put code to refresh data inside this block.
     * But call refreshDataOrRefreshToken() to execute the refresh.
     */
    abstract fun refreshData(authData: AuthData)

    /*
     * Checks if network connectivity is present.
     * -- If yes, then checks if valid authData is present.
     * ---- If yes, then dismiss timeout dialog (if showing) and call refreshData()
     * ---- If no, then request new token data.
     */
    fun refreshDataOrRefreshToken(mainActivityViewModel: MainActivityViewModel) {
        if (mainActivityViewModel.internetConnection.value == true) {
            mainActivityViewModel.authData.value?.let { authData ->
                dismissTimeoutDialog()
                refreshData(authData)
            } ?: run {
                mainActivityViewModel.retryFetchingTokenAfterTimeout()
            }
        }
    }

    /**
     * Display timeout alert dialog.
     *
     * @param activity Activity class. Basically the MainActivity.
     * @param message Alert dialog body.
     * @param positiveButtonText Positive button text. Example "Retry"
     * @param positiveButtonBlock Code block when [positiveButtonText] is pressed.
     * @param negativeButtonText Negative button text. Example "Retry"
     * @param negativeButtonBlock Code block when [negativeButtonText] is pressed.
     * @param positiveButtonText Positive button text. Example "Retry"
     * @param positiveButtonBlock Code block when [positiveButtonText] is pressed.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    fun displayTimeoutAlertDialog(
        timeoutFragment: TimeoutFragment,
        activity: Activity,
        message: String,
        positiveButtonText: String? = null,
        positiveButtonBlock: (() -> Unit)? = null,
        negativeButtonText: String? = null,
        negativeButtonBlock: (() -> Unit)? = null,
        neutralButtonText: String? = null,
        neutralButtonBlock: (() -> Unit)? = null,
        allowCancel: Boolean = true,
    ) {

        /*
         * If timeout dialog is already shown, don't proceed.
         */
        if (timeoutFragment.timeoutDialogShownLock) {
            return
        }

        val timeoutAlertDialogBuilder = AlertDialog.Builder(activity).apply {

            /*
             * Set title.
             */
            setTitle(R.string.timeout_title)

            if (!allowCancel) {
                /*
                 * Prevent dismissing the dialog from pressing outside as it will only
                 * show a blank screen below the dialog.
                 */
                setCancelable(false)
                /*
                 * If user presses back button to close the dialog without selecting anything,
                 * close App Lounge.
                 */
                setOnKeyListener { dialog, keyCode, _ ->
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        dialog.dismiss()
                        activity.finish()
                    }
                    true
                }
            } else {
                setCancelable(true)
            }

            /*
             * Set message
             */
            setMessage(message)

            /*
             * Set buttons.
             */
            positiveButtonText?.let {
                setPositiveButton(it) { _, _ ->
                    positiveButtonBlock?.invoke()
                }
            }
            negativeButtonText?.let {
                setNegativeButton(it) { _, _ ->
                    negativeButtonBlock?.invoke()
                }
            }
            neutralButtonText?.let {
                setNeutralButton(it) { _, _ ->
                    neutralButtonBlock?.invoke()
                }
            }
        }

        /*
         * Dismiss alert dialog if already being shown
         */
        try {
            timeoutAlertDialog?.dismiss()
        } catch (_: Exception) {}

        timeoutAlertDialog = timeoutAlertDialogBuilder.create()
        timeoutAlertDialog?.show()

        /*
         * Mark timeout dialog is already shown.
         */
        timeoutFragment.timeoutDialogShownLock = true
    }

    /**
     * Returns true if [timeoutAlertDialog] is displaying.
     * Returs false if it is not initialised.
     */
    fun isTimeoutDialogDisplayed(): Boolean {
        return timeoutAlertDialog?.isShowing == true
    }

    /**
     * Dismisses the [timeoutAlertDialog] if it is being displayed.
     * Does nothing if it is not being displayed.
     * Caller need not check if the dialog is being displayed.
     */
    fun dismissTimeoutDialog() {
        if (isTimeoutDialogDisplayed()) {
            try {
                timeoutAlertDialog?.dismiss()
            } catch (_: Exception) {}
        }
    }
}
