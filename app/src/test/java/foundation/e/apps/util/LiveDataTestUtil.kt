// Copyright (C) 2022  ECORP
// SPDX-FileCopyrightText: 2023 ECORP <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

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
