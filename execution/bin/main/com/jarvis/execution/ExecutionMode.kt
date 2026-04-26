package com.jarvis.execution

sealed class ExecutionMode {
    data object LOCAL_FAST : ExecutionMode()
    data object SERVER_AGENT : ExecutionMode()
}
