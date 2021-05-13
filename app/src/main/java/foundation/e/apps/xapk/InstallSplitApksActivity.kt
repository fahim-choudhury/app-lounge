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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import foundation.e.apps.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.IOException


class InstallSplitApksActivity : BaseActivity() {
    private  var apksBean: ApksBean?=null

    companion object{
        private const val KEY_PARAM = "params_apks"
        private const val PACKAGE_INSTALLED_ACTION = BuildConfig.APPLICATION_ID + ".SESSION_API_PACKAGE_INSTALLED"

        fun newInstanceIntent(mActivity: Context, apksBean: ApksBean): Intent {
            return Intent(mActivity, InstallSplitApksActivity::class.java).apply {
                this.putExtra(KEY_PARAM, apksBean)
            }
        }
    }

    override fun nextStep() {
        super.nextStep()
        apksBean = intent.getParcelableExtra(KEY_PARAM)
        if (apksBean == null
            || apksBean!!.splitApkPaths.isNullOrEmpty()
            || apksBean!!.packageName.isEmpty()) {
            finish()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({ this.install() }, 500)
    }

    private fun install() {
        var session: PackageInstaller.Session? = null
        try {
            val packageInstaller = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            params.setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)
            for (splitApk in apksBean!!.splitApkPaths!!) {
                addApkFileToInstallSession(File(splitApk), session)
            }
            val intent = Intent(mActivity, InstallSplitApksActivity::class.java)
            intent.action = PACKAGE_INSTALLED_ACTION
            intent.putExtra("packageName", apksBean!!.packageName)
            val pendingIntent = PendingIntent.getActivity(mActivity, 0, intent, 0)
            val statusReceiver = pendingIntent.intentSender
            // Commit the session (this will start the installation workflow).
            session.commit(statusReceiver)
            finish()
        } catch (e: IOException) {
            e.printStackTrace()
            finish()
            return
        } catch (e: RuntimeException) {
            session?.abandon()
            e.printStackTrace()
            finish()
            return
        }

    }

    @Throws(IOException::class)
    private fun addApkFileToInstallSession(file: File, session: PackageInstaller.Session) {
        session.openWrite(file.name, 0, -1).use { packageInSession ->
            FileInputStream(file).use { `is` ->
                val buffer = ByteArray(16384)
                var n: Int
                while (`is`.read(buffer).apply { n = this } >= 0) {
                    packageInSession.write(buffer, 0, n)
                }
                session.fsync(packageInSession)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) {
            return
        }
        val extras = intent.extras
        if (PACKAGE_INSTALLED_ACTION == intent.action) {
            val status = extras!!.getInt(PackageInstaller.EXTRA_STATUS)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // This test app isn't privileged, so the user has to confirm the install.
                    val confirmIntent = extras.get(Intent.EXTRA_INTENT) as Intent
                    startActivity(confirmIntent)
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    finish()
                }
                PackageInstaller.STATUS_FAILURE,
                PackageInstaller.STATUS_FAILURE_ABORTED,
                PackageInstaller.STATUS_FAILURE_BLOCKED,
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                PackageInstaller.STATUS_FAILURE_INVALID,
                PackageInstaller.STATUS_FAILURE_STORAGE -> {
                    finish()
                }
                else -> {}
            }

            if (status == PackageInstaller.STATUS_SUCCESS ||
                status == PackageInstaller.STATUS_FAILURE ||
                status == PackageInstaller.STATUS_FAILURE_ABORTED ||
                status == PackageInstaller.STATUS_FAILURE_BLOCKED ||
                status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ||
                status == PackageInstaller.STATUS_FAILURE_INVALID ||
                status == PackageInstaller.STATUS_FAILURE_STORAGE) {
                apksBean?.outputFileDir.let {
                    if (FsUtils.exists(it)){
                        FsUtils.deleteFileOrDir(it)
                    }
                }
            }
        }
    }


}


