package foundation.e.apps.integrity

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.api.gplay.GPlayAPIRepository
import foundation.e.apps.login.LoginDataStore
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IntegrityService: LifecycleService() {

    companion object {
        const val TAG = "IntegrityService"
    }

    @Inject lateinit var dataStoreModule: LoginDataStore
    @Inject lateinit var gson: Gson
    @Inject lateinit var gPlayAPIRepository: GPlayAPIRepository
    private var authData: AuthData? = null

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            fetchAuthData()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)

        authData?.let {
            return IntegrityBinder(applicationContext, lifecycleScope, it, gPlayAPIRepository)
        } ?: return null
    }

    private suspend fun fetchAuthData() {
        dataStoreModule.authData.collect {
            authData = gson.fromJson(it, AuthData::class.java)
        }
    }
}