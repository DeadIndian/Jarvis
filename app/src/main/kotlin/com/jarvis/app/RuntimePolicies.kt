package com.jarvis.app

import com.jarvis.core.JarvisState

enum class RuntimeServiceAction {
    START,
    STOP,
    NONE
}

enum class VoiceInputAction {
    START_LISTENING,
    REQUEST_PERMISSION,
    PERMISSION_DENIED
}

object RuntimeServicePolicy {
    fun decide(previous: JarvisState?, current: JarvisState): RuntimeServiceAction {
        if (previous == current) {
            return RuntimeServiceAction.NONE
        }
        return if (current == JarvisState.IDLE) {
            RuntimeServiceAction.STOP
        } else {
            RuntimeServiceAction.START
        }
    }
}

object VoiceInputPolicy {
    fun onVoiceButtonTapped(hasMicrophonePermission: Boolean): VoiceInputAction {
        return if (hasMicrophonePermission) {
            VoiceInputAction.START_LISTENING
        } else {
            VoiceInputAction.REQUEST_PERMISSION
        }
    }

    fun onPermissionResult(granted: Boolean): VoiceInputAction {
        return if (granted) {
            VoiceInputAction.START_LISTENING
        } else {
            VoiceInputAction.PERMISSION_DENIED
        }
    }
}