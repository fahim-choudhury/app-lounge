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
import foundation.e.apps.data.fused.data.FusedApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacyInfoViewModel @Inject constructor(
    private val privacyInfoRepository: IAppPrivacyInfoRepository,
    private val privacyScoreRepository: PrivacyScoreRepository,
) : ViewModel() {

    private val singularAppPrivacyInfoLiveData: MutableLiveData<Result<AppPrivacyInfo>> =
        MutableLiveData()

    fun getAppPrivacyInfoLiveData(fusedApp: FusedApp): LiveData<Result<AppPrivacyInfo>> {
        return liveData {
            emit(fetchEmitAppPrivacyInfo(fusedApp))
        }
    }

    fun getSingularAppPrivacyInfoLiveData(fusedApp: FusedApp?): LiveData<Result<AppPrivacyInfo>> {
        fetchPrivacyInfo(fusedApp)
        return singularAppPrivacyInfoLiveData
    }

    fun refreshAppPrivacyInfo(fusedApp: FusedApp?) {
        fetchPrivacyInfo(fusedApp, true)
    }

    private fun fetchPrivacyInfo(fusedApp: FusedApp?, forced: Boolean = false) {
        fusedApp?.let {
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
        fusedApp: FusedApp
    ): Result<AppPrivacyInfo> {
        val appPrivacyPrivacyInfoResult =
            privacyInfoRepository.getAppPrivacyInfo(fusedApp, fusedApp.package_name)
        return handleAppPrivacyInfoResult(appPrivacyPrivacyInfoResult)
    }

    private fun handleAppPrivacyInfoResult(
        appPrivacyPrivacyInfoResult: Result<AppPrivacyInfo>,
    ): Result<AppPrivacyInfo> {
        return if (!appPrivacyPrivacyInfoResult.isSuccess()) {
            Result.error("Tracker not found!")
        } else appPrivacyPrivacyInfoResult
    }

    fun getTrackerListText(fusedApp: FusedApp?): String {
        fusedApp?.let {
            if (it.trackers.isNotEmpty()) {
                return it.trackers.joinToString(separator = "") { tracker -> "$tracker<br />" }
            }
        }
        return ""
    }

    fun getPrivacyScore(fusedApp: FusedApp?): Int {
        fusedApp?.let {
            return privacyScoreRepository.calculatePrivacyScore(it)
        }
        return -1
    }

    fun shouldRequestExodusReport(fusedApp: FusedApp?): Boolean {
        if (fusedApp?.isFree != true) {
            return false
        }

        return getPrivacyScore(fusedApp) < 0
    }
}
