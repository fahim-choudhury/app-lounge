package io.eelo.appinstaller.home.model

import android.arch.lifecycle.MutableLiveData
import android.content.Context
import io.eelo.appinstaller.application.model.Application
import io.eelo.appinstaller.application.model.InstallManager
import io.eelo.appinstaller.utils.Common

class HomeModel : HomeModelInterface {
    val applications = MutableLiveData<LinkedHashMap<String, ArrayList<Application>>>()
    val bannerApplications = MutableLiveData<ArrayList<BannerApplication>>()
    private var installManager: InstallManager? = null

    init {
        if (applications.value == null) {
            applications.value = LinkedHashMap()
        }
        if (bannerApplications.value == null) {
            bannerApplications.value = ArrayList()
        }
    }

    override fun initialise(installManager: InstallManager) {
        this.installManager = installManager
    }

    override fun getInstallManager(): InstallManager {
        return installManager!!
    }

    override fun loadCategories(context: Context) {
        ApplicationsLoader(this).executeOnExecutor(Common.EXECUTOR, context)
    }

}
