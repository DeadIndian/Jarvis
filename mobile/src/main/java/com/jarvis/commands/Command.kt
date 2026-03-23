package com.jarvis.commands

data class Command(
    val id: String,
    val description: String?,
    val activatesOn: List<String>,
    val handler: String,
    val guards: Map<String, Any>? = null,
    val response: Map<String, String>? = null
)