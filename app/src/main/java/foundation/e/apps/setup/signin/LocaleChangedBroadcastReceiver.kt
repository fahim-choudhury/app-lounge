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

package foundation.e.apps.setup.signin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.utils.modules.DataStoreModule
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
    lateinit var dataStoreModule: DataStoreModule
    @Inject
    lateinit var gson: Gson
    @Inject
    lateinit var cache: Cache

    private val TAG = LocaleChangedBroadcastReceiver::class.java.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        GlobalScope.launch {
            try {
                val authDataJson = dataStoreModule.getAuthDataSync()
                val authData = gson.fromJson(authDataJson, AuthData::class.java)
                authData.locale = context.resources.configuration.locales[0]
                dataStoreModule.saveCredentials(authData)
                withContext(Dispatchers.IO) {
                    cache.evictAll()
                }
            } catch (ex: Exception) {
                Timber.e(ex.message.toString())
            }
        }
    }
}
