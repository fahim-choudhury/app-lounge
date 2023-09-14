// Copyright (C) 2019-2022  E FOUNDATION
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.utils

import android.annotation.SuppressLint
import android.os.Build
import foundation.e.apps.BuildConfig
import org.json.JSONObject

object SystemInfoProvider {

    const val KEY_LINEAGE_VERSION = "ro.lineage.version"

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String?): String? {
        var value: String? = null
        try {
            value = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java).invoke(null, key) as String
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return value
    }

    fun getAppBuildInfo(): String {
        val descriptionJson = JSONObject().apply {
            put("package", BuildConfig.APPLICATION_ID)
            put("version", BuildConfig.VERSION_NAME)
            put("device", Build.DEVICE)
            put("api", Build.VERSION.SDK_INT)
            put("os_version", getSystemProperty(KEY_LINEAGE_VERSION))
            put("build_id", BuildConfig.BUILD_ID)
        }
        return descriptionJson.toString()
    }
}
