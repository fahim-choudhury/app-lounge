package app.lounge.networking


sealed interface FetchError {

    var description: String

    enum class BadRequest(override var description: String) : FetchError {
        Encode("FIXME: Error encoding request! $dumpKeyWord"),
        Decode("FIXME: Error decoding request! $dumpKeyWord"),
    }

    enum class NotFound(override var description: String) : FetchError {
        MissingData("No data found! $dumpKeyWord"),
        MissingNetwork("No network! $dumpKeyWord")
    }

    enum class InterruptedIO(override var description: String) : FetchError {
        Timeout("Timeout Error! $dumpKeyWord")
    }

    data class BadStatusCode (val statusCode: Int, val rawResponse: Any) : FetchError {
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
    ) : FetchError

    companion object {
        const val dumpKeyWord: String = "dump:-"

        fun make(error: FetchError, addingDump: String = "") : FetchError {
            error.description = error.description + addingDump
            return error
        }
    }

}
