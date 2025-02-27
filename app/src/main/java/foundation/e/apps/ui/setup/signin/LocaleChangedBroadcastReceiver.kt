/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2022  E FOUNDATION
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

package foundation.e.apps.ui.setup.signin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.data.preference.AppLoungeDataStore
import foundation.e.apps.data.preference.getSync
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@DelicateCoroutinesApi
class LocaleChangedBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appLoungeDataStore: AppLoungeDataStore
    @Inject
    lateinit var gson: Gson
    @Inject
    lateinit var cache: Cache

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCALE_CHANGED) {
            // security measure so that only the android system can call the receiver
            return
        }
        GlobalScope.launch {
            try {
                val authDataJson = appLoungeDataStore.authData.getSync()
                val authData = gson.fromJson(authDataJson, AuthData::class.java)
                authData.locale = context.resources.configuration.locales[0]
                appLoungeDataStore.saveAuthData(authData)
                withContext(Dispatchers.IO) {
                    cache.evictAll()
                }
            } catch (ex: Exception) {
                Timber.e(ex.message.toString())
            }
        }
    }
}
