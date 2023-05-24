package foundation.e.apps.data.fusedDownload

import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object FileManager {
    private const val TAG = "FileManager"

    fun moveFile(inputPath: String, inputFile: String, outputPath: String) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {

            // create output directory if it doesn't exist
            val dir = File(outputPath)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            inputStream = FileInputStream(inputPath + inputFile)
            outputStream = FileOutputStream(outputPath + inputFile)
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            // write the output file
            outputStream.flush()
            // delete the original file
            File(inputPath + inputFile).delete()
        } catch (e: FileNotFoundException) {
            Timber.e(e.stackTraceToString())
        } catch (e: Exception) {
            Timber.e(e.stackTraceToString())
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }
}
