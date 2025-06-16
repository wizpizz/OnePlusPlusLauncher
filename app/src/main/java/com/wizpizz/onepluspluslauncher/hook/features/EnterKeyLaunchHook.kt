package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.ANDROID_X_SEARCH_VIEW_CLASS
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_ENTER_KEY_LAUNCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG

/**
 * Handles Enter key app launching for both launcher versions:
 * - System Launcher 15.4.13: AndroidX SearchView hook
 * - System Launcher 15.6.13+: OnePlus Search Bar Controller hook
 */
object EnterKeyLaunchHook {
    
    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            // Check if enter key launch is enabled
            val enterKeyLaunchEnabled = prefs.getBoolean(PREF_ENTER_KEY_LAUNCH, true)
            if (!enterKeyLaunchEnabled) {
                Log.d(TAG, "[EnterKeyLaunch] Feature disabled in preferences")
                return@apply
            }
            
            // Hook for System Launcher 15.4.13 (AndroidX SearchView)
            hookSearchView()
            
            // Hook for System Launcher 15.6.13+ (OnePlus Controller)
            hookOnePlusController()
        }
    }
    
    /**
     * Hook AndroidX SearchView for System Launcher 15.4.13
     */
    private fun PackageParam.hookSearchView() {
        ANDROID_X_SEARCH_VIEW_CLASS.toClassOrNull(appClassLoader)?.method {
            name = "onSubmitQuery"
            emptyParam()
        }?.hook { 
            before {
                val searchViewInstance = instance as? android.view.View ?: return@before
                
                val searchAutoComplete = instance.current().method { 
                    name = "getSearchAutoComplete" 
                }.call()
                val query = (searchAutoComplete as? android.widget.EditText)?.text?.toString() ?: ""

                if (query.isNotEmpty()) {
                    val launcherInstance = appClassLoader?.let {
                        HookUtils.getLauncherFromContext(
                            searchViewInstance.context,
                            it
                        )
                    }
                    
                    if (launcherInstance != null) {
                        val success = appClassLoader?.let {
                            HookUtils.launchFirstSearchResult(
                                launcherInstance,
                                searchViewInstance,
                                it
                            )
                        }
                        
                        if (success == true) {
                            Log.d(TAG, "[SearchView] Successfully launched first result for: '$query'")
                            resultNull()
                            return@before
                        }
                    }
                }
            }
        } ?: Log.d(TAG, "[SearchView] AndroidX SearchView not found - likely System Launcher 15.6.13+")
    }
    
    /**
     * Hook OnePlus Search Bar Controller for System Launcher 15.6.13+
     */
    private fun PackageParam.hookOnePlusController() {
        "com.android.launcher3.allapps.search.OplusAllAppsSearchBarController"
            .toClassOrNull(appClassLoader)?.method {
            name = "initialize"
            paramCount = 6
        }?.hook {
            after {
                val controller = instance
                val editTextParam = args[2] as? android.widget.EditText
                
                val searchEditText = editTextParam ?: findEditTextInController(controller)
                
                searchEditText?.setOnEditorActionListener { textView, actionId, _ ->
                    handleEditorAction(textView, actionId, controller)
                }
            }
        } ?: Log.d(TAG, "[OnePlusController] OplusAllAppsSearchBarController not found - likely System Launcher 15.4.13")
    }
    
    /**
     * Find EditText field in OnePlus controller
     */
    private fun findEditTextInController(controller: Any): android.widget.EditText? {
        val fieldNames = listOf("mEditText", "mSearchInput", "mInput", "mSearchField", "editText")
        
        for (fieldName in fieldNames) {
            try {
                val field = controller.javaClass.field { name = fieldName; superClass(true) }
                val fieldValue = field.get(controller).any()
                if (fieldValue is android.widget.EditText) {
                    return fieldValue
                }
            } catch (e: Throwable) {
                // Try next field name
            }
        }
        return null
    }
    
    /**
     * Handle Enter key press in OnePlus controller
     */
    private fun PackageParam.handleEditorAction(
        textView: android.widget.TextView,
        actionId: Int,
        controller: Any
    ): Boolean {
        if ((actionId == 2 || actionId == 3) && !android.text.TextUtils.isEmpty(textView.text)) {
            val query = textView.text.toString().trim()
            if (query.isNotEmpty()) {
                val launcherInstance = getLauncherInstance(textView, controller)
                
                if (launcherInstance != null) {
                    val success = appClassLoader?.let {
                        HookUtils.launchFirstSearchResult(
                            launcherInstance,
                            textView as android.view.View,
                            it
                        )
                    }
                    
                    if (success == true) {
                        Log.d(TAG, "[OnePlusController] Successfully launched first result for: '$query'")
                        return true
                    }
                }
            }
        }
        return false
    }
    
    /**
     * Get launcher instance from OnePlus controller
     */
    private fun PackageParam.getLauncherInstance(
        textView: android.widget.TextView,
        controller: Any
    ): Any? {
        // Try mLauncher field
        try {
            val launcherField = controller.javaClass.field { name = "mLauncher"; superClass(true) }
            return launcherField.get(controller).any()
        } catch (e: Throwable) {
            // Try mActivityContext field
            try {
                val activityContextField = controller.javaClass.field { name = "mActivityContext"; superClass(true) }
                return activityContextField.get(controller).any()
            } catch (e2: Throwable) {
                // Fallback: traverse context
                return appClassLoader?.let {
                    HookUtils.getLauncherFromContext(textView.context,
                        it
                    )
                }
            }
        }
    }
    

} 