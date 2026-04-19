package com.jarvis.input

interface WakeWordEngine {
    fun start(keyword: String)
    fun stop()
}
