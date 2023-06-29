package app.lounge.networking

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

interface RawResponseProcessing<R, E, T> {
    val onResponse: (Call<T>, Response<T>) -> RetrofitResult<R, E>
}


fun <R, E, T, Processing> RetrofitFetching.fetch(
    usingExecutor: RetrofitFetching.Executor = executor,
    endpoint: Call<T>,
    processing: Processing,
    success: (R) -> Unit,
    failure: (E) -> Unit
) where Processing : RawResponseProcessing<R, E, T>,
        Processing : RetrofitFetching.ResultProcessing<R, E> {
    fetch(usingExecutor, endpoint, processing) { it.invoke(success, failure) }
}


private fun <R, E, T, Processing> fetch(
    usingExecutor: RetrofitFetching.Executor,
    endpoint: Call<T>,
    processing: Processing,
    callback: (RetrofitResult<R, E>) -> Unit,
) where Processing : RawResponseProcessing<R, E, T>,
        Processing : RetrofitFetching.ResultProcessing<R, E> {
    usingExecutor.fetchAndCallback(endpoint, object : Callback<T> {
        override fun onFailure(call: Call<T>, t: Throwable) {
            callback(RetrofitResult.Failure(processing.errorFromFailureResponse(call, t)))
        }

        override fun onResponse(call: Call<T>, response: Response<T>) {
            callback(processing.onResponse(call, response))
        }
    })
}

class RetrofitRawResultProcessing<R, E, T>(
    override val onResponse: (Call<*>, Response<*>) -> RetrofitResult<R, E>,
    override val tryCastResponseBody: (Any?) -> R?,
    override val errorFromNetworkFailure: (AnyFetchError) -> E,
    hasNetwork: (() -> Boolean)? = null,
) : RetrofitResultProcessing<R, E>(tryCastResponseBody, errorFromNetworkFailure, hasNetwork),
    RawResponseProcessing<R, E, T> {

    override var resultFromResponse: (Call<R>, Response<R>) -> RetrofitResult<R, E> = {
            call, response ->
        when(val customResult = onResponse(call, response)) {
            is RetrofitResult.Success -> customResult
            is RetrofitResult.Failure -> super.resultFromResponse(call, response)
        }
    }

    companion object {
        inline operator fun <reified R, E, T> invoke(
            noinline onResponse: (Call<*>, Response<*>) -> RetrofitResult<R, E>,
            noinline errorFromNetworkFailure: (AnyFetchError) -> E,
            noinline hasNetwork: (() -> Boolean)? = null
        ) : RetrofitRawResultProcessing<R, E, T> {
            return RetrofitRawResultProcessing(
                onResponse = onResponse,
                tryCastResponseBody = {
                    if (it == null && R::class.java == Unit::class.java) { Unit as R }
                    else { it as? R }
                },
                errorFromNetworkFailure = errorFromNetworkFailure, hasNetwork = hasNetwork
            )
        }
    }

}
