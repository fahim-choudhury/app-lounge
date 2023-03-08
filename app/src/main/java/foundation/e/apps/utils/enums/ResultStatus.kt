package foundation.e.apps.utils.enums

enum class ResultStatus {
    OK,
    TIMEOUT,
    UNKNOWN,
    UNAUTHORIZED,
    RETRY;
    var message: String = ""
}
