/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.ui.application

import android.content.Intent
import android.net.Uri

object AppShareIntent {
    fun create(appName: String, appShareUri: Uri): Intent {
        val extraText = "$appName \n$appShareUri"

        val uriIntent = Intent(Intent.ACTION_SEND).apply {
            setData(appShareUri)
            putExtra(Intent.EXTRA_TITLE, appName)
        }

        val textIntent = Intent(Intent.ACTION_SEND).apply {
            setType("text/plain")
            putExtra(Intent.EXTRA_SUBJECT, appName)
            putExtra(Intent.EXTRA_TITLE, appName)
            putExtra(Intent.EXTRA_TEXT, extraText)
        }

        val shareIntent = Intent(textIntent).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(uriIntent))
        }

        return shareIntent
    }
}
