/*
 * Copyright (C) 2019-2022  E FOUNDATION
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

package foundation.e.apps.utils.modules

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.WorkManager
import java.lang.Exception

object CommonUtilsFunctions {

    /**
     * Copy anything to system clipboard.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5653
     */
    fun copyTextToClipboard(
        clipboard: ClipboardManager,
        label: String,
        text: String,
    ) {
        // https://developer.android.com/guide/topics/text/copy-paste#Copying
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String?): String? {
        var value: String? = null
        try {
            value = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java).invoke(null, key) as String
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return value
    }

    fun checkWorkIsAlreadyAvailable(context: Context, tag: String): Boolean {
        val works = WorkManager.getInstance(context).getWorkInfosByTag(tag)
        try {
            works.get().forEach {
                if (it.tags.contains(tag) && !it.state.isFinished) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
