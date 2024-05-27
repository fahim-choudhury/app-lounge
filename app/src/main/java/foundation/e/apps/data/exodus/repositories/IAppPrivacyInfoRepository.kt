package foundation.e.apps.data.exodus.repositories

import foundation.e.apps.data.Result
import foundation.e.apps.data.exodus.models.AppPrivacyInfo
import foundation.e.apps.data.application.data.Application

interface IAppPrivacyInfoRepository {
    suspend fun getAppPrivacyInfo(application: Application, appHandle: String): Result<AppPrivacyInfo>
}
