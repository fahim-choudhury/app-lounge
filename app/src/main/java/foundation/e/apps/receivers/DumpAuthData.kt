// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import foundation.e.apps.data.Constants.ACTION_AUTHDATA_DUMP
import foundation.e.apps.data.Constants.TAG_AUTHDATA_DUMP
import foundation.e.apps.data.preference.DataStoreModule
import org.json.JSONObject
import timber.log.Timber

/**
 * ADB commands:
 *
 * adb logcat -c
 * adb logcat -s "AUTHDATA_DUMP" &
 * adb shell am broadcast -a foundation.e.apps.action.DUMP_GACCOUNT_INFO --receiver-include-background
 */
class DumpAuthData : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_AUTHDATA_DUMP || context == null) {
            return
        }
        getAuthDataDump(context).let {
            Timber.tag(TAG_AUTHDATA_DUMP).i(it)
        }
    }

    private fun getAuthDataDump(context: Context): String {
        val gson = Gson()
        // TODO: replace with context.configuration
        val authData = DataStoreModule(context, gson).getAuthDataSync().let {
            gson.fromJson(it, AuthData::class.java)
        }
        val filteredData = JSONObject().apply {
            put("email", authData.email)
            put("authToken", authData.authToken)
            put("gsfId", authData.gsfId)
            put("locale", authData.locale)
            put("tokenDispenserUrl", authData.tokenDispenserUrl)
            put("deviceCheckInConsistencyToken", authData.deviceCheckInConsistencyToken)
            put("deviceConfigToken", authData.deviceConfigToken)
            put("dfeCookie", authData.dfeCookie)
            put("isAnonymous", authData.isAnonymous)
            put("deviceInfoProvider", authData.deviceInfoProvider)
        }
        return filteredData.toString(4)
    }
}
