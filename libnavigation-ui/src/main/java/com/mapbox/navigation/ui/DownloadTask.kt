package com.mapbox.navigation.ui

import android.os.AsyncTask
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import okhttp3.ResponseBody
import timber.log.Timber

/**
 * This class is an [AsyncTask] that downloads a file from a [ResponseBody].
 * Creates a DownloadTask which will download the input stream into the given
 * destinationDirectory with the specified file name and file extension. If a listener is passed
 *
 * @param destDirectory path to the directory where file should be downloaded
 * @param fileName name to name the file
 * @param extension file extension of the resulting file
 * @param downloadListener listener to be updated on completion of the task
 */
class DownloadTask
@JvmOverloads
constructor(
    private val destDirectory: String,
    private val fileName: String = "",
    private val extension: String,
    private val downloadListener: DownloadListener
) : AsyncTask<ResponseBody, Void, File>() {

    companion object {
        private var uniqueId = 0
        private const val BUFFER_SIZE = 4096
    }

    /**
     * It performs computation on the background thread. The computation works towards
     * downloading file from ResponseBody
     *
     * @param responseBodies used to download the file
     * @return File object as an output to UI thread
     */
    override fun doInBackground(vararg responseBodies: ResponseBody): File? =
        saveAsFile(responseBodies.firstOrNull())

    /**
     * Saves the file returned in the response body as a file in the cache directory
     *
     * @param responseBody containing file
     * @return resulting file, or null if there were any IO exceptions
     */
    private fun saveAsFile(responseBody: ResponseBody?): File? {
        if (responseBody == null) {
            return null
        }

        try {
            val filePath = StringBuilder().append(destDirectory)
                    .append(File.separator)
                    .append(fileName)
                    .append(retrieveUniqueId())
                    .append(".")
                    .append(extension)
                    .toString()
            val file = File(filePath)
            val inputStream: InputStream = responseBody.byteStream()
            val outputStream: OutputStream = FileOutputStream(file)
            val buffer = ByteArray(BUFFER_SIZE)

            inputStream.use { input ->
                outputStream.use { fileOut ->
                    while (true) {
                        val length = input.read(buffer)
                        if (length <= 0)
                            break
                        fileOut.write(buffer, 0, length)
                    }
                    fileOut.flush()
                }
            }
            return file
        } catch (e: FileNotFoundException) {
            Timber.e(e, "There was an exception during saveAsFile.")
            return null
        }
    }

    private fun retrieveUniqueId(): String =
        if (++uniqueId > 0) uniqueId.toString() else ""

    /**
     * Invoked to pass the results of [doInBackground] to UI thread
     *
     * @param instructionFile output from [doInBackground]
     */
    override fun onPostExecute(instructionFile: File?) {
        if (instructionFile == null) {
            downloadListener.onErrorDownloading()
        } else {
            downloadListener.onFinishedDownloading(instructionFile)
        }
    }

    /**
     * Interface which allows a Listener to be updated upon the completion of a [DownloadTask].
     */
    interface DownloadListener {
        /**
         * Notifies the user when the downloadTask has finished downloading
         *
         * @param file the file downloaded
         */
        fun onFinishedDownloading(file: File)

        /**
         * Notifies the user if there is error downloading the file
         */
        fun onErrorDownloading()
    }
}
