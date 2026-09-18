package com.jarvis.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.jarvis.app.core.model.DownloadState
import com.jarvis.app.core.model.ModelDownloadCallback
import com.jarvis.app.core.model.ModelDownloader
import java.io.File

class SplashActivity : ComponentActivity() {

    private lateinit var downloader: ModelDownloader
    private var progressBar: ProgressBar? = null
    private var statusText: TextView? = null
    private var actionButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        val view = LayoutInflater.from(this)
            .inflate(R.layout.activity_splash, null)
        setContentView(view)

        progressBar = view.findViewById(R.id.progressBar)
        statusText = view.findViewById(R.id.statusText)
        actionButton = view.findViewById(R.id.actionButton)

        downloader = ModelDownloader(this)

        if (downloader.isDownloaded) {
            launchMain()
            return
        }

        actionButton?.setOnClickListener {
            startDownload()
        }

        statusText?.text = "Download on-device AI model for offline commands"
        actionButton?.text = "Download Model (~200MB)"
        startDownload()
    }

    private fun startDownload() {
        actionButton?.isEnabled = false
        downloader.download(callback = object : ModelDownloadCallback {
            override fun onProgress(state: DownloadState) {
                runOnUiThread {
                    progressBar?.progress = state.progress
                    statusText?.text = when (state.status) {
                        DownloadState.Status.DOWNLOADING ->
                            "Downloading: ${state.progress}%"
                        DownloadState.Status.SUCCESS ->
                            "Model ready!"
                        DownloadState.Status.FAILED ->
                            "Download failed. Tap to retry."
                        DownloadState.Status.PENDING ->
                            "Starting..."
                    }
                    if (state.status == DownloadState.Status.FAILED) {
                        actionButton?.isEnabled = true
                        actionButton?.text = "Retry Download"
                    }
                }
            }

            override fun onComplete(file: File?) {
                runOnUiThread {
                    if (file != null) {
                        launchMain()
                    } else {
                        // Model failed but app still works (online mode degrades gracefully)
                        launchMain()
                    }
                }
            }
        })
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloader.cancel()
    }
}
