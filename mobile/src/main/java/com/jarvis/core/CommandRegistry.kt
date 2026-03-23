package com.jarvis.core

import com.jarvis.commands.Command

interface CommandRegistry {
    fun getCommand(input: String): Command?
    fun registerCommand(command: Command)
    fun validateAll(): Boolean
}