/*
 * Copyright (C) 2019-2022  E FOUNDATION
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

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import foundation.e.apps.BuildConfig
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.fused.data.FusedApp
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL

object CommonUtilsFunctions {

    /**
     * Copy anything to system clipboard.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5653
     */
    fun copyTextToClipboard(
        clipboard: ClipboardManager,
        label: String,
        text: String,
    ) {
        // https://developer.android.com/guide/topics/text/copy-paste#Copying
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

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
            put("os_version", getSystemProperty("ro.lineage.version"))
            put("build_id", BuildConfig.BUILD_ID)
        }
        return descriptionJson.toString()
    }

    fun getIconImageToBase64(url: String): String? {
        return try {
            val stream = URL(url).openStream()
            val bitmap = BitmapFactory.decodeStream(stream)
            val byteArrayOS = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOS)
            Base64.encodeToString(byteArrayOS.toByteArray(), Base64.DEFAULT)
        } catch (e: IOException) {
            Timber.e(e)
            null
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }
}
