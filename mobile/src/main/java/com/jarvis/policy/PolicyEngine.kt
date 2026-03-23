package com.jarvis.policy

interface PolicyEngine {
    fun isActiveNow(): Boolean
    fun setMode(mode: String)
    fun getCurrentMode(): String
}