package com.jarvis.app.ui

enum class MainTab(val route: String, val title: String) {
    HOME(route = "home", title = "Home"),
    HELP(route = "help", title = "Help"),
    LOGS(route = "logs", title = "Logs"),
    SETTINGS(route = "settings", title = "Settings");

    companion object {
        fun fromRoute(route: String?): MainTab {
            return entries.firstOrNull { it.route.equals(route?.trim(), ignoreCase = true) } ?: HOME
        }
    }
}
