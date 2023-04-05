package foundation.e.apps.integrity

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.PreferenceManager
import com.aurora.gplayapi.*
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.utils.asProtoTimestamp
import com.google.android.gms.common.api.GoogleApi
import com.google.android.gms.droidguard.DroidGuard
import com.google.android.gms.droidguard.DroidGuardClient
import com.google.android.gms.droidguard.internal.DroidGuardResultsRequest
import foundation.e.apps.IAppLoungeIntegrityService
import foundation.e.apps.api.gplay.GPlayAPIRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.microg.gms.common.api.ConnectionCallbacks
import org.microg.gms.common.api.GoogleApiManager
import org.microg.gms.common.api.OnConnectionFailedListener
import org.microg.gms.common.api.ReturningGoogleApiCall
import org.microg.gms.droidguard.DroidGuardApiClient
import java.security.MessageDigest

class IntegrityBinder(
    private val context: Context,
    private val lifecycleCoroutineScope: LifecycleCoroutineScope,
    private val authData: AuthData,
    private val gPlayAPIRepository: GPlayAPIRepository,
    private val handler: Handler
) : IAppLoungeIntegrityService.Stub() {

    companion object {
        const val TAG = "IntegrityBinder"
    }

    override fun checkIntegrity(packageName: String, nonce: String) {
        requestDroidGuardToken(packageName, nonce)
    }

    private fun requestDroidGuardToken(packageName: String, nonce: String) {
        val integrityPackage = IntegrityPackage.newBuilder().setPackageName(packageName)
        val versionCode = PackageVersionCode.newBuilder().setVersion(10)
        val timestamp = System.currentTimeMillis().asProtoTimestamp()

        val digest = MessageDigest.getInstance("SHA-256")

        val data = DroidGuardIntegrityRequest.newBuilder()
            .setPackage(integrityPackage)
            .setVersion(versionCode)
            .setNonce(Base64.encodeToString(digest.digest(Base64.decode(nonce, 11)), 11))
            .setTimestamp(timestamp)
            .build()

        val client = DroidGuard.getClient(context)
        val request = DroidGuardResultsRequest()
        val map = buildDroidGuardData(data)

        for (entry in map.entries) {
            Log.i("jklee", "${entry.key}:${entry.value}")
        }

        request.bundle.putString("thirdPartyCallerAppPackageName", packageName)
        client.getResults("pia_attest", map, request).addOnSuccessListener {
            lifecycleCoroutineScope.launch {
                gPlayAPIRepository.checkIntegrity(authData, packageName, nonce, it)
            }
        }
    }

    private fun buildDroidGuardData(request: DroidGuardIntegrityRequest): Map<String, String> {
        return mapOf(
            "pkg_key" to request.`package`.packageName,
            "vc_key" to request.version.version.toString(),
            "nonce_sha256_key" to request.nonce,
            "tm_s_key" to request.timestamp.seconds.toString(),
            "binding_key" to Base64.encodeToString(request.toByteArray(), 10)
        )
    }
}