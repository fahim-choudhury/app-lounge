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

package foundation.e.apps.applicationmanager

import android.app.Service
import android.content.Intent
import android.os.*

class ApplicationManagerService : Service() {

    private val installManager = ApplicationManager()
    private val messenger = Messenger(SimpleHandler(installManager))


    override fun onCreate() {
        installManager.start(this)
    }

    override fun onBind(intent: Intent): IBinder? {
        return messenger.binder
    }

    private class SimpleHandler(private val applicationManager: ApplicationManager) : Handler(Looper.getMainLooper()) {

        @Suppress("UNCHECKED_CAST")
        override fun handleMessage(msg: Message) {
            (msg.obj as (ApplicationManager) -> Unit).invoke(applicationManager)
        }
    }
}
