package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_GLOBAL_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG

/**
 * Intercepts global search redirects to third-party apps (like QuickSearchBox)
 * and redirects them to the All Apps search instead.
 * Also provides optional auto-focus when redirecting.
 */
object GlobalSearchRedirectHook {
    
    private const val SEARCH_ENTRY_CLASS = "com.android.launcher3.search.SearchEntry"
    private const val QUICK_SEARCH_BOX_PACKAGE = "com.oppo.quicksearchbox"
    
    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            SEARCH_ENTRY_CLASS.toClassOrNull(appClassLoader)?.method {
                name = "startSearchApp"
                param(IntentClass)
                returnType = BooleanType
            }?.hook { 
                before { 
                    // Check if global search redirect is enabled
                    val globalSearchRedirectEnabled = prefs.getBoolean(PREF_GLOBAL_SEARCH_REDIRECT, true)
                    if (!globalSearchRedirectEnabled) return@before
                    
                    val intentToLaunch = args[0] as? android.content.Intent
                    val targetPackageName = intentToLaunch?.`package`
                    
                    // Check if this is a QuickSearchBox intent (or null default)
                    val isQuickSearchBoxIntent = (targetPackageName == QUICK_SEARCH_BOX_PACKAGE) || (intentToLaunch == null)

                    if (isQuickSearchBoxIntent) {
                        Log.d(TAG, "[GlobalSearch] Intercepting QuickSearchBox launch, redirecting to All Apps")
                        
                        // Mark that we're starting a redirect to prevent AutoFocusHook from triggering
                        HookUtils.setRedirectInProgress(true)
                        
                        if (redirectToAllApps(instance)) {
                            result = false // Prevent original method
                            return@before
                        } else {
                            // Reset flag if redirect failed
                            HookUtils.setRedirectInProgress(false)
                        }
                    }
                }
            } ?: Log.e(TAG, "[GlobalSearch] Failed to find SearchEntry.startSearchApp method")
        }
    }
    
    private fun PackageParam.redirectToAllApps(searchEntryInstance: Any): Boolean {
        return try {
            val launcherInstance = getLauncherFromSearchEntry(searchEntryInstance)
            if (launcherInstance == null) {
                Log.e(TAG, "[GlobalSearch] Failed to get launcher instance")
                return false
            }
            
            // Try primary method
            val success = try {
                launcherInstance.current().method { 
                    name = "showAllAppsFromIntent"
                    param(BooleanType) 
                }.call(true)
                Log.d(TAG, "[GlobalSearch] Called showAllAppsFromIntent successfully")
                true
            } catch (e: Throwable) {
                Log.w(TAG, "[GlobalSearch] showAllAppsFromIntent failed, trying TaskbarUtils: ${e.message}")
                
                // Fallback to TaskbarUtils
                try {
                    val launcherContext = launcherInstance as? android.content.Context ?: return false
                    "com.android.launcher3.taskbar.TaskbarUtils".toClass(appClassLoader).method { 
                        name = "showAllApps"
                        param(launcherContext.javaClass)
                        modifiers { isStatic } 
                    }.get().call(launcherContext)
                    Log.d(TAG, "[GlobalSearch] Called TaskbarUtils.showAllApps successfully")
                    true
                } catch (e2: Throwable) {
                    Log.e(TAG, "[GlobalSearch] TaskbarUtils.showAllApps also failed: ${e2.message}")
                    false
                }
            }
            
            // If redirect was successful and auto focus on redirect is enabled, focus search
            if (success) {
                val autoFocusRedirectEnabled = prefs.getBoolean(PREF_AUTO_FOCUS_SEARCH_REDIRECT, true)
                if (autoFocusRedirectEnabled) {
                    focusSearchAfterRedirect(launcherInstance)
                } else {
                    // Reset flag immediately if auto focus on redirect is disabled
                    HookUtils.setRedirectInProgress(false)
                    Log.d(TAG, "[GlobalSearch] Auto focus on redirect disabled - reset flag")
                }
            } else {
                // Reset flag if redirect failed
                HookUtils.setRedirectInProgress(false)
                Log.d(TAG, "[GlobalSearch] Redirect failed - reset flag")
            }
            
            return success
        } catch (e: Throwable) {
            Log.e(TAG, "[GlobalSearch] Error redirecting to All Apps: ${e.message}")
            false
        }
    }
    
    private fun getLauncherFromSearchEntry(searchEntryInstance: Any): Any? {
        return try {
            searchEntryInstance.javaClass.field { 
                name = "mLauncher"
                superClass(true)
            }.get(searchEntryInstance).any()
        } catch (e: Exception) {
            Log.e(TAG, "[GlobalSearch] Error getting mLauncher: ${e.message}")
            null
        }
    }
    
    /**
     * Focus search input after redirecting to All Apps
     */
    private fun PackageParam.focusSearchAfterRedirect(launcherInstance: Any) {
        if (launcherInstance !is android.content.Context) return
        
        try {
            // Focus search input immediately after redirect
            try {
                // Reset the redirect flag since we're now handling the redirect focus
                HookUtils.setRedirectInProgress(false)
                // Get AppsView
                var appsView = "com.android.launcher3.Launcher".toClass(appClassLoader)
                    .field { name = "mAppsView" }
                    .get(instance = launcherInstance)
                    .any()
                
                if (appsView == null) {
                    appsView = launcherInstance.current().method { name = "getAppsView" }.call()
                }
                
                if (appsView == null) {
                    Log.e(TAG, "[GlobalSearch] Failed to get AppsView for redirect focus")
                    return
                }

                // Get SearchUiManager
                val searchUiManager = appsView.current().method {
                    name = "getSearchUiManager"
                    superClass()
                }.call()
                
                if (searchUiManager == null) {
                    Log.e(TAG, "[GlobalSearch] Failed to get SearchUiManager for redirect focus")
                    return
                }

                // Try to focus search input
                var searchInputFocused = false
                
                // Approach 1: Try getEditText method
                try {
                    val editText = searchUiManager.current().method {
                        name = "getEditText"
                        superClass()
                    }.call() as? android.widget.EditText
                    
                    if (editText != null) {
                        editText.requestFocus()
                        searchInputFocused = true
                        Log.d(TAG, "[GlobalSearch] Successfully focused search input via getEditText")
                    }
                } catch (e: Throwable) {
                    Log.d(TAG, "[GlobalSearch] getEditText method not available")
                }
                
                // Approach 2: Search for EditText in view hierarchy
                if (!searchInputFocused && searchUiManager is android.view.ViewGroup) {
                    val editText = findEditTextInViewGroup(searchUiManager)
                    if (editText != null) {
                        editText.requestFocus()
                        searchInputFocused = true
                        Log.d(TAG, "[GlobalSearch] Successfully focused search input via view traversal")
                    }
                }
                
                // Show keyboard
                searchUiManager.current().method {
                    name = "showKeyboard"
                    superClass()
                }.call()
                
                if (!searchInputFocused) {
                    Log.w(TAG, "[GlobalSearch] Could not focus search input - no suitable method found")
                }
                
            } catch (e: Throwable) {
                Log.e(TAG, "[GlobalSearch] Error during redirect focus logic: ${e.message}")
            }
            
        } catch (e: Throwable) {
            Log.e(TAG, "[GlobalSearch] Error setting up redirect focus: ${e.message}")
        }
    }
    
    /**
     * Recursively search for EditText in view hierarchy
     */
    private fun findEditTextInViewGroup(viewGroup: android.view.ViewGroup): android.widget.EditText? {
        for (i in 0 until viewGroup.childCount) {
            when (val child = viewGroup.getChildAt(i)) {
                is android.widget.EditText -> return child
                is android.view.ViewGroup -> {
                    val found = findEditTextInViewGroup(child)
                    if (found != null) return found
                }
            }
        }
        return null
    }
} 