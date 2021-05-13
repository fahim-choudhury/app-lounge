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
import android.os.Handler
import android.os.Looper
import foundation.e.apps.application.model.InstallerInterface

object LaunchUtils {
    fun startInstallSplitApksActivity(mActivity: Context, apksBean: ApksBean, callback: InstallerInterface) {
        mActivity.startActivity(InstallSplitApksActivity.newInstanceIntent(mActivity, apksBean))
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            callback.onInstallationComplete(mActivity)
        }, 10000)

    }

}
