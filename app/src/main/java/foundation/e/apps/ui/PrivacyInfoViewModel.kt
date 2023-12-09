// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.Result
import foundation.e.apps.data.exodus.models.AppPrivacyInfo
import foundation.e.apps.data.exodus.repositories.IAppPrivacyInfoRepository
import foundation.e.apps.data.exodus.repositories.PrivacyScoreRepository
import foundation.e.apps.data.application.data.Application
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacyInfoViewModel @Inject constructor(
    private val privacyInfoRepository: IAppPrivacyInfoRepository,
    private val privacyScoreRepository: PrivacyScoreRepository,
) : ViewModel() {

    private val singularAppPrivacyInfoLiveData: MutableLiveData<Result<AppPrivacyInfo>> =
        MutableLiveData()

    fun getAppPrivacyInfoLiveData(application: Application): LiveData<Result<AppPrivacyInfo>> {
        return liveData {
            emit(fetchEmitAppPrivacyInfo(application))
        }
    }

    fun getSingularAppPrivacyInfoLiveData(application: Application?): LiveData<Result<AppPrivacyInfo>> {
        fetchPrivacyInfo(application)
        return singularAppPrivacyInfoLiveData
    }

    fun refreshAppPrivacyInfo(application: Application?) {
        fetchPrivacyInfo(application, true)
    }

    private fun fetchPrivacyInfo(application: Application?, forced: Boolean = false) {
        application?.let {
            if (forced) {
                it.trackers = emptyList()
                it.permsFromExodus = emptyList()
            }

            viewModelScope.launch {
                singularAppPrivacyInfoLiveData.postValue(fetchEmitAppPrivacyInfo(it))
            }
        }
    }

    private suspend fun fetchEmitAppPrivacyInfo(
        application: Application
    ): Result<AppPrivacyInfo> {
        val appPrivacyPrivacyInfoResult =
            privacyInfoRepository.getAppPrivacyInfo(application, application.package_name)
        return handleAppPrivacyInfoResult(appPrivacyPrivacyInfoResult)
    }

    private fun handleAppPrivacyInfoResult(
        appPrivacyPrivacyInfoResult: Result<AppPrivacyInfo>,
    ): Result<AppPrivacyInfo> {
        return if (!appPrivacyPrivacyInfoResult.isSuccess()) {
            Result.error("Tracker not found!")
        } else appPrivacyPrivacyInfoResult
    }

    fun getTrackerListText(application: Application?): String {
        application?.let {
            if (it.trackers.isNotEmpty()) {
                return it.trackers.joinToString(separator = "") { tracker -> "$tracker<br />" }
            }
        }
        return ""
    }

    fun getPrivacyScore(application: Application?): Int {
        application?.let {
            return privacyScoreRepository.calculatePrivacyScore(it)
        }
        return -1
    }

    fun shouldRequestExodusReport(application: Application?): Boolean {
        if (application?.isFree != true) {
            return false
        }

        return getPrivacyScore(application) < 0
    }
}
