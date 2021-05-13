/*
 * Copyright (C) 2019-2021  E FOUNDATION
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

package foundation.e.apps.xapk

import android.content.Context
import android.content.Intent
import java.io.File

object IntentUtils {
    fun installedApk(mContext: Context, filePath: String) {
        if (FsUtils.exists(filePath)) {
            Intent().apply {
                this.action = Intent.ACTION_VIEW
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                this.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                this.setDataAndType(UriUtils.fromFileProvider(mContext, File(filePath)), "application/vnd.android.package-archive")
                mContext.startActivity(this)
            }
        }
    }


}
