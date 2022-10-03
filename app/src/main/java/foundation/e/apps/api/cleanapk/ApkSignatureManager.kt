/*
 *  Copyright (C) 2022  ECORP
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.api.cleanapk

import android.content.Context
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.security.Security

object ApkSignatureManager {
    fun verifyFdroidSignature(context: Context, apkFilePath: String, signature: String): Boolean {
        Security.addProvider(BouncyCastleProvider())
        try {
            return verifyAPKSignature(
                BufferedInputStream(FileInputStream(apkFilePath)),
                signature.byteInputStream(Charsets.UTF_8),
                context.assets.open("f-droid.org-signing-key.gpg")
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
        return false
    }

    private fun verifyAPKSignature(
        apkInputStream: BufferedInputStream,
        apkSignatureInputStream: InputStream,
        publicKeyInputStream: InputStream
    ): Boolean {
        try {
            val signature = extractSignature(apkSignatureInputStream)
            val pgpPublicKeyRingCollection =
                PGPPublicKeyRingCollection(
                    PGPUtil.getDecoderStream(publicKeyInputStream),
                    JcaKeyFingerprintCalculator()
                )

            val key = pgpPublicKeyRingCollection.getPublicKey(signature.keyID)
            signature.init(BcPGPContentVerifierBuilderProvider(), key)
            updateSignature(apkInputStream, signature)
            return signature.verify()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            apkInputStream.close()
            apkSignatureInputStream.close()
            publicKeyInputStream.close()
        }

        return false
    }

    private fun extractSignature(apkSignatureInputStream: InputStream): PGPSignature {
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
        val signature = pgpSignatureList.get(0)
        return signature
    }

    private fun updateSignature(
        apkInputStream: BufferedInputStream,
        signature: PGPSignature
    ) {
        val buff = ByteArray(1024)
        var read = apkInputStream.read(buff)
        while (read != -1) {
            signature.update(buff, 0, read)
            read = apkInputStream.read(buff)
        }
    }
}
