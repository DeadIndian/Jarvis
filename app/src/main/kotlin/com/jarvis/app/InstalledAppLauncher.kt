package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

class InstalledAppLauncher(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    suspend fun launch(appName: String): String {
        val target = appName.trim()
        if (target.isBlank()) {
            return "Please name an app to launch"
        }

        val candidates = packageManager.queryIntentActivities(launcherQuery, 0)
        val match = candidates.firstOrNull { info -> matches(info, target) }
            ?: return unavailableMessage(target, candidates)

        val packageName = match.activityInfo?.packageName.orEmpty()
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return "I found ${labelOf(match)} but could not open it"

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(launchIntent)
        return "Launching ${labelOf(match)}"
    }

    private fun matches(info: ResolveInfo, target: String): Boolean {
        val label = labelOf(info)
        val packageName = info.activityInfo?.packageName.orEmpty()
        val normalizedTarget = normalize(target)
        val normalizedLabel = normalize(label)
        val normalizedPackage = normalize(packageName)

        return packageName.equals(target, ignoreCase = true) ||
            label.equals(target, ignoreCase = true) ||
            normalizedLabel == normalizedTarget ||
            normalizedPackage == normalizedTarget ||
            normalizedLabel.contains(normalizedTarget) ||
            normalizedTarget.contains(normalizedLabel)
    }

    private fun unavailableMessage(target: String, candidates: List<ResolveInfo>): String {
        val suggestions = candidates
            .asSequence()
            .map(::labelOf)
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
            .joinToString(", ")

        return if (suggestions.isBlank()) {
            "I could not find an installed app named $target"
        } else {
            "I could not find an installed app named $target. Try: $suggestions"
        }
    }

    private fun labelOf(info: ResolveInfo): String {
        return info.loadLabel(packageManager).toString().trim()
    }

    private fun normalize(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]+"), "")
    }
}