package com.jarvis.output

interface OutputChannel {
    fun showListening()
    fun showThinking()
    fun showSpeaking()
    fun speak(text: String)
}
