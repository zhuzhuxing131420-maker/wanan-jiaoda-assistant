package com.wananjiaoda.assistant.automation

enum class WeChatLaunchMethod { OWNER_PROFILE, INTENT_CHOOSER }

object OrdinaryWeChatLauncher {
    fun launch(ownerProfileLaunch: () -> Unit, fallbackLaunch: () -> Unit,
        onOwnerFailure: (Exception) -> Unit = {}): WeChatLaunchMethod {
        return try {
            ownerProfileLaunch()
            WeChatLaunchMethod.OWNER_PROFILE
        } catch (error: Exception) {
            onOwnerFailure(error)
            fallbackLaunch()
            WeChatLaunchMethod.INTENT_CHOOSER
        }
    }
}
