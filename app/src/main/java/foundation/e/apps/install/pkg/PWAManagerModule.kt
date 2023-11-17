package foundation.e.apps.install.pkg

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.OpenForTesting
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.fusedDownload.FusedDownloadRepository
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class PWAManagerModule @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedDownloadRepository: FusedDownloadRepository,
) {

    companion object {
        private const val URL = "URL"
        private const val SHORTCUT_ID = "SHORTCUT_ID"
        private const val TITLE = "TITLE"
        private const val ICON = "ICON"

        private const val PWA_NAME = "PWA_NAME"
        private const val PWA_ID = "PWA_ID"

        private const val PWA_PLAYER = "content://foundation.e.pwaplayer.provider/pwa"
        private const val VIEW_PWA = "foundation.e.blisslauncher.VIEW_PWA"
    }

    /**
     * Fetch info from PWA Player to check if a PWA is installed.
     * The column names returned from PWA helper are: [_id, shortcutId, url, title, icon]
     * The last column ("icon") is a blob.
     * Note that there is no pwa version. Also there is no "package_name".
     *
     * In this method, we get all the available PWAs from PWA Player and compare each of their url
     * to the method argument [application]'s url. If an item (from the cursor) has url equal to
     * that of pwa app, we return [Status.INSTALLED].
     * We also set [Application.pwaPlayerDbId] for the [application].
     *
     * As there is no concept of version, we cannot send [Status.UPDATABLE].
     */
    fun getPwaStatus(application: Application): Status {
        context.contentResolver.query(
            Uri.parse(PWA_PLAYER),
            null, null, null, null
        )?.let { cursor ->
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                if (isPwaInstalled(cursor, application)) {
                    return Status.INSTALLED
                }
                cursor.moveToNext()
            }
            cursor.close()
        }

        return Status.UNAVAILABLE
    }

    private fun isPwaInstalled(
        cursor: Cursor,
        application: Application
    ): Boolean {
        try {
            val pwaItemUrl = cursor.getString(cursor.columnNames.indexOf("url"))
            val pwaItemDbId = cursor.getLong(cursor.columnNames.indexOf("_id"))
            if (application.url == pwaItemUrl) {
                application.pwaPlayerDbId = pwaItemDbId
                return true
            }
        } catch (e: Exception) {
           Timber.w(e)
        }

        return false
    }

    /**
     * Launch PWA using PWA Player.
     */
    fun launchPwa(application: Application) {
        val launchIntent = Intent(VIEW_PWA).apply {
            data = Uri.parse(application.url)
            putExtra(PWA_ID, application.pwaPlayerDbId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS)
        }
        context.startActivity(launchIntent)
    }

    suspend fun installPWAApp(fusedDownload: FusedDownload) {
        // Update status
        fusedDownload.status = Status.DOWNLOADING
        fusedDownloadRepository.updateDownload(fusedDownload)

        // Get bitmap and byteArray for icon
        val iconBitmap = getIconImageBitmap(fusedDownload.getAppIconUrl())

        if (iconBitmap == null) {
            fusedDownload.status = Status.INSTALLATION_ISSUE
            fusedDownloadRepository.updateDownload(fusedDownload)
            return
        }

        val iconByteArray = iconBitmap.toByteArray()
        val values = ContentValues()
        values.apply {
            put(URL, fusedDownload.downloadURLList[0])
            put(SHORTCUT_ID, fusedDownload.id)
            put(TITLE, fusedDownload.name)
            put(ICON, iconByteArray)
        }

        context.contentResolver.insert(Uri.parse(PWA_PLAYER), values)?.let {
            val databaseID = ContentUris.parseId(it)
            publishShortcut(fusedDownload, iconBitmap, databaseID)
        }
    }

    fun getIconImageBitmap(url: String): Bitmap? {
        return try {
            val stream = URL(url).openStream()
            BitmapFactory.decodeStream(stream)
        } catch (e: IOException) {
            Timber.e(e)
            null
        }
    }

    fun Bitmap.toByteArray(): ByteArray {
        val byteArrayOS = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOS)
        return byteArrayOS.toByteArray()
    }

    private suspend fun publishShortcut(
        fusedDownload: FusedDownload,
        bitmap: Bitmap,
        databaseID: Long
    ) {
        // Update status
        fusedDownload.status = Status.INSTALLING
        fusedDownloadRepository.updateDownload(fusedDownload)

        val intent = Intent().apply {
            action = VIEW_PWA
            data = Uri.parse(fusedDownload.downloadURLList[0])
            putExtra(PWA_NAME, fusedDownload.name)
            putExtra(PWA_ID, databaseID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS)
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(context, fusedDownload.id)
            .setShortLabel(fusedDownload.name)
            .setIcon(IconCompat.createWithBitmap(bitmap))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)

        // Add a small delay to avoid conflict of button states.
        delay(100)

        // Update status
        fusedDownload.status = Status.INSTALLED
        fusedDownloadRepository.updateDownload(fusedDownload)
        delay(500)
        fusedDownloadRepository.deleteDownload(fusedDownload)
    }
}
