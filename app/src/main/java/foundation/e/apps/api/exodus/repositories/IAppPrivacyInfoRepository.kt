package foundation.e.apps.api.exodus.repositories

import foundation.e.apps.api.Result
import foundation.e.apps.api.exodus.models.AppPrivacyInfo
import foundation.e.apps.api.fused.data.FusedApp

interface IAppPrivacyInfoRepository {
    suspend fun getAppPrivacyInfo(fusedApp: FusedApp, appHandle: String): Result<AppPrivacyInfo>
    fun calculatePrivacyScore(fusedApp: FusedApp): Int
}
