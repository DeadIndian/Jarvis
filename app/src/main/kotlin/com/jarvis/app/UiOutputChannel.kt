package com.jarvis.app

data class UiOutputChannel(
    val onState: (String) -> Unit,
    val onSpeak: (String) -> Unit
)
