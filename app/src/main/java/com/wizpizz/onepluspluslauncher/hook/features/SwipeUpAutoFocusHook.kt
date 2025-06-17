package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.LAUNCHER_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.LAUNCHER_STATE_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_SWIPE
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG

/**
 * Auto-focuses search input when swiping up to enter All Apps state
 */
object SwipeUpAutoFocusHook {
    
    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            LAUNCHER_CLASS.toClass(appClassLoader).method {
                name = "onStateSetStart"
                param(LAUNCHER_STATE_CLASS.toClass(appClassLoader))
            }.hook {
                after {
                    // Check if auto focus on swipe up is enabled
                    val autoFocusSwipeEnabled = prefs.getBoolean(PREF_AUTO_FOCUS_SEARCH_SWIPE, true)
                    if (!autoFocusSwipeEnabled) return@after
                    
                    // Check if this ALL_APPS transition is from a redirect (not a swipe)
                    if (HookUtils.isRedirectInProgress()) {
                        Log.d(TAG, "[AutoFocus] Skipping swipe focus - redirect in progress")
                        return@after
                    }
                    
                    val launcherInstance = instance
                    val targetState = args[0] ?: return@after
                    val allAppsState = LAUNCHER_STATE_CLASS.toClass(appClassLoader)
                        .field { name = "ALL_APPS" }.get().any() ?: return@after

                    if (targetState == allAppsState) {
                        Log.d(TAG, "[AutoFocus] Focusing search input for swipe-up gesture")
                        appClassLoader?.let { HookUtils.focusSearchInput(launcherInstance, it) }
                    }
                }
            }
        }
    }
} 