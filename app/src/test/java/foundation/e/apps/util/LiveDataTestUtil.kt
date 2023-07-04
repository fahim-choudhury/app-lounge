/*
 *  Copyright (C) 2022  ECORP
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.delay

/**
 * Gets the value of a [LiveData] or waits for it to have one, with a timeout.
 *
 * Use this extension from host-side (JVM) tests. It's recommended to use it alongside
 * `InstantTaskExecutorRule` or a similar mechanism to execute tasks synchronously.
 */
suspend fun <T> LiveData<T>.getOrAwaitValue(
    time: Long = 5,
    afterObserve: () -> Unit = {}
): T {
    var data: T? = null
    val observer = Observer<T> { o ->
        data = o
        print("onChanged: $o")
    }
    this.observeForever(observer)
    afterObserve.invoke()
    delay(time * 1000)
    this.removeObserver(observer)
    @Suppress("UNCHECKED_CAST")
    return data as T
}
