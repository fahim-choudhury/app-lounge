package foundation.e.apps.domain.install.usecase

import foundation.e.apps.domain.common.repository.CommonRepository
import javax.inject.Inject

class AppInstallerUseCase@Inject constructor(
    private val commonRepository: CommonRepository
) {

    fun currentAuthData() = commonRepository.cacheAuthData()
}
