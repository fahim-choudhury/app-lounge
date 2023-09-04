package foundation.e.apps.domain.main.usecase

import foundation.e.apps.domain.common.repository.CommonRepository
import javax.inject.Inject

class MainActivityUserCase @Inject constructor(
    private val commonRepository: CommonRepository
) {
    fun currentUser() = commonRepository.currentUser()

    fun currentAuthData() = commonRepository.cacheAuthData()
}
