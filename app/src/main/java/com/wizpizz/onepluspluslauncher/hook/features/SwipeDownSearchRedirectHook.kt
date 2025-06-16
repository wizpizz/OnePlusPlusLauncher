package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SWIPE_DOWN_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG

/**
 * Hook to redirect swipe down global search to app drawer
 */
object SwipeDownSearchRedirectHook {
    
    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            // Hook showSearchBar since startGlobalSearch is private
            // This method is called when global search should be shown
            "com.android.launcher.touch.WorkspacePullDownDetectController".toClassOrNull(appClassLoader)?.method {
                name = "showSearchBar"
                paramCount = 5 // Launcher, String, boolean, Bundle, boolean parameters
                returnType = BooleanType
            }?.hook {
                before {
                    if (prefs.getBoolean(PREF_SWIPE_DOWN_SEARCH_REDIRECT, true)) {
                        Log.d(TAG, "[SwipeDownSearch] Intercepting showSearchBar, redirecting to app drawer")
                        
                        // Mark redirect in progress to prevent AutoFocusHook from interfering
                        HookUtils.setRedirectInProgress(true)
                        
                        val launcher = args[0]
                        val success = launcher?.let { openAppDrawer(it) }
                        
                        if (success == true) {
                            // Clean up the pull down animation/blur overlay
                            cleanupPullDownAnimation(instance, launcher)
                            
                            // Auto focus search input if enabled
                            if (prefs.getBoolean(PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT, true)) {
                                Log.d(TAG, "[SwipeDownSearch] Auto focus enabled, focusing search input")
                                appClassLoader?.let { HookUtils.focusSearchInput(launcher, it) }
                                // Clear redirect flag after focusing
                                HookUtils.setRedirectInProgress(false)
                            } else {
                                Log.d(TAG, "[SwipeDownSearch] Auto focus disabled, clearing redirect flag")
                                // Clear redirect flag immediately if not focusing
                                HookUtils.setRedirectInProgress(false)
                            }
                            
                            // Return true to indicate success and prevent original method execution
                            result = true
                        } else {
                            Log.d(TAG, "[SwipeDownSearch] Failed to open app drawer, allowing original behavior")
                            // Clear redirect flag since we failed
                            HookUtils.setRedirectInProgress(false)
                            // Let original method execute
                        }
                    } else {
                        Log.d(TAG, "[SwipeDownSearch] Feature disabled, allowing original behavior")
                        // Let original method execute
                    }
                }
            } ?: Log.e(TAG, "[SwipeDownSearch] Failed to find WorkspacePullDownDetectController.showSearchBar method")
        }
    }
    
    /**
     * Clean up the pull down animation and blur overlay comprehensively
     */
    private fun PackageParam.cleanupPullDownAnimation(controllerInstance: Any, launcherInstance: Any) {
        try {
            // Get the PullDownAnimator from the controller
            val pullDownAnimator = controllerInstance.current().method {
                name = "getPullDownAnimator"
                superClass()
            }.call()
            
            if (pullDownAnimator != null) {
                // Use destroyAnimAsHomeGesture for more comprehensive cleanup
                try {
                    pullDownAnimator.current().method {
                        name = "destroyAnimAsHomeGesture"
                        superClass()
                    }.call()
                    Log.d(TAG, "[SwipeDownSearch] Successfully called destroyAnimAsHomeGesture")
                } catch (e: Throwable) {
                    Log.w(TAG, "[SwipeDownSearch] destroyAnimAsHomeGesture failed, trying destroyAnimatorIfNeeded: ${e.message}")
                    // Fallback to original method
                    pullDownAnimator.current().method {
                        name = "destroyAnimatorIfNeeded"
                        superClass()
                    }.call()
                }
                
                Log.d(TAG, "[SwipeDownSearch] Successfully cleaned up pull down animation")
            } else {
                Log.w(TAG, "[SwipeDownSearch] Could not get PullDownAnimator for cleanup")
            }
            
            // Also try to clean up BlurScrimWindowController directly from launcher
            try {
                val blurController = launcherInstance.current().method {
                    name = "getBlurScrimWindowController"
                    superClass()
                }.call()
                
                if (blurController != null) {
                    // Clear blur background and detach
                    try {
                        blurController.current().method {
                            name = "blurBackground"
                            param(IntType)
                            superClass()
                        }.call(0)
                    } catch (e: Throwable) {
                        Log.w(TAG, "[SwipeDownSearch] blurBackground method failed: ${e.message}")
                    }
                    
                    try {
                        blurController.current().method {
                            name = "detach"
                            superClass()
                        }.call()
                    } catch (e: Throwable) {
                        Log.w(TAG, "[SwipeDownSearch] detach method failed: ${e.message}")
                    }
                    
                    Log.d(TAG, "[SwipeDownSearch] Successfully cleaned up BlurScrimWindowController")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "[SwipeDownSearch] Could not clean up BlurScrimWindowController: ${e.message}")
            }
            
        } catch (e: Throwable) {
            Log.e(TAG, "[SwipeDownSearch] Error during animation cleanup: ${e.message}")
        }
    }
    
    /**
     * Open app drawer using the same approach as GlobalSearchRedirectHook
     */
    private fun PackageParam.openAppDrawer(launcherInstance: Any): Boolean {
        return try {
            // Try primary method
            val success = try {
                launcherInstance.current().method { 
                    name = "showAllAppsFromIntent"
                    param(BooleanType) 
                }.call(true)
                Log.d(TAG, "[SwipeDownSearch] Called showAllAppsFromIntent successfully")
                true
            } catch (e: Throwable) {
                Log.w(TAG, "[SwipeDownSearch] showAllAppsFromIntent failed, trying TaskbarUtils: ${e.message}")
                
                // Fallback to TaskbarUtils
                try {
                    val launcherContext = launcherInstance as? android.content.Context ?: return false
                    "com.android.launcher3.taskbar.TaskbarUtils".toClass(appClassLoader).method { 
                        name = "showAllApps"
                        param(launcherContext.javaClass)
                        modifiers { isStatic } 
                    }.get().call(launcherContext)
                    Log.d(TAG, "[SwipeDownSearch] Called TaskbarUtils.showAllApps successfully")
                    true
                } catch (e2: Throwable) {
                    Log.e(TAG, "[SwipeDownSearch] TaskbarUtils.showAllApps also failed: ${e2.message}")
                    false
                }
            }
            
            return success
        } catch (e: Throwable) {
            Log.e(TAG, "[SwipeDownSearch] Error opening app drawer: ${e.message}")
            false
        }
    }
} 