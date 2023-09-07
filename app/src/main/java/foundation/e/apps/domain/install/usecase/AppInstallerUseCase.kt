package foundation.e.apps.domain.install.usecase

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.enums.User
import foundation.e.apps.domain.common.repository.CommonRepository
import java.util.Locale
import javax.inject.Inject

class AppInstallerUseCase@Inject constructor(
    private val commonRepository: CommonRepository
) {

    fun currentAuthData(): AuthData? {
        return try {
            commonRepository.cacheAuthData()
        } catch (e: Exception) {
            if (commonRepository.currentUser() == User.NO_GOOGLE) {
                return AuthData("", "").apply {
                    this.isAnonymous = false
                    this.locale = Locale.getDefault()
                }
            }
            return null
        }
    }
}
