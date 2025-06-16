package com.wizpizz.onepluspluslauncher.hook.features

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.toClass

import java.util.ArrayList

object HookUtils {
    
    // Constants
    const val TAG = "OPPLauncherHook"

    // Preference keys
    const val PREF_USE_FUZZY_SEARCH = "use_fuzzy_search"
    const val PREF_AUTO_FOCUS_SEARCH_SWIPE = "auto_focus_search_swipe"
    const val PREF_AUTO_FOCUS_SEARCH_REDIRECT = "auto_focus_search_redirect"
    const val PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT = "auto_focus_swipe_down_redirect"
    const val PREF_ENTER_KEY_LAUNCH = "enter_key_launch"
    const val PREF_GLOBAL_SEARCH_REDIRECT = "global_search_redirect"
    const val PREF_SWIPE_DOWN_SEARCH_REDIRECT = "swipe_down_search_redirect"
    
    // Class names
    const val LAUNCHER_CLASS = "com.android.launcher3.Launcher"
    const val LAUNCHER_STATE_CLASS = "com.android.launcher3.LauncherState"
    const val ANDROID_X_SEARCH_VIEW_CLASS = "androidx.appcompat.widget.SearchView"
    private const val ITEM_INFO_CLASS = "com.android.launcher3.model.data.ItemInfo"
    private const val VIEW_CLASS = "android.view.View"
    private const val INTENT_CLASS = "android.content.Intent"
    
    // Coordination flag to distinguish between swipe and redirect
    @Volatile
    private var isRedirectInProgress = false
    
    /**
     * Mark that a redirect is in progress (used by GlobalSearchRedirectHook)
     */
    fun setRedirectInProgress(inProgress: Boolean) {
        isRedirectInProgress = inProgress
    }
    
    /**
     * Check if a redirect is currently in progress (used by AutoFocusHook)
     */
    fun isRedirectInProgress(): Boolean = isRedirectInProgress
    
    /**
     * Extract Launcher instance from context traversal
     */
    fun getLauncherFromContext(context: Context, appClassLoader: ClassLoader): Any? {
        var currentContext: Context? = context
        val launcher3Class = LAUNCHER_CLASS.toClassOrNull(appClassLoader)
        
        while (currentContext != null) {
            if (currentContext is Activity && launcher3Class?.isInstance(currentContext) == true) {
                return currentContext
            }
            currentContext = if (currentContext is ContextWrapper) {
                currentContext.baseContext
            } else {
                null
            }
        }
        return null
    }
    
    /**
     * Focus search input in app drawer
     */
    fun focusSearchInput(launcherInstance: Any, appClassLoader: ClassLoader) {
        if (launcherInstance !is android.content.Context) return
        
        try {
            // Get AppsView
            var appsView = LAUNCHER_CLASS.toClass(appClassLoader)
                .field { name = "mAppsView" }
                .get(instance = launcherInstance)
                .any()
            
            if (appsView == null) {
                appsView = launcherInstance.current().method { name = "getAppsView" }.call()
            }
            
            if (appsView == null) {
                Log.e(TAG, "[AutoFocus] Failed to get AppsView")
                return
            }

            // Get SearchUiManager
            val searchUiManager = appsView.current().method {
                name = "getSearchUiManager"
                superClass()
            }.call()
            
            if (searchUiManager == null) {
                Log.e(TAG, "[AutoFocus] Failed to get SearchUiManager")
                return
            }

            // Try multiple approaches to get and focus search input
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
                    Log.d(TAG, "[AutoFocus] Successfully focused search input via getEditText")
                }
            } catch (e: Throwable) {
                Log.d(TAG, "[AutoFocus] getEditText method not available")
            }
            
            // Approach 2: Search for EditText in view hierarchy
            if (!searchInputFocused && searchUiManager is android.view.ViewGroup) {
                val editText = findEditTextInViewGroup(searchUiManager)
                if (editText != null) {
                    editText.requestFocus()
                    searchInputFocused = true
                    Log.d(TAG, "[AutoFocus] Successfully focused search input via view traversal")
                }
            }
            
            if (!searchInputFocused) {
                Log.w(TAG, "[AutoFocus] Could not focus search input - no suitable method found")
            }

            // Show keyboard
            searchUiManager.current().method {
                name = "showKeyboard"
                superClass()
            }.call()
            
        } catch (e: Throwable) {
            Log.e(TAG, "[AutoFocus] Error during focus logic: ${e.message}")
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
    
    /**
     * Launch first search result
     */
    fun launchFirstSearchResult(
        launcherInstance: Any,
        searchView: android.view.View,
        appClassLoader: ClassLoader
    ): Boolean {
        return try {
            val appsView = launcherInstance.current().method { 
                name = "getAppsView"; superClass() 
            }.call()
            
            val appsList = appsView?.current()?.method { 
                name = "getAlphabeticalAppsList"; superClass() 
            }?.call() ?: appsView?.current()?.method { 
                name = "getAppsList"; superClass() 
            }?.call()

            if (appsList != null) {
                val searchResults = appsList.current().method { 
                    name = "getSearchResults"; superClass() 
                }.call() as? ArrayList<*>
                
                if (searchResults != null && searchResults.isNotEmpty()) {
                    val firstAdapterItem = searchResults[0]
                    if (firstAdapterItem != null) {
                        val itemInfoField = firstAdapterItem.javaClass.field { 
                            name = "itemInfo"; superClass(true) 
                        }
                        val itemInfoObject = itemInfoField.get(firstAdapterItem).any()
                        val itemInfoClass = ITEM_INFO_CLASS.toClass(appClassLoader)
                        val itemInfo = if (itemInfoClass.isInstance(itemInfoObject)) itemInfoObject else null
                        
                        if (itemInfo != null) {
                            val foundIntent = itemInfo.current().method { 
                                name = "getIntent"; superClass()
                            }.call() as? android.content.Intent
                            
                            if (foundIntent != null) {
                                launcherInstance.current().method {
                                    name = "startActivitySafely"
                                    param(VIEW_CLASS.toClass(appClassLoader),
                                            INTENT_CLASS.toClass(appClassLoader),
                                            ITEM_INFO_CLASS.toClass(appClassLoader))
                                    superClass()
                                }.call(searchView, foundIntent, itemInfo)
                                
                                return true
                            }
                        }
                    }
                }
            }
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Error launching first search result: ${e.message}")
            false
        }
    }
}

// Extension for safe class resolution
fun String.toClassOrNull(classLoader: ClassLoader): Class<*>? {
    return try {
        Class.forName(this, false, classLoader)
    } catch (e: ClassNotFoundException) {
        null
    }
} 