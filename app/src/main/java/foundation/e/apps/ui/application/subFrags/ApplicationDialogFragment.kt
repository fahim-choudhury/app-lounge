/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
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

package foundation.e.apps.ui.application.subFrags

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R

@AndroidEntryPoint
class ApplicationDialogFragment() : DialogFragment() {

    private var drawable: Int = -1
    private var title: String? = null
    private var message: String? = null
    private var positiveButtonText: String? = null
    private var positiveButtonAction: (() -> Unit)? = null
    private var cancelButtonText: String? = null
    private var cancelButtonAction: (() -> Unit)? = null
    private var cancellable: Boolean = true
    private var onDismissListener: (() -> Unit)? = null

    constructor(
        drawable: Int = -1,
        title: String,
        message: String,
        positiveButtonText: String = "",
        positiveButtonAction: (() -> Unit)? = null,
        cancelButtonText: String = "",
        cancelButtonAction: (() -> Unit)? = null,
        cancellable: Boolean = true,
        onDismissListener: (() -> Unit)? = null,
    ) : this() {
        this.drawable = drawable
        this.title = title
        this.message = message
        this.positiveButtonText = positiveButtonText
        this.positiveButtonAction = positiveButtonAction
        this.cancelButtonText = cancelButtonText
        this.cancelButtonAction = cancelButtonAction
        this.cancellable = cancellable
        this.onDismissListener = onDismissListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val positiveButtonText =
            positiveButtonText?.ifEmpty { getString(R.string.ok) }
        val materialAlertDialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(Html.fromHtml(title ?: "", Html.FROM_HTML_MODE_COMPACT))
            .setMessage(Html.fromHtml(message ?: "", Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton(positiveButtonText) { _, _ ->
                positiveButtonAction?.invoke()
                this.dismiss()
            }
            .setCancelable(cancellable)
        if (cancelButtonText?.isNotEmpty() == true) {
            materialAlertDialogBuilder.setNegativeButton(cancelButtonText) { _, _ ->
                cancelButtonAction?.invoke()
                this.dismiss()
            }
        }
        if (drawable != -1) {
            materialAlertDialogBuilder.setIcon(drawable)
        }
        return materialAlertDialogBuilder.create()
    }

    override fun onResume() {
        super.onResume()
        dialog?.findViewById<TextView>(android.R.id.message)?.apply {
            movementMethod = LinkMovementMethod.getInstance()
            isClickable = true
            removeUnderlineFromLinks()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    private fun TextView.removeUnderlineFromLinks() {
        val spannable = SpannableString(text)
        for (urlSpan in spannable.getSpans(0, spannable.length, URLSpan::class.java)) {
            spannable.setSpan(
                object : URLSpan(urlSpan.url) {
                    override fun updateDrawState(textPaint: TextPaint) {
                        super.updateDrawState(textPaint)
                        textPaint.isUnderlineText = false
                    }
                },
                spannable.getSpanStart(urlSpan),
                spannable.getSpanEnd(urlSpan),
                0
            )
        }
        text = spannable
    }
}
