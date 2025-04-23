package com.wizpizz.onepluspluslauncher.hook

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    // Define class names to avoid typos and for easier maintenance
    private val launcherClass = "com.android.launcher3.Launcher"
    private val launcherStateClass = "com.android.launcher3.LauncherState"
    // Class for the AndroidX Search View base class
    private val androidXSearchViewClass = "androidx.appcompat.widget.SearchView"
    // Add ItemInfo class path
    private val itemInfoClass = "com.android.launcher3.model.data.ItemInfo"
    // Add View and Intent class paths
    private val viewClass = "android.view.View"
    private val intentClass = "android.content.Intent"

    // Target App Package Name
    private val targetPackageName = "com.android.launcher"

    private val TAG = "OPPLauncherHook"

    // Add preference key
    companion object {
        const val PREF_USE_FUZZY_SEARCH = "use_fuzzy_search"
    }

    // Data class to hold fuzzy match results with score
    data class FuzzyMatchResult(val appInfo: Any, val score: Int, val appName: String)

    override fun onInit() = configs {
        isDebug = true
    }

    @SuppressLint("DiscouragedApi")
    override fun onHook() = encase {
        Log.i(TAG, "OPPLauncher module onHook() started in process") 
        loadApp(name = targetPackageName) {
             Log.i(TAG, "OPPLauncher attempting to hook app: $targetPackageName")
            
            // --- Hook onStateSetStart --- 
            launcherClass.toClass(appClassLoader).method {
                name = "onStateSetStart"
                param(launcherStateClass.toClass(appClassLoader))
            }.hook {
                after { // Run after the state transition is initiated
                    val launcherInstance = instance
                    val targetState = args[0] ?: return@after
                    val allAppsState = launcherStateClass.toClass(appClassLoader).field { name = "ALL_APPS" }.get().any() ?: return@after

                    if (targetState == allAppsState) {
                        Log.d(TAG, "Launcher.onStateSetStart detected target ALL_APPS")
                        val context = launcherInstance as? Context ?: return@after
                        try {
                            // Get AppsView (using same logic as onStateSetEnd hook)
                            var appsView = launcherClass.toClass(appClassLoader)
                                .field { name = "mAppsView" }
                                .get(instance = launcherInstance)
                                .any()
                            if (appsView == null) {
                                appsView = launcherInstance.current().method { name = "getAppsView" }.call()
                            }
                            if (appsView == null) {
                                Log.e(TAG, "[onStateSetStart] Failed to get AppsView")
                                return@after
                            }
                            Log.d(TAG, "[onStateSetStart] Got AppsView: ${appsView.javaClass.name}")

                            // Get SearchUiManager
                            val searchUiManagerInstance = appsView.current().method {
                                name = "getSearchUiManager"
                                superClass()
                            }.call()
                            if (searchUiManagerInstance == null) {
                                Log.e(TAG, "[onStateSetStart] Failed to get SearchUiManager")
                                return@after
                            }

                            // Find search input and focus
                            val searchViewParent = searchUiManagerInstance as? android.view.View
                            if (searchViewParent != null) {
                                val targetRes = context.resources
                                val searchViewId = targetRes.getIdentifier("search_box_input", "id", targetPackageName)
                                if (searchViewId != 0) {
                                    val couiSearchView = searchViewParent.findViewById<android.view.View>(searchViewId)
                                    if (couiSearchView != null) {
                                    } else {
                                        // Keep error log
                                        Log.e(TAG, "[onStateSetStart] Failed to find view with ID 'search_box_input'")
                                    }
                                } else {
                                    // Keep error log
                                    Log.e(TAG, "[onStateSetStart] Failed to get resource ID for 'search_box_input'")
                                }
                            } else {
                                // Keep error log
                                Log.e(TAG, "[onStateSetStart] SearchUiManager is not a View")
                            }

                            // Show keyboard
                            searchUiManagerInstance.current().method {
                                name = "showKeyboard"
                                superClass()
                            }.call() != null
                        } catch (e: Throwable) {
                            Log.e(TAG, "[onStateSetStart] Error during focus logic: ${e.message}", e)
                        }
                        // We don't prevent original method here, just trigger focus early
                    }
                }
            }

            // --- Hook onSubmitQuery on AndroidX SearchView --- 
            androidXSearchViewClass.toClassOrNull(appClassLoader)?.method {
                name = "onSubmitQuery"
                emptyParam() // onSubmitQuery has no parameters
            }?.hook { 
                before { // Hook *before* original logic runs
                    
                    // The instance here is the SearchView (likely COUISearchViewAnimate)
                    val searchViewInstance = instance as? android.view.View ?: run {
                        Log.e(TAG, "Hook instance is not a View, cannot proceed.")
                        return@before
                    }
                    
                    // --- Get query via internal EditText --- 
                    val searchAutoComplete = instance.current().method { name = "getSearchAutoComplete" }.call()
                    val query = (searchAutoComplete as? android.widget.EditText)?.text?.toString() ?: ""
                    // --- End get query ---

                    if (query.isNotEmpty()) {

                        // Try getting Launcher from the View's context again, handling wrappers
                        var context = searchViewInstance.context
                        var currentActivity: android.app.Activity? = null
                        while (context is android.content.ContextWrapper) {
                            if (context is android.app.Activity) {
                                currentActivity = context
                                break
                            }
                            context = context.baseContext
                        }
                        if (currentActivity == null && context is android.app.Activity) {
                             currentActivity = context
                        }
                        val launcher3Class = launcherClass.toClassOrNull(appClassLoader)

                        if (launcher3Class != null && currentActivity != null && launcher3Class.isInstance(currentActivity)) {
                            try {
                                val baseLauncherInstance = currentActivity
                                val appsViewFromContext = baseLauncherInstance.current().method { name = "getAppsView"; superClass() }.call()
                                val appsList = appsViewFromContext?.current()?.method { name = "getAlphabeticalAppsList"; superClass() }?.call() 
                                        ?: appsViewFromContext?.current()?.method { name = "getAppsList"; superClass() }?.call() 
                                        ?: appsViewFromContext?.current()?.method { name = "getApps"; superClass() }?.call()

                                if (appsList != null) {
                                    val searchResults = appsList.current().method { name = "getSearchResults"; superClass() }.call() as? ArrayList<*>
                                    if (searchResults != null && searchResults.isNotEmpty()) {
                                        val firstAdapterItem = searchResults[0]
                                        if (firstAdapterItem != null) {
                                            // Find the field first
                                            val itemInfoField = firstAdapterItem.javaClass.field { name = "itemInfo"; superClass(true) }
                                            // Then get the value from the instance
                                            val itemInfoObject = itemInfoField.get(firstAdapterItem).any()
                                            // Get ItemInfo class via reflection
                                            val itemInfoClassConst = itemInfoClass.toClass(appClassLoader)
                                            val itemInfo = if (itemInfoClassConst.isInstance(itemInfoObject)) itemInfoObject else null
                                            
                                            if (itemInfo != null) {

                                                // --- Directly use startActivitySafely --- 
                                                val foundIntent = itemInfo.current().method { name = "getIntent"; superClass()}.call() as? android.content.Intent
                                                
                                                if (foundIntent != null) {
                                                    baseLauncherInstance.current().method {
                                                        name = "startActivitySafely"
                                                        param(viewClass.toClass(appClassLoader),
                                                                intentClass.toClass(appClassLoader),
                                                                itemInfoClass.toClass(appClassLoader))
                                                        superClass()
                                                    }.call(searchViewInstance, foundIntent, itemInfo) 
                                                    Log.d(TAG, "Called startActivitySafely for the first result.")
                                                    
                                                    // Prevent original method only if launch was successful
                                                    resultNull() 
                                                    return@before // Exit hook
                                                } else {
                                                    Log.e(TAG, "Failed to get launch intent for startActivitySafely.") // Keep
                                                    // Allow original method if intent retrieval failed
                                                }
                                            } else {
                                                 Log.e(TAG, "Failed to get ItemInfo from the first AdapterItem.") // Keep
                                            }
                                        } else {
                                             Log.e(TAG, "First AdapterItem in search results was null.") // Keep
                                        }
                                    } else {
                                         // Allowing original makes sense here
                                    }
                            } else {
                                    Log.e(TAG, "Failed to get AlphabeticalAppsList from AppsView.") // Keep
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "Error during launch attempt: ${e.message}", e) // Keep
                            }
                        } else {
                             Log.e(TAG, "Could not get Activity instance or wrong activity type: ${currentActivity?.javaClass?.name}") // Keep
                        }
                    } else {
                    }
                    // Allow original method if we fall through
                }
            }

            // --- Hook Search Result Callback for Fuzzy Search --- 
            val searchContainerClass = "com.android.launcher3.allapps.search.LauncherTaskbarAppsSearchContainerLayout"
            val baseAdapterItemClass = "com.android.launcher3.allapps.BaseAllAppsAdapter\$AdapterItem"
            val appInfoClass = "com.android.launcher3.model.data.AppInfo"
            val arrayListClass = "java.util.ArrayList"

            searchContainerClass.toClassOrNull(appClassLoader)?.method {
                name = "onSearchResult"
                param(String::class.java.name, arrayListClass)
            }?.hook { 
                before { 
                    val instanceContainer = instance
                    val query = args[0] as? String ?: return@before
                    
                    // Read preference
                    val useFuzzySearch = prefs.getBoolean(PREF_USE_FUZZY_SEARCH, true) // Default to true

                    // Conditionally apply fuzzy logic
                    if (!useFuzzySearch || query.isBlank()) {
                        return@before
                    }

                    try {
                        // Get AppsView from the container instance
                        val appsViewField = instanceContainer.javaClass.field { name = "mAppsView"; superClass(true) }
                        val appsViewInstance = appsViewField.get(instanceContainer).any() ?: run {
                            Log.e(TAG, "[FuzzySearch] Failed to get mAppsView from container ${instanceContainer.javaClass.name}")
                            return@before
                        }

                        // Get AlphabeticalAppsList (reuse logic from onSubmitQuery)
                        val appsList: Any? = appsViewInstance.current().method { name = "getAlphabeticalAppsList"; superClass() }
                            .call()
                                ?: appsViewInstance.current().method { name = "getAppsList"; superClass() }
                                    .call()
                                ?: appsViewInstance.current().method { name = "getApps"; superClass() }
                                    .call()
                        if (appsList == null) {
                            Log.e(TAG, "[FuzzySearch] Failed to get appsList instance")
                            return@before
                        }
                        
                        // --- Get All App Infos using getApps() method --- 
                        var allAppInfos: List<*>? = null
                        try {
                            // Call the getApps() method, explicitly searching superclasses
                            allAppInfos = appsList.current().method { 
                                name = "getApps"
                                superClass(true) // Explicitly search superclasses
                            }.call() as? List<*>
                        } catch (e: Throwable) {
                            Log.e(TAG, "[FuzzySearch] Error calling getApps() method: ${e.message}", e)
                            // Attempt fallback using getAllAppsStore().getApps() (also needs superClass check)
                            try {
                                val allAppsStore = appsList.current().method { name = "getAllAppsStore"; superClass(true) }.call()
                                allAppInfos = allAppsStore?.current()?.method { name = "getApps"; superClass(true) }?.call() as? List<*>
                            } catch (e2: Throwable) {
                                Log.e(TAG, "[FuzzySearch] Error calling getAllAppsStore().getApps(): ${e2.message}", e2)
                            }
                        }
                        
                        if (allAppInfos == null) {
                             Log.e(TAG, "[FuzzySearch] Failed to get app list via getApps() or fallback.")
                             return@before
                        }
                        val filteredAppInfos = allAppInfos.filterNotNull() // Ensure no nulls in the list
                        
                        // --- Score Constants --- 
                        val PREFIX_SCORE = 3
                        val SUBSTRING_SCORE = 2
                        val SUBSEQUENCE_SCORE = 1
                        
                        // List to hold intermediate results with scores
                        val scoredResults = ArrayList<FuzzyMatchResult>()
                        val appInfoClassTyped = appInfoClass.toClass(appClassLoader)
                        val queryLower = query.lowercase()
                        
                        filteredAppInfos.forEach { appInfoObj ->
                            try {
                                if (!appInfoClassTyped.isInstance(appInfoObj)) { return@forEach }
                                val appInfo = appInfoClassTyped.cast(appInfoObj)
                                val titleField = appInfo?.javaClass?.field { name = "title"; superClass(true) }
                                val appName = titleField?.get(appInfo)?.any()?.toString() ?: ""
                                val appNameLower = appName.lowercase()
                                
                                var score = 0
                                if (appNameLower.startsWith(queryLower)) {
                                    score = PREFIX_SCORE
                                } else if (appNameLower.contains(queryLower)) {
                                    score = SUBSTRING_SCORE
                                } else {
                                    // Subsequence check (only if not prefix or substring)
                                    var queryIndex = 0 
                                    var appNameIndex = 0
                                    var isSubsequence = false
                                    if (queryLower.isNotEmpty()) { 
                                        while (queryIndex < queryLower.length && appNameIndex < appNameLower.length) {
                                            if (queryLower[queryIndex] == appNameLower[appNameIndex]) {
                                                queryIndex++
                                            }
                                            appNameIndex++
                                        }
                                        if (queryIndex == queryLower.length) {
                                            isSubsequence = true
                                        }
                                    }
                                    if (isSubsequence) {
                                        score = SUBSEQUENCE_SCORE
                                    }
                                }
                                
                                if (score > 0) {
                                    scoredResults.add(FuzzyMatchResult(appInfo, score, appName))
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "[FuzzyRank] Error processing app '$appInfoObj': ${e.message}", e) // Keep
                            }
                        }

                        // --- Sort Results --- 
                        // Sort by score (descending), then alphabetically (ascending)
                        Collections.sort(scoredResults, 
                            Comparator<FuzzyMatchResult> { o1, o2 -> 
                                // Primary sort: score descending
                                val scoreCompare = o2.score.compareTo(o1.score)
                                if (scoreCompare != 0) {
                                    return@Comparator scoreCompare
                                }
                                // Secondary sort: app name ascending (case-insensitive)
                                return@Comparator o1.appName.compareTo(o2.appName, ignoreCase = true)
                            }
                        )

                        // --- Convert Sorted AppInfo to AdapterItem --- 
                        val finalAdapterItems = ArrayList<Any>()
                        val adapterItemClass = baseAdapterItemClass.toClass(appClassLoader)
                        val asAppMethod = adapterItemClass.method { 
                            name = "asApp"
                            param(appInfoClassTyped) 
                            modifiers { isStatic }
                        }.get() 
                        
                        scoredResults.forEach { result -> 
                            try {
                                val adapterItem: Any? = asAppMethod.invoke(result.appInfo)
                                if (adapterItem != null) {
                                    finalAdapterItems.add(adapterItem)
                                } else {
                                }
                        } catch (e: Throwable) {
                                Log.e(TAG, "[FuzzyRank] Error invoking asApp for ${result.appName}: ${e.message}", e) // Keep
                            }
                        }

                        // Replace the original results list with our final sorted list
                        args[1] = finalAdapterItems

                    } catch (e: Throwable) {
                        Log.e(TAG, "[FuzzyRank] Error during fuzzy rank/sort hook: ${e.message}", e) // Keep
                    }
                }
            } ?: Log.e(TAG, "[FuzzySearch] Could not find method 'onSearchResult' in class $searchContainerClass") // Keep

            // --- Hook SearchEntry.startSearchApp to intercept Global Search --- 
            val searchEntryClass = "com.android.launcher3.search.SearchEntry"
            val quickSearchBoxPackage = "com.oppo.quicksearchbox" // Target package
            
            Log.d(TAG, "[SearchEntryIntercept] Setting up SearchEntry.startSearchApp hook...")
            searchEntryClass.toClassOrNull(appClassLoader)?.method {
                name = "startSearchApp"
                param(IntentClass)
                returnType = BooleanType // Assuming it returns boolean based on decompiled code
            }?.hook { 
                before { 
                     Log.i(TAG, "---------- [SearchEntryIntercept] SearchEntry.startSearchApp HOOK EXECUTED ----------")
                     val searchEntryInstance = instance
                     val intentToLaunch = args[0] as? android.content.Intent

                     // Determine the package name being launched
                     val targetPackageName = intentToLaunch?.`package`
                     
                     // If intent is null, we assume it defaults to the QuickSearchBox based on the trigger context.
                     val isQuickSearchBoxIntent = (targetPackageName == quickSearchBoxPackage) || (intentToLaunch == null)

                     Log.d(TAG, "[SearchEntryIntercept] Intent package = $targetPackageName, Is QuickSearchBox Target = $isQuickSearchBoxIntent")

                     if (isQuickSearchBoxIntent) {
                        Log.d(TAG, "[SearchEntryIntercept] Intercepting QuickSearchBox launch (or null intent default). Redirecting to All Apps.")
                        try {
                            // --- Call App Drawer Logic --- 
                            var launcherInstance: Any? = null // Declare outside the try block
                            try {
                                // 1. Get Launcher instance from SearchEntry's mLauncher field (Ensure this works)
                                try {
                                    val searchEntryClassForField = searchEntryInstance.javaClass
                                    var launcherField: java.lang.reflect.Field? = null
                                    var currentClass: Class<*>? = searchEntryClassForField
                                    while (currentClass != null && launcherField == null) {
                                        try { launcherField = currentClass.getDeclaredField("mLauncher") }
                                        catch (_: NoSuchFieldException) { currentClass = currentClass.superclass }
                                    }
                                    if (launcherField == null) { throw NoSuchFieldException("mLauncher") }
                                    launcherField.isAccessible = true
                                    launcherInstance = launcherField.get(searchEntryInstance)
                                } catch (e: Exception) {
                                    Log.e(TAG, "[SearchEntryIntercept] Error getting mLauncher: ${e.message}")
                                    // Allow original if we can't proceed
                                    return@before 
                                }
                                if (launcherInstance == null) {
                                    Log.e(TAG, "[SearchEntryIntercept] mLauncher field is null")
                                    return@before 
                                }
                                launcherInstance as? Context ?: run {
                                    Log.e(TAG, "[SearchEntryIntercept] Launcher is not a Context")
                                    return@before
                                }

                                // 2. Show All Apps (Primary: showAllAppsFromIntent)
                                launcherInstance.current().method { name = "showAllAppsFromIntent"; param(BooleanType) }.call(true)
                                Log.d(TAG, "[SearchEntryIntercept] Called showAllAppsFromIntent successfully.")

                                // 3. Prevent original startSearchApp method
                                result = false 
                                return@before

                            } catch (e: Throwable) {
                                Log.w(TAG, "[SearchEntryIntercept] showAllAppsFromIntent failed, trying TaskbarUtils fallback: ${e.message}")
                                // 2b. Show All Apps (Fallback: TaskbarUtils)
                                try {
                                     // Need launcherContext from the outer try block
                                    // launcherInstance is now accessible here due to moved declaration
                                    val launcherContext = (launcherInstance as? Context) ?: run {
                                         Log.e(TAG, "[SearchEntryIntercept] Launcher instance became invalid for fallback")
                                         return@before
                                    }
                                    val taskbarUtilsClass = "com.android.launcher3.taskbar.TaskbarUtils".toClass(appClassLoader)
                                    val showMethod = taskbarUtilsClass.method { name = "showAllApps"; param(launcherContext.javaClass); modifiers { isStatic } }.give()
                                    showMethod?.isAccessible = true
                                    showMethod?.invoke(null, launcherContext)
                                    Log.d(TAG, "[SearchEntryIntercept] Called TaskbarUtils.showAllApps successfully.")
                                    
                                    // 3b. Prevent original
                                    result = false
                                    return@before
                                } catch (e2: Throwable) {
                                    Log.e(TAG, "[SearchEntryIntercept] All attempts to show app drawer failed for QSB intercept: ${e2.message}")
                                    // Allow original method if our redirect fails completely
                                }
                            } 
                            // --- End App Drawer Logic --- 

                        } catch (e: Throwable) { // Catch outer try block (shouldn't happen often)
                             Log.e(TAG, "[SearchEntryIntercept] Unexpected error during QSB intercept: ${e.message}", e)
                        }
                     }
                 }
            } ?: Log.e(TAG, "[SearchEntryIntercept] FAILED to find or hook SearchEntry.startSearchApp method.")

        } // End loadApp
    } // End onHook
} // End HookEntry