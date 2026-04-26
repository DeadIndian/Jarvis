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

enum class WakeWordAction {
    START,
    STOP,
    NONE
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

object WakeWordPolicy {
    fun decide(previous: JarvisState?, current: JarvisState): WakeWordAction {
        if (previous == current) {
            return WakeWordAction.NONE
        }
        // Wake-word is no longer required for user flow; voice capture is started explicitly.
        return WakeWordAction.STOP
    }
}
