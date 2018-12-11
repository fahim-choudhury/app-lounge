package io.eelo.appinstaller.categories.category.model

import android.arch.lifecycle.MutableLiveData
import android.content.Context
import io.eelo.appinstaller.api.ListApplicationsRequest
import io.eelo.appinstaller.application.model.Application
import io.eelo.appinstaller.application.model.InstallManager
import io.eelo.appinstaller.utils.*

class CategoryModel : CategoryModelInterface {

    lateinit var installManager: InstallManager
    lateinit var category: String
    private var page = 1
    val categoryApplicationsList = MutableLiveData<ArrayList<Application>>()
    var screenError = MutableLiveData<Error>()
    private var error: Error? = null

    init {
        if (categoryApplicationsList.value == null) {
            categoryApplicationsList.value = ArrayList()
        }
    }

    override fun initialise(installManager: InstallManager, category: String) {
        this.installManager = installManager
        this.category = category
    }

    override fun loadApplications(context: Context) {
        var apps: ArrayList<Application>? = null
        if (Common.isNetworkAvailable(context)) {
            Execute({
                apps = loadApplicationsSynced(context)
            }, {
                if (error == null && apps != null) {
                    categoryApplicationsList.value = apps
                } else {
                    screenError.value = error
                }
            })
            page++
        } else {
            screenError.value = Error.NO_INTERNET
        }
    }

    private fun loadApplicationsSynced(context: Context): ArrayList<Application>? {
        var listApplications: ListApplicationsRequest.ListApplicationsResult? = null
        ListApplicationsRequest(category, page, Constants.RESULTS_PER_PAGE)
                .request { applicationError, listApplicationsResult ->
                    when (applicationError) {
                        null -> {
                            listApplications = listApplicationsResult!!
                        }
                        else -> {
                            error = applicationError
                        }
                    }
                }
        if (listApplications != null) {
            return listApplications!!.getApplications(installManager, context)
        } else {
            return null
        }
    }


}
