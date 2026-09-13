package com.jarvis.app.core.model

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class DownloadState(
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: Status
) {
    enum class Status { PENDING, DOWNLOADING, SUCCESS, FAILED }
}

interface ModelDownloadCallback {
    fun onProgress(state: DownloadState)
    fun onComplete(file: File?)
}

class ModelDownloader(private val context: Context) {

    private val tag = "ModelDownloader"
    private val executor = Executors.newSingleThreadExecutor()
    private var activeJob: Thread? = null

    companion object {
        const val MODEL_FILENAME = "mobile-actions_q8_ekv1024.litertlm"
        const val DEFAULT_URL = "https://huggingface.co/JackJ1/functiongemma-270m-it-mobile-actions-litertlm/resolve/main/mobile-actions_q8_ekv1024.litertlm?download=true"
    }

    val modelFile: File
        get() = File(context.filesDir, MODEL_FILENAME)

    val isDownloaded: Boolean
        get() = modelFile.exists() && modelFile.length() > 0

    fun getModelPath(): String? = if (isDownloaded) modelFile.absolutePath else null

    fun download(
        url: String = DEFAULT_URL,
        callback: ModelDownloadCallback
    ) = downloadTo(url, MODEL_FILENAME, callback)

    /** Downloads [url] into filesDir/[fileName]. Used for user-added tool models. */
    fun downloadTo(
        url: String,
        fileName: String,
        callback: ModelDownloadCallback
    ) {
        val target = File(context.filesDir, fileName)
        if (target.exists() && target.length() > 0) {
            callback.onComplete(target)
            return
        }

        activeJob?.let { if (it.isAlive) return }

        callback.onProgress(DownloadState(0, 0L, -1L, DownloadState.Status.DOWNLOADING))

        val job = Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "Jarvis/1.0")
                }

                if (conn.responseCode != 200) {
                    callback.onComplete(null)
                    return@Thread
                }

                val total = conn.contentLength.toLong()
                val tmpFile = File(context.filesDir, "$fileName.tmp")
                var downloaded = 0L

                FileOutputStream(tmpFile).use { out ->
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0 && downloaded % (total / 20.coerceAtLeast(1)) == 0L) {
                                val progress = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                callback.onProgress(
                                    DownloadState(progress, downloaded, total, DownloadState.Status.DOWNLOADING)
                                )
                            }
                        }
                    }
                }

                if (tmpFile.renameTo(target)) {
                    callback.onProgress(
                        DownloadState(100, downloaded, total, DownloadState.Status.SUCCESS)
                    )
                    callback.onComplete(target)
                    Log.i(tag, "Model downloaded to ${target.absolutePath}")
                } else {
                    tmpFile.delete()
                    callback.onProgress(
                        DownloadState(0, 0L, total, DownloadState.Status.FAILED)
                    )
                    callback.onComplete(null)
                }
            } catch (e: Exception) {
                Log.e(tag, "Download failed: ${e.message}")
                callback.onProgress(
                    DownloadState(0, 0L, -1L, DownloadState.Status.FAILED)
                )
                callback.onComplete(null)
            }
        }

        activeJob = job
        executor.execute(job)
    }

    fun cancel() {
        activeJob?.interrupt()
        activeJob = null
    }
}
