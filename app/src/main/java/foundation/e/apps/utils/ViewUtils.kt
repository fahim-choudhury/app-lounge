// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.utils

import android.content.Context
import android.content.res.Resources
import android.widget.Toast

fun Int.toDP(): Int {
    return (this / Resources.getSystem().displayMetrics.density).toInt()
}

fun Int.toPX(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}

fun Context.toast(messageResId: Int, duration: Int = Toast.LENGTH_LONG) =
    Toast.makeText(this, messageResId, duration).show()

fun Context.toast(message: String, duration: Int = Toast.LENGTH_LONG) =
    Toast.makeText(this, message, duration).show()
