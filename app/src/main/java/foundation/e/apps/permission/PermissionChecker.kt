package foundation.e.apps.permission

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

object PermissionChecker {

    private const val TAG = "PermissionChecker"

    @RequiresApi(Build.VERSION_CODES.O)
    fun run(context: Context, supportFragmentManager: FragmentManager) {

        if (!context.packageManager.canRequestPackageInstalls()) {
            InstallSettingDialog().show(supportFragmentManager, TAG)
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                ExternalManagerSettingDialog().show(supportFragmentManager, TAG)
            }
        } else {

        }
    }

    class InstallSettingDialog() : DialogFragment() {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(requireContext())
                .setMessage("App Lounge requires some more permission to install packages.")
                .setCancelable(false)
                .setPositiveButton("Go to settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
                }
                .create()
    }

    class ExternalManagerSettingDialog() : DialogFragment() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(requireContext())
                .setMessage("App Lounge requires some more permission to access files.")
                .setCancelable(false)
                .setPositiveButton("Go to settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                .create()
    }
}