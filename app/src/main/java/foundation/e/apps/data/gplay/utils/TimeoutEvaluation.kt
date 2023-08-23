/*
 * Copyright (C) 2019-2023  MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.data.gplay.utils

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import foundation.e.apps.data.Constants
import foundation.e.apps.data.ResultSupreme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Observing a LiveData/Flow for timeout is not easy.
 * We use a [CountDownTimer] to keep track of intervals between two emissions of data.
 * Also we had to collect items from Flow and emit it to a LiveData to avoid missing data emissions.
 *
 * @param block Code block producing the Flow
 * @param moreItemsToLoad Code block evaluating if more items will be loaded in the Flow.
 * Check if the item passed is the last item or not. If it is the last item, return false.
 * @param timeoutBlock Mandatory code block to execute for timeout.
 * Pass empty data from this block or any other data.
 * @param exceptionBlock Optional code block to execute for any other error.
 *
 * @return LiveData containing items from the Flow from [block], each item
 * wrapped in [ResultSupreme].
 */
suspend fun <T> runFlowWithTimeout(
    block: suspend () -> Flow<T>,
    moreItemsToLoad: suspend (item: T) -> Boolean,
    timeoutBlock: () -> T,
    exceptionBlock: ((e: Exception) -> T?)? = null,
): LiveData<ResultSupreme<T>> {

    return liveData {
        withContext(Dispatchers.Main) {

            val timer =
                Timer(this) {
                    emit(ResultSupreme.Timeout(timeoutBlock()))
                    cancel()
                }

            try {
                withContext(Dispatchers.IO) {
                    timer.start()
                    block().collect { item ->
                        timer.cancel()
                        emit(ResultSupreme.Success(item))
                        if (!moreItemsToLoad(item)) {
                            cancel()
                        }
                        timer.start()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    return@withContext
                }
                runCatching {
                    emit(
                        ResultSupreme.Error<T>(e.stackTraceToString()).apply {
                            exceptionBlock?.invoke(e)?.let { setData(it) }
                        }
                    )
                }
            }
        }
    }
}

private class Timer(
    private val scope: CoroutineScope,
    private val onTimeout: suspend () -> Unit,
) : CountDownTimer(Constants.timeoutDurationInMillis, 1000) {

    override fun onTick(millisUntilFinished: Long) {}

    override fun onFinish() {
        scope.launch {
            onTimeout()
        }
    }
}
