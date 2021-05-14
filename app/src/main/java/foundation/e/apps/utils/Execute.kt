/*
 * Copyright (C) 2019-2021  E FOUNDATION
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

package foundation.e.apps.utils

import android.os.AsyncTask

/**
 * Executes the given tasks given in constructors using [AsyncTask]
 * @param background Task to run in background
 * @param after Task to run after [background] task has been finished
 */
class Execute(private val background: () -> Unit, private val after: () -> Unit) :
    AsyncTask<Any, Any, Any>() {

    init {
        executeOnExecutor(Common.EXECUTOR)
    }

    /**
     * Runs the given task in background
     * @param params [Any]
     * @return [Any]
     */
    override fun doInBackground(vararg params: Any?): Any? {
        background.invoke()
        return null
    }

    /**
     * Runs the given task once the background task has finished
     * @param result [Any]
     */
    override fun onPostExecute(result: Any?) {
        after.invoke()
    }

}
