package com.jarvis.app

import android.util.Log
import com.jarvis.logging.JarvisLogger

class AndroidLogJarvisLogger : JarvisLogger {
    override fun info(stage: String, message: String, data: Map<String, Any?>) {
        Log.i("Jarvis/$stage", "$message $data")
    }

    override fun warn(stage: String, message: String, data: Map<String, Any?>) {
        Log.w("Jarvis/$stage", "$message $data")
    }

    override fun error(stage: String, message: String, throwable: Throwable?, data: Map<String, Any?>) {
        Log.e("Jarvis/$stage", "$message $data", throwable)
    }
}
