package com.jarvis.android

import com.jarvis.core.JarvisState

interface JarvisRuntimeBridge {
    fun onPermissionChanged(permission: String, granted: Boolean)
    fun onStateChanged(state: JarvisState)
}
