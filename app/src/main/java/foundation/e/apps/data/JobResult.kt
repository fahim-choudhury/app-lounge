package foundation.e.apps.data

import foundation.e.apps.utils.enums.ResultStatus

/**
 * Currently defunct, not being used anywhere.
 * Prototype to merge API request and also get rid of Pair, Triple for timeout related cases.
 */
open class JobResult<T> private constructor(val status: ResultStatus) {

    /*
     * Classes for returning multiple data from a function along with a status
     * in the form of ResultStatus.
     * Use the static overloaded create methods (in companion object) to for easy creation.
     *
     * If needed to just pass a single data element with status for API requests,
     * see the static methods success(), error(), loading() (in companion object).
     */
    class of1<A> (val data1: A, status: ResultStatus) : JobResult<A>(status)
    class of2<A, B> (val data1: A, val data2: B, status: ResultStatus) : JobResult<A>(status)
    class of3<A, B, C> (val data1: A, val data2: B, val data3: C, status: ResultStatus) : JobResult<A>(status)

    var message = ""

    /*
     * This is the primary data, mainly for API requests which might send null data.
     * Other data (type B, C ...) are secondary/optional data.
     *
     * For non-null return type, directly use of1, of2, of3 ... classes
     * and directly access data1, data2, data3 ...
     */
    val data: T? get() = when (this) {
        is of1 -> this.data1
        is of2<T, *> -> this.data1
        is of3<T, *, *> -> this.data1
        else -> null
    }

    fun isSuccess(): Boolean {
        return status == ResultStatus.OK
    }

    companion object {
        fun <A> create(data1: A, status: ResultStatus, message: String? = null): of1<A> {
            return of1(data1, status).apply {
                message?.let { this.message = message }
            }
        }
        fun <A, B> create(data1: A, data2: B, status: ResultStatus, message: String? = null): of2<A, B> {
            return of2(data1, data2, status).apply {
                message?.let { this.message = message }
            }
        }
        fun <A, B, C> create(data1: A, data2: B, data3: C, status: ResultStatus, message: String? = null): of3<A, B, C> {
            return of3(data1, data2, data3, status).apply {
                message?.let { this.message = message }
            }
        }

        /*
         * Methods for API
         */
        fun <T> success(data: T): JobResult<T> {
            return of1(data, ResultStatus.OK)
        }
        fun <T> error(message: String, data: T? = null): JobResult<T> {
            val result = if (data == null) JobResult(ResultStatus.UNKNOWN)
            else of1<T>(data, ResultStatus.UNKNOWN)
            return result.apply {
                this.message = message
            }
        }
        /*fun <T> loading(data: T?): JobResult<T> {
            return if (data == null) JobResult(ResultStatus.LOADING)
            else JobResult.of1(data, ResultStatus.LOADING)
        }*/
    }
}
