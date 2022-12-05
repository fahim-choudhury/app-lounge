package foundation.e.apps.manager.workmanager

import android.app.Application
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import java.lang.Exception

object InstallWorkManager {
    const val INSTALL_WORK_NAME = "APP_LOUNGE_INSTALL_APP"
    lateinit var context: Application

    fun enqueueWork(fusedDownload: FusedDownload, isUpdateWork:Boolean = false) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            INSTALL_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<InstallAppWorker>().setInputData(
                Data.Builder()
                    .putString(InstallAppWorker.INPUT_DATA_FUSED_DOWNLOAD, fusedDownload.id)
                    .putBoolean(InstallAppWorker.IS_UPDATE_WORK, isUpdateWork)
                    .build()
            ).addTag(fusedDownload.id)
                .build()
        )
    }

    fun cancelWork(tag: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
    }

    fun checkWorkIsAlreadyAvailable(tag: String): Boolean {
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
