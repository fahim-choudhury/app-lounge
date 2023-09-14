// Copyright ECORP SAS 2022
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import foundation.e.apps.install.download.data.DownloadProgress
import foundation.e.apps.install.download.data.DownloadProgressLD
import javax.inject.Inject

@HiltViewModel
class AppProgressViewModel @Inject constructor(
    downloadProgressLD: DownloadProgressLD,
    private val fusedManagerRepository: FusedManagerRepository
) : ViewModel() {

    val downloadProgress = downloadProgressLD

    suspend fun calculateProgress(
        fusedApp: FusedApp?,
        progress: DownloadProgress
    ): Int {
        return fusedManagerRepository.calculateProgress(fusedApp, progress)
    }
}
