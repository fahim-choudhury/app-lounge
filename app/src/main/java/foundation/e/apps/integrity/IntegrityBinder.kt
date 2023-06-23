package foundation.e.apps.integrity

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.aurora.gplayapi.DroidGuardIntegrityRequest
import com.aurora.gplayapi.IntegrityPackage
import com.aurora.gplayapi.PackageVersionCode
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.utils.asProtoTimestamp
import com.google.android.gms.droidguard.DroidGuard
import com.google.android.gms.droidguard.DroidGuardClient
import com.google.android.gms.droidguard.internal.DroidGuardResultsRequest
import foundation.e.apps.IAppLoungeIntegrityService
import foundation.e.apps.IAppLoungeIntegrityServiceCallback
import foundation.e.apps.api.gplay.GPlayAPIRepository
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest

const val BASE64_ENCODING_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP // = 10

class IntegrityBinder(
    private val context: Context,
    private val lifecycleCoroutineScope: LifecycleCoroutineScope,
    private val authData: AuthData,
    private val gPlayAPIRepository: GPlayAPIRepository,
) : IAppLoungeIntegrityService.Stub() {

    companion object {
        const val TAG = "IntegrityBinder"
        const val INTEGRITY_ERROR_NETWORK_ERROR = -3
    }

    override fun checkIntegrity(
        packageName: String,
        nonce: String,
        droidGuardToken: String,
        callback: IAppLoungeIntegrityServiceCallback
    ) {
        Timber.tag(TAG).i("checkIntegrity")
        lifecycleCoroutineScope.launch {
            gPlayAPIRepository.checkIntegrity(
                authData,
                packageName,
                nonce,
                droidGuardToken
            ).let {
                if (it == null) {
                    callback.onError(INTEGRITY_ERROR_NETWORK_ERROR)
                } else {
                    callback.onSuccess(it)
                }
            }
        }
    }
}