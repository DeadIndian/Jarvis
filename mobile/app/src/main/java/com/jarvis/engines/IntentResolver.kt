package com.jarvis.engines

interface IntentResolver {
    fun normalize(input: String): String
}
