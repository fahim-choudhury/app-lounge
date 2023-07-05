package app.lounge.networking

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.IllegalStateException
import java.net.UnknownHostException


//region Retrofit Asynchronous Networking
interface NetworkFetching {
    val executor: Executor get() = callEnqueue

    val checkNetwork: (() -> Boolean)? get() = null

    interface Executor {
        fun <R> fetchAndCallback(endpoint: Call<R>, callback: Callback<R>)
    }

    /**
     * An object that receives Retrofit `Callback<T>` arguments and determines whether or not
     * an error should be returned. Use to return specific error cases for a given network request.
     */
    interface ResultProcessing<R, E> {
        /**
         * Return `R` if possible to cast given object to `R` or return `null`.
         * This lambda is required due to jvm's type-erasing for generic types.
         */
        val tryCastResponseBody: (Any?) -> R?

        /** Return result, success/failure, for the received request response */
        val resultFromResponse: (call: Call<R>, response: Response<R>) -> RetrofitResult<R, E>

        /** Return error object `E` for the given failed request. */
        val errorFromFailureResponse: (call: Call<*>, t: Throwable) -> E

        /** Return error object `E` that contains/represents the given `AnyFetchError` */
        val errorFromNetworkFailure: (AnyFetchError) -> E

    }

    companion object {
        /** Creates and returns a network request executor using Retrofit `Call<T>` enqueue. */
        val callEnqueue: Executor
            get() {
                return object : Executor {
                    override fun <R> fetchAndCallback(endpoint: Call<R>, callback: Callback<R>) {
                        endpoint.enqueue(callback)
                    }
                }
            }
    }
}

/**
 * Fetch for response type `R` and callback in `success` callback. Invokes failure with
 * an error subtype of `AnyFetchError` upon failure.
 *
 * @param usingExecutor Network request executor (set to `this.executor` by default)
 * @param endpoint The API endpoint that should be fetched
 * @param success Success callback with the response `R`
 * @param failure Failure callback with an error case from `AnyFetchError` subtypes
 */
inline fun <reified R> NetworkFetching.fetch(
    usingExecutor: NetworkFetching.Executor = executor,
    endpoint: Call<R>,
    noinline success: (R) -> Unit,
    noinline failure: (FetchError) -> Unit
) {
    val resultProcessing = RetrofitResultProcessing<R, FetchError>(
        errorFromNetworkFailure = { FetchError.Network(it) },
        hasNetwork = checkNetwork
    )
    fetch(usingExecutor, endpoint, resultProcessing, success, failure)
}

/**
 * Fetch for response type `R` and callback in `success` callback. Invokes failure with
 * an error subtype of `E : AnyFetchError` upon failure.
 *
 * @param usingExecutor Network request executor (set to `this.executor` by default)
 * @param endpoint The API endpoint that should be fetched
 * @param resultProcessing Processes response and finds corresponding error case (if needed)
 * @param success Success callback with the response `R`
 * @param failure Failure callback with an error case from given error subtype `E`
 */
fun <R, E> NetworkFetching.fetch(
    usingExecutor: NetworkFetching.Executor = executor,
    endpoint: Call<R>,
    resultProcessing: NetworkFetching.ResultProcessing<R, E>,
    success: (R) -> Unit,
    failure: (E) -> Unit
) {
    fetch(usingExecutor, endpoint, resultProcessing) { it.invoke(success, failure) }
}

inline fun <R, E> RetrofitResult<R, E>.invoke(success: (R) -> Unit, failure: (E) -> Unit) {
    return when (this) {
        is RetrofitResult.Success -> success(this.result)
        is RetrofitResult.Failure -> failure(this.error)
    }
}

sealed class RetrofitResult<R, E> {
    data class Success<R, E>(val result: R) : RetrofitResult<R, E>()
    data class Failure<R, E>(val error: E) : RetrofitResult<R, E>()
}

private fun <R, E> fetch(
    usingExecutor: NetworkFetching.Executor,
    endpoint: Call<R>,
    resultProcessing: NetworkFetching.ResultProcessing<R, E>,
    callback: (RetrofitResult<R, E>) -> Unit,
) {
    usingExecutor.fetchAndCallback(endpoint, object : Callback<R> {
        override fun onFailure(call: Call<R>, t: Throwable) {
            callback(RetrofitResult.Failure(resultProcessing.errorFromFailureResponse(call, t)))
        }

        override fun onResponse(call: Call<R>, response: Response<R>) {
            callback(resultProcessing.resultFromResponse(call, response))
        }
    })
}

//endregion

//region Retrofit Standard Result Processing

/** Returns result processing object for given response type `R` and error type `E` */
open class RetrofitResultProcessing<R, E>(
    override val tryCastResponseBody: (Any?) -> R?,
    override val errorFromNetworkFailure: (AnyFetchError) -> E,
    hasNetwork: (() -> Boolean)? = null,
) : NetworkFetching.ResultProcessing<R, E> {

    companion object {
        inline operator fun <reified R, E> invoke(
            noinline errorFromNetworkFailure: (AnyFetchError) -> E,
            noinline hasNetwork: (() -> Boolean)? = null
        ) : RetrofitResultProcessing<R, E> {
            return RetrofitResultProcessing<R, E>(
                tryCastResponseBody = {
                    if (it == null && R::class.java == Unit::class.java) { Unit as R }
                    else { it as? R }
                },
                errorFromNetworkFailure = errorFromNetworkFailure, hasNetwork = hasNetwork
            )
        }
    }

    override var errorFromFailureResponse: (call: Call<*>, t: Throwable) -> E = { call, t ->
        val error: AnyFetchError = when(t) {
            is UnknownHostException -> errorForUnknownHostException
            is IllegalStateException -> errorForIllegalStateException

            //TODO: Check other cases
            else -> AnyFetchError.Unknown()
        }
        errorFromNetworkFailure(
            AnyFetchError.make(error = error, addingDump = "(Throwable): $t\n(Call): $call")
        )
    }

    override var resultFromResponse:
                (Call<R>, Response<R>) -> RetrofitResult<R, E> = { call, response ->
        if (response.isSuccessful) {
            tryCastResponseBody(response.body())?.let { body ->
                RetrofitResult.Success(body)
            } ?: RetrofitResult.Failure(
                errorFromNetworkFailure(
                    AnyFetchError.make(
                        error = AnyFetchError.NotFound.MissingData,
                        addingDump = "(Response): $response\n(Call): $call"
                    )
                )
            )
        } else {
            RetrofitResult.Failure(
                errorFromNetworkFailure(AnyFetchError.BadStatusCode(response.code(), response))
            )
        }
    }

    //region Exception to AnyFetchError mapping

    var errorForUnknownHostException: AnyFetchError = if (hasNetwork != null) {
        if (hasNetwork()) AnyFetchError.BadRequest.Encode
        else AnyFetchError.NotFound.MissingNetwork
    } else {
        // Cannot distinguish the error case from `MissingNetwork` and `Encode` error.
        AnyFetchError.Unknown()
    }

    var errorForIllegalStateException: AnyFetchError = AnyFetchError.BadRequest.Decode

    //endregion

}

//endregion