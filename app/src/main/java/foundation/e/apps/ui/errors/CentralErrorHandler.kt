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

package foundation.e.apps.ui.errors

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.databinding.DialogErrorLogBinding

class CentralErrorHandler {

    private var lastDialog: AlertDialog? = null

    fun <T> getDialogForDataLoadError(
        context: Activity,
        result: ResultSupreme<T>,
        retryAction: () -> Unit
    ): AlertDialog.Builder? {
        return when (result) {
            is ResultSupreme.Timeout -> {
                getDialogForTimeout(
                    context,
                    result.message.ifBlank { result.exception?.message ?: "Timeout - ${result.exception}" },
                    retryAction
                )
            }
            is ResultSupreme.Error -> {
                getDialogForOtherErrors(
                    context,
                    result.message.ifBlank { result.exception?.message ?: "Error - ${result.exception}" },
                    retryAction
                )
            }
            else -> null
        }
    }

    fun getDialogForUnauthorized(
        context: Activity,
        logToDisplay: String = "",
        user: User,
        retryAction: () -> Unit,
        logoutAction: () -> Unit
    ): AlertDialog.Builder {
        val customDialogView = getDialogCustomView(context, logToDisplay)
        val dialog = AlertDialog.Builder(context).apply {
            if (user == User.GOOGLE) {
                setTitle(R.string.sign_in_failed_title)
                setMessage(R.string.sign_in_failed_desc)
            } else {
                setTitle(R.string.anonymous_login_failed)
                setMessage(R.string.anonymous_login_failed_desc)
            }

            setView(customDialogView)

            setPositiveButton(R.string.retry) { _, _ ->
                retryAction()
            }
            setNegativeButton(R.string.logout) { _, _ ->
                logoutAction()
            }
            setCancelable(true)
        }
        return dialog
    }

    private fun getDialogForTimeout(
        context: Activity,
        logToDisplay: String = "",
        retryAction: () -> Unit
    ): AlertDialog.Builder {
        val customDialogView = getDialogCustomView(context, logToDisplay)
        val dialog = AlertDialog.Builder(context).apply {
            setTitle(R.string.timeout_title)
            setMessage(R.string.timeout_desc_cleanapk)
            setView(customDialogView)
            setPositiveButton(R.string.retry) { _, _ ->
                retryAction()
            }
            setNegativeButton(R.string.close, null)
            setCancelable(true)
        }
        return dialog
    }

    private fun getDialogForOtherErrors(
        context: Activity,
        logToDisplay: String = "",
        retryAction: () -> Unit
    ): AlertDialog.Builder {
        val customDialogView = getDialogCustomView(context, logToDisplay)
        val dialog = AlertDialog.Builder(context).apply {
            setTitle(R.string.data_load_error)
            setMessage(R.string.data_load_error_desc)
            setView(customDialogView)
            setPositiveButton(R.string.retry) { _, _ ->
                retryAction()
            }
            setNegativeButton(R.string.close, null)
            setCancelable(true)
        }
        return dialog
    }

    private fun getDialogCustomView(
        context: Activity,
        logToDisplay: String
    ): View {
        val dialogLayout = DialogErrorLogBinding.inflate(context.layoutInflater)
        dialogLayout.apply {
            moreInfo.setOnClickListener {
                logDisplay.isVisible = true
                moreInfo.isVisible = false
            }
            setTextviewUnderlined(troubleshootingLink)
            troubleshootingLink.setOnClickListener {
                openTroubleshootingPage(context)
            }

            if (logToDisplay.isNotBlank()) {
                logDisplay.text = logToDisplay
                moreInfo.isVisible = true
            }
        }
        return dialogLayout.root
    }

    fun dismissAllAndShow(alertDialogBuilder: AlertDialog.Builder) {
        if (lastDialog?.isShowing == true) {
            lastDialog?.dismiss()
        }
        alertDialogBuilder.create().run {
            this.show()
            lastDialog = this
        }
    }

    private fun setTextviewUnderlined(textView: TextView) {
        textView.paintFlags = textView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }

    private fun openTroubleshootingPage(context: Activity) {
        context.run {
            val troubleshootUrl = getString(R.string.troubleshootURL)
            val openUrlIntent = Intent(Intent.ACTION_VIEW)
            openUrlIntent.data = Uri.parse(troubleshootUrl)
            startActivity(openUrlIntent)
        }
    }
}
