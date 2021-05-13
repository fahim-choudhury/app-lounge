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

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import foundation.e.apps.application.model.InstallerInterface
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipFile

object XApkInstallUtils {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    enum class InstallError {
        SplitApksError,
        ObbError,
        ApkError,
        LowerVersionError,
        LowerSdkError,
    }

    @MainThread
    fun installXApk(xApkFile: File, callback: InstallerInterface, xApkInstallProgressCallback: XApkInstallProgressCallback?) {
        Thread(Runnable {
            var zipFile: ZipFile? = null
            try {
                handler.post {
                    xApkInstallProgressCallback?.onStart()
                }
                parseXApkZipFile(xApkFile)?.apply {
                    zipFile = this
                    getXApkManifest(this)?.apply {
                        if (this.xApkVersion < 2) {
                            handler.post {
                                xApkInstallProgressCallback?.onError(InstallError.LowerVersionError)
                            }
                            return@Runnable
                        }
                        if (this.useObbs()) {
                            installXApkObb(zipFile!!, this, xApkInstallProgressCallback)
                        }
                        if (this.useSplitApks()) {
                            installSplitApks(xApkFile, zipFile!!,callback, this, xApkInstallProgressCallback)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    zipFile?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }).start()
    }

    interface XApkInstallProgressCallback {
        @MainThread
        fun onStart()

        @MainThread
        fun onObbProgress(currentOffset: Long, totalLength: Long, percent: Int)

        @MainThread
        fun onApkProgress(currentOffset: Long, totalLength: Long, percent: Int)

        @MainThread
        fun onCompedApk(apkFile: File)

        @MainThread
        fun onCompedApks(apksBean: ApksBean, callback: InstallerInterface)

        @MainThread
        fun onError(installError: InstallError)
    }

    fun getXApkManifest(xApkFile: File): XApkManifest? {
        var xApkManifest: XApkManifest? = null
        parseXApkZipFile(xApkFile)?.apply {
            xApkManifest = getXApkManifest(this)
            this.close()
        }
        return xApkManifest
    }


    @WorkerThread
         fun parseXApkZipFile(xApkFile: File): ZipFile? {
        var zipFile: ZipFile? = null
        if (FsUtils.exists(xApkFile)) {
            try {
                zipFile = ZipFile(xApkFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return zipFile
    }



    @WorkerThread
     private fun getXApkManifest(zipFile: ZipFile): XApkManifest? {
        var xApkManifest: XApkManifest? = null
        getZipFileInputStream(zipFile, "manifest.json")?.let {
            xApkManifest = JsonUtils.objectFromJson(InputStreamReader(it, "UTF-8"), XApkManifest::class.java)
        }
        return xApkManifest
    }

    private fun installXApkObb(zipFile: ZipFile, xApkManifest: XApkManifest,
                               xApkInstallProgressCallback: XApkInstallProgressCallback?){
        var obbSuccess = false
        if (xApkManifest.useObbs()) {
            val obbTotalSize = getXApkObbTotalSize(zipFile, xApkManifest)
            for (item in xApkManifest.expansionList!!) {
                val inputStream = getZipFileInputStream(zipFile, item.xFile, true)!!
                val obbFile = File(FsUtils.getStorageDir(), item.installPath)
                if (obbFile.parentFile?.exists() == false) {
                    obbFile.parentFile?.mkdirs()
                }
                obbSuccess = FileWriterUtils.writeFileFromIS(
                    obbFile,
                    inputStream,
                    object : FileWriterUtils.FileWriterProgressCallback {
                        var percent = 0
                        override fun onProgress(currentOffset: Long) {
                            val percent1 = FormatUtils.formatPercent(currentOffset, obbTotalSize)
                            if (percent1 > percent) {
                                percent = percent1
                                handler.post {
                                    xApkInstallProgressCallback?.onObbProgress(currentOffset, obbTotalSize, percent)
                                }
                            }
                        }
                    })
            }
            if (!obbSuccess){
                xApkInstallProgressCallback?.onError(InstallError.ObbError)
            }
        }
    }
    private fun installSplitApks(xApkFile: File, zipFile: ZipFile, callback: InstallerInterface,
                                 xApkManifest: XApkManifest,
                                 xApkInstallProgressCallback: XApkInstallProgressCallback?){
        val fileList= arrayListOf<String>()
        if (xApkManifest.useSplitApks()){
            val totalLength = getXApkTotalSize(zipFile, xApkManifest)
            var percent = 0
            var currentTotal = 0L
            xApkManifest.XSplitApks?.forEach {
                var singFileOffset = 0L
                getZipFileInputStream(zipFile, it.fileName)?.apply {
                    val tempApk = File(AppFolder.getXApkInstallTempFolder(xApkManifest.packageName), it.fileName)
                    val isApkSuccess = FileWriterUtils.writeFileFromIS(tempApk, this, object : FileWriterUtils.FileWriterProgressCallback {
                        override fun onProgress(currentOffset: Long) {
                            val updateOffset = currentOffset - singFileOffset
                            singFileOffset = currentOffset
                            currentTotal += updateOffset
                            val percent1 = FormatUtils.formatPercent(currentTotal, totalLength)
                            if (percent1 > percent) {
                                percent = percent1
                                handler.post {
                                    xApkInstallProgressCallback?.onApkProgress(currentTotal,totalLength,percent)
                                }
                            }
                        }
                    })
                    if (isApkSuccess && FsUtils.exists(tempApk)) {
                        fileList.add(tempApk.absolutePath)
                    }
                }
            }
            if (fileList.isNotEmpty()) {
                handler.post {
                    xApkInstallProgressCallback?.onCompedApks(ApksBean().apply {
                        this.label = xApkManifest.label
                        this.packageName = xApkManifest.packageName
                        this.splitApkPaths = fileList
                        this.outputFileDir = AppFolder.getXApkInstallTempFolder(packageName).absolutePath
                        this.iconPath = xApkFile.absolutePath
                        this.apkAssetType = ApkAssetType.XAPK
                    },callback)
                }
            } else {
                handler.post {
                    xApkInstallProgressCallback?.onError(InstallError.SplitApksError)
                }
            }
        }
    }


    @WorkerThread
    private fun getZipFileInputStream(zipFile: ZipFile, inputName: String, isRaw: Boolean = false): InputStream? {
        var inputStream: InputStream? = null
        try {
            zipFile.getEntry(inputName)?.apply {
                inputStream = if (isRaw) {
                    zipFile.getInputStream(this)
                } else {
                    zipFile.getInputStream(this)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return inputStream
    }

    private fun getXApkTotalSize(zipFile: ZipFile, xApkManifest: XApkManifest): Long {
        return if (xApkManifest.useSplitApks()) {
            var totalLength = 0L
            xApkManifest.XSplitApks?.forEach {
                totalLength += zipFile.getEntry(it.fileName)?.compressedSize ?: 0L
            }
            totalLength
        } else {
            val apkFileName = "${xApkManifest.packageName}.apk"
            zipFile.getEntry(apkFileName).size
        }
    }

    private fun getXApkObbTotalSize(zipFile: ZipFile, xApkManifest: XApkManifest): Long {
        return if (xApkManifest.useObbs()) {
            var totalLength = 0L
            for (item in xApkManifest.expansionList!!) {
                totalLength += zipFile.getEntry(item.xFile)?.size ?: 0L
            }
            totalLength
        } else {
            0L
        }
    }
}


