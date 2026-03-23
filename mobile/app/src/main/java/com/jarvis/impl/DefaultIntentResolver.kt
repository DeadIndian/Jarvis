package com.jarvis.impl

import com.jarvis.engines.IntentResolver

class DefaultIntentResolver : IntentResolver {
    override fun normalize(input: String): String = input.trim().lowercase()
}
