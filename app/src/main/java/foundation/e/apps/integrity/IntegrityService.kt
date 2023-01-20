package foundation.e.apps.integrity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aurora.gplayapi.data.models.AuthData
import com.google.android.gms.droidguard.internal.DroidGuardResultsRequest
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.api.gplay.GPlayAPIRepository
import foundation.e.apps.login.LoginDataStore
import kotlinx.coroutines.launch
import org.microg.gms.common.api.ConnectionCallbacks
import org.microg.gms.common.api.OnConnectionFailedListener
import org.microg.gms.droidguard.DroidGuardApiClient
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
    private lateinit var handler: Handler
    private lateinit var thread: HandlerThread

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            fetchAuthData()
        }

        thread = HandlerThread("tokenThread")
        thread.start()
        handler = Handler(Looper.getMainLooper())
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)

        authData?.let {
            return IntegrityBinder(applicationContext, lifecycleScope, it, gPlayAPIRepository, handler)
        } ?: return null
    }

    private suspend fun fetchAuthData() {
        dataStoreModule.authData.collect {
            authData = gson.fromJson(it, AuthData::class.java)
        }
    }
}