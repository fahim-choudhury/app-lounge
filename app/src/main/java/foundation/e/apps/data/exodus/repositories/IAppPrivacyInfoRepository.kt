package foundation.e.apps.data.exodus.repositories

import foundation.e.apps.data.Result
import foundation.e.apps.data.exodus.models.AppPrivacyInfo
import foundation.e.apps.data.fused.data.FusedApp

interface IAppPrivacyInfoRepository {
    suspend fun getAppPrivacyInfo(fusedApp: FusedApp, appHandle: String): Result<AppPrivacyInfo>
}
