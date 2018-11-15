package io.eelo.appinstaller.categories.model

import android.arch.lifecycle.MutableLiveData
import android.content.Context
import io.eelo.appinstaller.utils.Common
import io.eelo.appinstaller.utils.Common.EXECUTOR
import io.eelo.appinstaller.utils.Constants

class CategoriesModel : CategoriesModelInterface {
    val applicationsCategoriesList = MutableLiveData<ArrayList<Category>>()
    val gamesCategoriesList = MutableLiveData<ArrayList<Category>>()
    var screenError = MutableLiveData<Int>()

    init {
        if (applicationsCategoriesList.value == null) {
            applicationsCategoriesList.value = ArrayList()
        }
        if (gamesCategoriesList.value == null) {
            gamesCategoriesList.value = ArrayList()
        }
    }

    override fun loadCategories(context: Context) {
        if (Common.isNetworkAvailable(context)) {
            Loader(this).executeOnExecutor(EXECUTOR)
        } else {
            screenError.value = Constants.ERROR_NO_INTERNET
        }
    }
}
