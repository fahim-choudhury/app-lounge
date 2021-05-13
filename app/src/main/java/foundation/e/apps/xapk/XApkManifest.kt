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

package foundation.e.apps.xapk

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class XApkManifest (@Expose
                         @SerializedName("xapk_version")
                         var xApkVersion: Int,
                         @Expose
                         @SerializedName("package_name")
                         var packageName: String,
                         @Expose
                         @SerializedName("name")
                         var label: String,
                         @Expose
                         @SerializedName("locales_name")
                         var localesLabel: Map<String, String>? = null,
                         @Expose
                         @SerializedName("version_code")
                         var versionCode: String,
                         @Expose
                         @SerializedName("version_name")
                         var versionName: String,
                         @Expose
                         @SerializedName("min_sdk_version")
                         var minSdkVersion: String,
                         @Expose
                         @SerializedName("target_sdk_version")
                         var targetSdkVersion: String,
                         @Expose
                         @SerializedName("permissions")
                         var permissions: List<String>? = null,
                         @Expose
                         @SerializedName("total_size")
                         var totalSize: Long,
                         @Expose
                         @SerializedName("expansions")
                         var expansionList: List<XApkExpansion>? = null,
                         @Expose
                         @SerializedName("split_apks")
                         var XSplitApks: List<XSplitApks>? = null,
                         @Expose
                         @SerializedName("split_configs")
                         var splitConfigs: Array<String>? = null) {
    constructor() : this(
        0
        , String(), String()
        , null, String()
        , String(), String()
        , String(), null
        , 0L, null
        , null, null
    )

    fun useSplitApks() = !this.XSplitApks.isNullOrEmpty()

    fun useObbs() = !this.expansionList.isNullOrEmpty()


}

