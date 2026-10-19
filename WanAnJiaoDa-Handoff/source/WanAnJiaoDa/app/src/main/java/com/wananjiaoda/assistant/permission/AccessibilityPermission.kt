package com.wananjiaoda.assistant.permission

/** Parses Settings.Secure's colon-delimited flattened component names without Android dependencies. */
object AccessibilityPermission {
    fun readAuthorization(packageName: String, className: String, readSetting: () -> String?): Boolean? = try {
        isAuthorized(readSetting(), packageName, className)
    } catch (_: RuntimeException) {
        null
    }

    fun isAuthorized(components: String?, packageName: String, className: String): Boolean =
        components.orEmpty().split(':').any { component ->
            val parts = component.trim().split('/')
            if (parts.size != 2 || parts[0] != packageName || parts[1].isEmpty()) false
            else {
                val expandedClass = if (parts[1].startsWith('.')) parts[0] + parts[1] else parts[1]
                expandedClass == className
            }
        }
}
