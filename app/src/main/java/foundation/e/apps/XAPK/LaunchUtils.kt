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

package foundation.e.apps.XAPK

import android.content.Context
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import foundation.e.apps.application.model.InstallerInterface

object LaunchUtils {
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    fun startInstallSplitApksActivity(mActivity: Context, apksBean: ApksBean, callback: InstallerInterface) {
        mActivity.startActivity(InstallSplitApksActivity.newInstanceIntent(mActivity, apksBean))
        val handler = Handler()
        handler.postDelayed({
            callback.onInstallationComplete(mActivity)
        }, 10000)

    }

}
