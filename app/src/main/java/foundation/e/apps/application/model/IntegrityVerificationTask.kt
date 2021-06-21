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

import android.content.Context
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import foundation.e.apps.R
import foundation.e.apps.api.FDroidAppExistsRequest
import foundation.e.apps.application.model.data.FullData
import foundation.e.apps.utils.Constants
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.Security

class IntegrityVerificationTask(
        private val applicationInfo: ApplicationInfo,
        private val fullData: FullData,
        private val integrityVerificationCallback: IntegrityVerificationCallback) :
        AsyncTask<Context, Void, Context>() {
    private var verificationSuccessful: Boolean = false

    override fun doInBackground(vararg context: Context): Context {

        if(isSystemApplication(fullData.packageName, context[0])){
            verificationSignature(context[0]);
        }
        else if (isfDroidApplication(fullData.packageName)) {
            verificationSignature(context[0]);
        }
        else{
            verificationSignature(context[0]);
        }
        return context[0]
    }

    private fun isfDroidApplication(packageName: String): Boolean {
        //implement in  vulner_3329 branch
        //https://gitlab.e.foundation/e/backlog/-/issues/3329
        var fDroidAppExistsResponse: Int = 0
        FDroidAppExistsRequest(fullData.packageName)
                .request { applicationError, searchResult ->
                    when (applicationError) {
                        null -> {
                            if (searchResult != null && searchResult.size > 0) {
                                fDroidAppExistsResponse = searchResult[0]!!
                            }
                        }
                        else -> {
                            // Log.e("TAG", "error....."+applicationError)
                        }
                    }
                }
        return fDroidAppExistsResponse==200;
    }

    private fun verificationSignature(context: Context) {
        verificationSuccessful = if (!fullData.getLastVersion()!!.apkSHA.isNullOrEmpty()) {
            getApkFileSha1(applicationInfo.getApkOrXapkFile(context, fullData, fullData.basicData)) ==
                    fullData.getLastVersion()!!.apkSHA
        } else {
            Security.addProvider(BouncyCastleProvider())
            verifyAPKSignature(
                    context,
                    BufferedInputStream(FileInputStream(
                            applicationInfo.getApkFile(context,
                                    fullData.basicData).absolutePath)),
                    fullData.getLastVersion()!!.signature.byteInputStream(Charsets.UTF_8),
                    context.assets.open("f-droid.org-signing-key.gpg"))
        }
    }

    private fun isSystemApplication(packageName: String, context: Context): Boolean {
        //implement in  vulner_3328 branch
        // https://gitlab.e.foundation/e/backlog/-/issues/3328
        return false;
    }

    override fun onPostExecute(context: Context) {
        integrityVerificationCallback.onIntegrityVerified(context, verificationSuccessful)
    }

    private fun getApkFileSha1(file: File): String? {
        val messageDigest = MessageDigest.getInstance("SHA-1")
        val fileInputStream = FileInputStream(file)
        var length = 0
        val buffer = ByteArray(8192)
        while (length != -1) {
            length = fileInputStream.read(buffer)
            if (length > 0) {
                messageDigest.update(buffer, 0, length)
            }
        }
        return byteArrayToHex(messageDigest.digest())
    }

    private fun byteArrayToHex(a: ByteArray): String? {
        val sb = StringBuilder(a.size * 2)
        for (b in a) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun verifyAPKSignature(
            context: Context,
            apkInputStream: BufferedInputStream,
            apkSignatureInputStream: InputStream,
            publicKeyInputStream: InputStream): Boolean {
        try {

            var jcaPGPObjectFactory =
                    JcaPGPObjectFactory(PGPUtil.getDecoderStream(apkSignatureInputStream))
            val pgpSignatureList: PGPSignatureList

            val pgpObject = jcaPGPObjectFactory.nextObject()
            if (pgpObject is PGPCompressedData) {
                jcaPGPObjectFactory = JcaPGPObjectFactory(pgpObject.dataStream)
                pgpSignatureList = jcaPGPObjectFactory.nextObject() as PGPSignatureList
            } else {
                pgpSignatureList = pgpObject as PGPSignatureList
            }

            val pgpPublicKeyRingCollection =
                    PGPPublicKeyRingCollection(
                            PGPUtil.getDecoderStream(publicKeyInputStream),
                            JcaKeyFingerprintCalculator())

            val signature = pgpSignatureList.get(0)
            val key = pgpPublicKeyRingCollection.getPublicKey(signature.keyID)

            signature.init(BcPGPContentVerifierBuilderProvider(), key)

            val buff = ByteArray(1024)
            var read = apkInputStream.read(buff)
            while (read != -1) {
                signature.update(buff, 0, read)
                read = apkInputStream.read(buff)
            }

            apkInputStream.close()
            apkSignatureInputStream.close()
            publicKeyInputStream.close()
            return signature.verify()
        } catch (e: Exception) {
            e.printStackTrace()

            Handler(Looper.getMainLooper()).post {
                val toast = Toast.makeText(context, context.resources.getString(R.string.Signature_verification_failed),  Toast.LENGTH_LONG)
                toast.show()
            }

        }

        return false;

    }
}

interface IntegrityVerificationCallback {
    fun onIntegrityVerified(context: Context, verificationSuccessful: Boolean)
}
