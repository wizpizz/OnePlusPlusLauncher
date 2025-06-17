package com.wizpizz.onepluspluslauncher.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.wizpizz.onepluspluslauncher.hook.features.*

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {

    override fun onHook() = encase {
        configs {
            debugLog {
                tag = "OPPLauncherHook"
            }
        }

        loadApp(name = "com.android.launcher") {
            // Load all feature hooks
            SwipeUpAutoFocusHook.apply(this)
            EnterKeyLaunchHook.apply(this)
            FuzzySearchHook.apply(this)
            GlobalSearchRedirectHook.apply(this)
            SwipeDownSearchRedirectHook.apply(this)
        }
    }
}