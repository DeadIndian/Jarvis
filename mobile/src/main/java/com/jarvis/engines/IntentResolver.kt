package com.jarvis.engines

interface IntentResolver {
    fun resolve(input: String): String?
}