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

package foundation.e.apps.application.model

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import foundation.e.apps.MainActivity.Companion.mActivity
import foundation.e.apps.xapk.XAPKFile
import foundation.e.apps.api.AppDetailRequest
import foundation.e.apps.api.AppDownloadedRequest
import foundation.e.apps.api.PackageNameSearchRequest
import foundation.e.apps.application.model.State.*
import foundation.e.apps.application.model.data.*
import foundation.e.apps.application.model.release.ReleaseData
import foundation.e.apps.applicationmanager.ApplicationManager
import foundation.e.apps.pwa.PwaInstaller
import foundation.e.apps.utils.*
import java.util.concurrent.atomic.AtomicInteger

class Application(val packageName: String, private val applicationManager: ApplicationManager) :
        DownloaderInterface, InstallerInterface {


    private val uses = AtomicInteger(0)
    private val info = ApplicationInfo(packageName)
    private val stateManager = StateManager(info, this, applicationManager)
    var basicData: BasicData? = null
    var releaseData: ReleaseData? = null
    var fullData: FullData? = null
    var pwabasicdata: PwasBasicData? = null
    var pwaFullData: PwaFullData? = null
    var searchAppsBasicData: SearchAppsBasicData? = null


    fun addListener(listener: ApplicationStateListener) {
        stateManager.addListener(listener)
    }

    fun removeListener(listener: ApplicationStateListener) {
        stateManager.removeListener(listener)
    }

    val state: State
        get() = stateManager.state
    var downloader: Downloader? = null
    private val blocker = Object()
    var isInstalling = false

    fun incrementUses() {
        uses.incrementAndGet()
    }

    fun decrementUses() {
        uses.decrementAndGet()
        applicationManager.tryRemove(this)
    }

    fun checkForStateUpdate(context: Context) {
        if (basicData != null) {
            stateManager.find(context, basicData!!)
        } else if (searchAppsBasicData != null) {
            if (searchAppsBasicData!!.is_pwa) {
            } else {
                stateManager.searchAppsFind(context, searchAppsBasicData!!)
            }
        } else if (pwabasicdata != null) {
        }
    }

    private fun checkForSystemAppStateUpdate(context: Context) {
        if (basicData != null) {
            stateManager.findSystemApp(context, basicData!!)
        }
    }

    fun pwaInstall(context: Context) {
        var error: Error?

        Thread(Runnable {
            error = assertFullData(context)

            mActivity.runOnUiThread(Runnable {
                run {

                    if (error == null) {
                        val intent = Intent(context, PwaInstaller::class.java)
                        intent.putExtra("NAME", pwaFullData!!.name)
                        intent.putExtra("URL", pwaFullData!!.url)
                        context.startActivity(intent)
                    } else {
                        stateManager.notifyError(error!!)
                    }
                }
            })

        }).start()

    }


    @Synchronized
    fun buttonClicked(context: Context, activity: Activity?) {
        when (stateManager.state) {
            INSTALLED -> info.launch(context)
            NOT_UPDATED, NOT_DOWNLOADED -> {
                if (activity != null) {
                    if (canWriteStorage(activity)) {
                        applicationManager.install(context, this)
                    }
                } else {
                    applicationManager.install(context, this)
                }
            }
            DOWNLOADING -> {
                if (!isInstalling) {
                    if (downloader != null) {
                        downloader?.cancelDownload()
                    } else {
                        onDownloadComplete(context, DownloadManager.STATUS_FAILED)
                    }
                }
                return
            }
            else ->
                return
        }
        checkForStateUpdate(context)
    }

    private fun canWriteStorage(activity: Activity): Boolean {
        return if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                        Constants.STORAGE_PERMISSION_REQUEST_CODE)
            false
        } else {
            true
        }
    }

    fun download(context: Context) {
        if (basicData?.name == Constants.MICROG) {
            downloader = Downloader(info, SystemAppDataSource.getFullData(), this)
            stateManager.notifyDownloading(downloader!!)
            downloader!!.downloadSystemApp(context)
            synchronized(blocker) {
                blocker.wait()
            }
        } else {
            val error = assertFullData(context)
            if (error == null) {
                if (isAPKArchCompatible()) {
                    downloader = Downloader(info, fullData!!, this)
                    stateManager.notifyDownloading(downloader!!)
                    downloader!!.download(context)
                    synchronized(blocker) {
                        blocker.wait()
                    }
                } else {
                    stateManager.notifyError(Error.APK_INCOMPATIBLE)
                    onDownloadComplete(context, DownloadManager.STATUS_FAILED)
                }
            } else {
                stateManager.notifyError(error)
                onDownloadComplete(context, DownloadManager.STATUS_FAILED)
            }
        }
    }

    private fun isAPKArchCompatible(): Boolean {
        val apkArchitecture: String? = fullData!!.getLastVersion()?.apkArchitecture
        return if (apkArchitecture != null) {
            if (apkArchitecture == "universal" || apkArchitecture == "noarch") {
                true
            } else {
                android.os.Build.SUPPORTED_ABIS.toList().contains(apkArchitecture)
            }
        } else {
            true
        }
    }

    override fun onDownloadComplete(context: Context, status: Int) {
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            if (basicData?.packageName == Constants.MICROG_PACKAGE) {
                installSystemApp(context)
            } else {
                Execute({
                    AppDownloadedRequest(basicData!!.id,fullData!!.getLastVersion()?.apkArchitecture).request()
                }, {})
                if(info.isXapk(fullData!!)){
                    isInstalling=true
                    XAPKFile(info.getxApkFile(context,basicData!!),this)
                }
                else {
                    install(context)
                }
            }
        } else {
            synchronized(blocker) {
                blocker.notify()
            }
            if (basicData != null) {
                info.getApkFile(context, basicData!!).delete()
                applicationManager.stopInstalling(context, this)
            } else {
                applicationManager.stopInstalling(context, this)
            }
        }
        downloader = null
    }

    private fun install(context: Context) {
        isInstalling = true
        checkForStateUpdate(context)
        info.install(context, basicData!!, this)
    }

    private fun installSystemApp(context: Context) {
        isInstalling = true
        checkForSystemAppStateUpdate(context)
        info.install(context, basicData!!, this)
    }

    override fun onInstallationComplete(context: Context) {
        synchronized(blocker) {
            blocker.notify()
        }
        info.getApkFile(context, basicData!!).delete()
        isInstalling = false
    }

    fun isUsed(): Boolean {
        return uses.get() != 0
    }

    fun assertBasicData(context: Context): Error? {
        if (basicData != null) {
            return null
        }
        return findBasicData(context)
    }

    fun assertFullData(context: Context): Error? {
        if (fullData != null) {
            return null
        } else if (pwabasicdata != null) {
            return findPwaFullData(context)
        } else if (searchAppsBasicData != null) {
            if (searchAppsBasicData!!.is_pwa) {
                return findSearchResultPwaFulldata(context)
            } else {
                findSearchAppsFullData(context)
            }
        }
        return findFullData(context)
    }

    private fun findBasicData(context: Context): Error? {
        var error: Error? = null
        if (Common.isNetworkAvailable(context)) {
            PackageNameSearchRequest(packageName).request { applicationError, searchResult ->
                when (applicationError) {
                    null -> {
                        error = Error.NO_RESULTS
                        searchResult!!.findOneAppData(packageName)?.let {
                            update(it, context)
                            error = null
                        }
                    }
                    else -> {
                        error = applicationError
                    }
                }
            }
        } else {
            error = Error.NO_INTERNET
        }
        return error
    }

    private fun findFullData(context: Context): Error? {
        if (basicData == null) {
            val error = findBasicData(context)
            if (error != null) {
                return error
            }
        }
        var error: Error? = null
        if (Common.isNetworkAvailable(context)) {
            AppDetailRequest(basicData!!.id).request { applicationError, fullData ->
                when (applicationError) {
                    null -> {
                        error = Error.NO_RESULTS
                        fullData!!.let {
                            update(fullData, context)
                            error = null
                        }
                    }
                    else -> {
                        error = applicationError
                    }
                }
            }
        } else {
            error = Error.NO_INTERNET
        }
        return error
    }

    private fun findSearchAppsFullData(context: Context): Error? {
        if (searchAppsBasicData == null) {
            val error = findBasicData(context)
            if (error != null) {
                return error
            }
        }
        var error: Error? = null
        if (Common.isNetworkAvailable(context)) {
            AppDetailRequest(searchAppsBasicData!!.id).request { applicationError, fullData ->
                when (applicationError) {
                    null -> {
                        error = Error.NO_RESULTS
                        fullData!!.let {
                            update(fullData, context)
                            error = null
                        }
                    }
                    else -> {
                        error = applicationError
                    }
                }
            }
        } else {
            error = Error.NO_INTERNET
        }
        return error
    }


    private fun findPwaFullData(context: Context): Error? {
        if (pwabasicdata == null) {
            val error = findBasicData(context)
            if (error != null) {
                return error
            }
        }
        var error: Error? = null
        if (Common.isNetworkAvailable(context)) {
            AppDetailRequest(pwabasicdata!!.id).Pwarequest { applicationError, PwaFullData ->
                when (applicationError) {
                    null -> {
                        error = Error.NO_RESULTS
                        PwaFullData!!.let {
                            Pwaupdate(PwaFullData, context)
                            error = null
                        }
                    }
                    else -> {
                        error = applicationError
                    }
                }
            }
        } else {
            error = Error.NO_INTERNET
        }
        return error
    }

    private fun findSearchResultPwaFulldata(context: Context): Error? {
        if (searchAppsBasicData == null) {
            val error = findBasicData(context)
            if (error != null) {
                return error
            }
        }
        var error: Error? = null
        if (Common.isNetworkAvailable(context)) {
            AppDetailRequest(searchAppsBasicData!!.id).Pwarequest { applicationError, PwaFullData ->
                when (applicationError) {
                    null -> {
                        error = Error.NO_RESULTS
                        PwaFullData!!.let {
                            Pwaupdate(PwaFullData, context)
                            error = null
                        }
                    }
                    else -> {
                        error = applicationError
                    }
                }
            }
        } else {
            error = Error.NO_INTERNET
        }
        return error
    }


    fun loadIcon(iconLoaderCallback: BasicData.IconLoaderCallback) {
        basicData?.loadIconAsync(this, iconLoaderCallback)
    }

    fun loadSystemIcon(iconLoaderCallback: BasicData.IconLoaderCallback) {
        basicData?.loadSystemAppIconAsync(this, iconLoaderCallback)
    }

    fun PwaloadIcon(iconLoaderCallback: PwasBasicData.IconLoaderCallback) {
        pwabasicdata?.loadIconAsync(this, iconLoaderCallback)
    }

    fun SearchAppsloadIcon(iconLoaderCallback: BasicData.IconLoaderCallback) {
        searchAppsBasicData?.loadIconAsync(this, iconLoaderCallback)
    }

    fun update(basicData: BasicData, context: Context) {
        this.basicData?.let { basicData.updateLoadedImages(it) }
        this.basicData = basicData
        checkForStateUpdate(context)
    }

    fun searchUpdate(basicData: SearchAppsBasicData, context: Context) {
        this.searchAppsBasicData?.let { basicData.updateLoadedImages(it) }
        this.searchAppsBasicData = basicData
        checkForStateUpdate(context)
    }

    fun Pwaupdate(basicData: PwasBasicData, context: Context) {
        this.pwabasicdata?.let { basicData.updateLoadedImages(it) }
        this.pwabasicdata = basicData
        checkForStateUpdate(context)
    }

    fun update(fullData: FullData, context: Context) {
        this.fullData = fullData
        update(fullData.basicData, context)
        fullData.basicData = basicData!!
    }

    fun Pwaupdate(pwaFullData: PwaFullData, context: Context) {
        this.pwaFullData = pwaFullData
        Pwaupdate(pwaFullData.pwabasicdata, context)
        pwaFullData.pwabasicdata = pwabasicdata!!
    }
}
