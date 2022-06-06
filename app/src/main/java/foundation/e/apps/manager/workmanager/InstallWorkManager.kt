package foundation.e.apps.manager.workmanager

import android.app.Application
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import foundation.e.apps.manager.database.fusedDownload.FusedDownload

object InstallWorkManager {
    const val INSTALL_WORK_NAME = "APP_LOUNGE_INSTALL_APP"
    lateinit var context: Application

    fun enqueueWork(fusedDownload: FusedDownload) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            INSTALL_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<InstallAppWorker>().setInputData(
                Data.Builder()
                    .putString(InstallAppWorker.INPUT_DATA_FUSED_DOWNLOAD, fusedDownload.id)
                    .build()
            ).addTag(fusedDownload.id)
                .build()
        )
    }

    fun cancelWork(tag: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
    }
}
