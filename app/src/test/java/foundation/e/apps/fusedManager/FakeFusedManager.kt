// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 MURENA SAS <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.fusedManager

import androidx.lifecycle.LiveData
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fusedDownload.FusedDownloadDAO
import foundation.e.apps.data.fusedDownload.IFusedManager
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import java.io.File

class FakeFusedManager(private val fusedDownloadDAO: FusedDownloadDAO) : IFusedManager {
    override fun createNotificationChannels() {
        TODO("Not yet implemented")
    }

    override suspend fun addDownload(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.QUEUED
        fusedDownloadDAO.addDownload(fusedDownload)
    }

    override suspend fun getDownloadById(fusedDownload: FusedDownload): FusedDownload? {
        return fusedDownloadDAO.getDownloadById(fusedDownload.id)
    }

    override suspend fun getDownloadList(): List<FusedDownload> {
        TODO("Not yet implemented")
    }

    override fun getDownloadLiveList(): LiveData<List<FusedDownload>> {
        TODO("Not yet implemented")
    }

    override suspend fun updateDownloadStatus(fusedDownload: FusedDownload, status: Status) {
        TODO("Not yet implemented")
    }

    override suspend fun downloadApp(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override suspend fun installApp(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override suspend fun cancelDownload(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override suspend fun getFusedDownload(downloadId: Long, packageName: String): FusedDownload {
        TODO("Not yet implemented")
    }

    override fun flushOldDownload(packageName: String) {
        TODO("Not yet implemented")
    }

    override suspend fun downloadNativeApp(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override fun getGplayInstallationPackagePath(
        fusedDownload: FusedDownload,
        it: String,
        parentPath: String,
        count: Int
    ): File {
        TODO("Not yet implemented")
    }

    override fun createObbFileForDownload(fusedDownload: FusedDownload, url: String): File {
        TODO("Not yet implemented")
    }

    override fun moveOBBFilesToOBBDirectory(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override fun getBaseApkPath(fusedDownload: FusedDownload): String {
        return "root/data/apps/${fusedDownload.packageName}/${fusedDownload.packageName}_1.apk"
    }

    override suspend fun installationIssue(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override suspend fun updateAwaiting(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override suspend fun updateUnavailable(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override suspend fun updateFusedDownload(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override suspend fun insertFusedDownloadPurchaseNeeded(fusedDownload: FusedDownload) {
        TODO("Not yet implemented")
    }

    override fun isFusedDownloadInstalled(fusedDownload: FusedDownload): Boolean {
        TODO("Not yet implemented")
    }

    override fun getFusedDownloadInstallationStatus(fusedApp: FusedDownload): Status {
        TODO("Not yet implemented")
    }
}
