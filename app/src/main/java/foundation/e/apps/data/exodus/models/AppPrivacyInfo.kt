package foundation.e.apps.data.exodus.models

data class AppPrivacyInfo(val trackerList: List<String> = listOf(), val permissionList: List<String> = listOf(), val reportId: Long = -1L)
