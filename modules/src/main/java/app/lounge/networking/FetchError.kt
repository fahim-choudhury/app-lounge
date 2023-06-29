package app.lounge.networking


//region Generic Network Error Types

sealed interface FetchError {
    data class Network(val underlyingError: AnyFetchError) : FetchError
}

/** Supertype for network error types. */
sealed interface AnyFetchError {

    var description: String

    enum class BadRequest(override var description: String) : AnyFetchError {
        Encode("FIXME: Error encoding request! $dumpKeyWord"),
        Decode("FIXME: Error decoding request! $dumpKeyWord"),
    }

    enum class NotFound(override var description: String) : AnyFetchError {
        MissingData("No data found! $dumpKeyWord"),
        MissingNetwork("No network! $dumpKeyWord")
    }

    data class BadStatusCode (val statusCode: Int, val rawResponse: Any) : AnyFetchError {
        override var description: String =
            "Bad status code: $statusCode. Raw response: $rawResponse"
    }

    /**
     * Represents a vague error case typically caused by `UnknownHostException`.
     * This error case is encountered if and only if network status cannot be determined
     * while the `UnknownHostException` is received.
     */
    data class Unknown(
        override var description: String = "Unknown Error! $dumpKeyWord"
    ) : AnyFetchError

    companion object {
        const val dumpKeyWord: String = "dump:-"

        fun make(error: AnyFetchError, addingDump: String) : AnyFetchError {
            error.description = error.description + addingDump
            return error
        }
    }

}

//endregion
