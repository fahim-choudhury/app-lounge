package foundation.e.apps.domain.updates.usecase

import foundation.e.apps.domain.common.repository.CommonRepository
import javax.inject.Inject

class UpdatesUseCase @Inject constructor(
    private val commonRepository: CommonRepository
) {
    fun currentUser() =  commonRepository.currentUser()

    fun currentAuthData() = commonRepository.cacheAuthData()
}