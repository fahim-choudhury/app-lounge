/*
 * Copyright ECORP SAS 2022
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

package foundation.e.apps.install.workmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import foundation.e.apps.R
import java.util.concurrent.atomic.AtomicInteger

@HiltWorker
class InstallAppWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val appInstallProcessor: AppInstallProcessor
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "InstallWorker"
        const val INPUT_DATA_FUSED_DOWNLOAD = "input_data_fused_download"
        const val IS_UPDATE_WORK = "is_update_work"

        /*
         * If this is not "static" then each notification has the same ID.
         * Making it static makes sure the id keeps increasing when atomicInteger.getAndIncrement()
         * is called.
         * This is possible cause for "Installing ..." notification to linger around
         * after the app is installed.
         *
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5330
         */
        private val atomicInteger = AtomicInteger(100)
    }

    override suspend fun doWork(): Result {
        val fusedDownloadId = params.inputData.getString(INPUT_DATA_FUSED_DOWNLOAD) ?: ""
        val isPackageUpdate = params.inputData.getBoolean(IS_UPDATE_WORK, false)
        appInstallProcessor.processInstall(fusedDownloadId, isPackageUpdate) { title ->
            setForeground(
                createForegroundInfo(
                    "${context.getString(R.string.installing)} $title"
                )
            )
        }
        return Result.success()
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val title = applicationContext.getString(R.string.app_name)
        val cancel = applicationContext.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(getId())

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mChannel = NotificationChannel(
                "applounge_notification",
                title,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(mChannel)
        }

        val notification = NotificationCompat.Builder(applicationContext, "applounge_notification")
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(progress)
            .setSmallIcon(R.drawable.app_lounge_notification_icon)
            .setOngoing(true)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return ForegroundInfo(atomicInteger.getAndIncrement(), notification)
    }
}
