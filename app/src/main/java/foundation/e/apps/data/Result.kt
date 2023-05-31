package foundation.e.apps.data

data class Result<T>(val status: Status, val data: T?, val message: String?) {

    enum class Status {
        SUCCESS,
        ERROR,
        LOADING
    }

    companion object {
        fun <T> success(data: T): Result<T> {
            return Result(
                Status.SUCCESS,
                data,
                null
            )
        }

        fun <T> error(message: String, data: T? = null): Result<T> {
            return Result(
                Status.ERROR,
                data,
                message
            )
        }

        fun <T> loading(data: T? = null): Result<T> {
            return Result(
                Status.LOADING,
                data,
                null
            )
        }
    }

    fun isSuccess() = status == Status.SUCCESS

    fun handleResult(handler: () -> Unit, defaultErrorData: T?): T? {
        if (isSuccess()) {
            handler()
            return data ?: defaultErrorData
        }
        return defaultErrorData
    }
}
