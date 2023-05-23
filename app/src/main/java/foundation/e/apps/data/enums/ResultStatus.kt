package foundation.e.apps.data.enums

enum class ResultStatus {
    OK,
    TIMEOUT,
    UNKNOWN,
    RETRY;
    var message: String = ""
}
