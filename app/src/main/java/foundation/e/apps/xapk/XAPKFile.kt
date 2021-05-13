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

import foundation.e.apps.MainActivity.Companion.mActivity
import foundation.e.apps.application.model.InstallerInterface
import java.io.File

class XAPKFile(x: File, callback: InstallerInterface) {

    init{
        val fileName =getSingleXApkAssetInfo(x)
        fileName?.xApkInfo?.let { it1 -> ViewUtils.installXApk(mActivity, it1,callback) }
    }


    fun getSingleXApkAssetInfo(xApkFile: File): ApkAssetBean? {
        var apkAssetBean: ApkAssetBean? = null
        XApkInstallUtils.getXApkManifest(xApkFile)?.apply {
            val xApkManifest = this
            apkAssetBean = ApkAssetBean().apply {
                this.apkAssetType = ApkAssetType.XAPK
                this.xApkInfo = XApkInfo().apply {
                    //                    this.label = xApkManifest.getLocalLabel()
                    this.packageName = xApkManifest.packageName
                    this.appSize = xApkFile.length()
                    this.versionCode = StringUtils.parseInt(xApkManifest.versionCode) ?: 0
                    this.versionName = xApkManifest.versionName
                    this.lastModified = xApkFile.lastModified()
                    this.path = xApkFile.absolutePath
                }
                this.sortPosition = this.xApkInfo?.lastModified ?: 0L
            }
        }
        return apkAssetBean
    }
}
