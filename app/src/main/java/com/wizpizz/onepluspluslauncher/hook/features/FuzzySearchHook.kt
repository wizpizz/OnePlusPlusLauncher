package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_USE_FUZZY_SEARCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG
import java.util.ArrayList

/**
 * Implements fuzzy search with intelligent ranking:
 * - Prefix matches (highest priority)
 * - Substring matches (medium priority) 
 * - Subsequence matches (lowest priority)
 */
object FuzzySearchHook {
    
    private const val SEARCH_CONTAINER_CLASS = "com.android.launcher3.allapps.search.LauncherTaskbarAppsSearchContainerLayout"
    private const val BASE_ADAPTER_ITEM_CLASS = "com.android.launcher3.allapps.BaseAllAppsAdapter\$AdapterItem"
    private const val APP_INFO_CLASS = "com.android.launcher3.model.data.AppInfo"
    private const val ARRAY_LIST_CLASS = "java.util.ArrayList"
    
    // Scoring constants
    private const val PREFIX_SCORE = 3
    private const val SUBSTRING_SCORE = 2
    private const val SUBSEQUENCE_SCORE = 1
    
    data class FuzzyMatchResult(val appInfo: Any, val score: Int, val appName: String)
    
    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            SEARCH_CONTAINER_CLASS.toClassOrNull(appClassLoader)?.method {
                name = "onSearchResult"
                param(String::class.java.name, ARRAY_LIST_CLASS)
            }?.hook { 
                before { 
                    val query = args[0] as? String ?: return@before
                    
                    // Read preference - default to true for better search experience
                    val useFuzzySearch = prefs.getBoolean(PREF_USE_FUZZY_SEARCH, true)

                    if (!useFuzzySearch || query.isBlank()) {
                        return@before
                    }

                    try {
                        val sortedResults = performFuzzySearch(instance, query)
                        if (sortedResults.isNotEmpty()) {
                            args[1] = sortedResults
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "[FuzzySearch] Error during fuzzy search: ${e.message}")
                    }
                }
            } ?: Log.e(TAG, "[FuzzySearch] Could not find onSearchResult method")
        }
    }
    
    private fun PackageParam.performFuzzySearch(containerInstance: Any, query: String): ArrayList<Any> {
        // Get apps list
        val appsList = getAppsListFromContainer(containerInstance) ?: return ArrayList()
        val allAppInfos = getAllAppInfos(appsList) ?: return ArrayList()
        
        // Score and filter results
        val scoredResults = scoreSearchResults(allAppInfos, query)
        
        // Sort by score and convert to adapter items
        return convertToAdapterItems(scoredResults)
    }
    
    private fun getAppsListFromContainer(containerInstance: Any): Any? {
        return try {
            val appsViewField = containerInstance.javaClass.field { name = "mAppsView"; superClass(true) }
            val appsViewInstance = appsViewField.get(containerInstance).any() ?: return null
            
            appsViewInstance.current().method { name = "getAlphabeticalAppsList"; superClass() }.call()
                ?: appsViewInstance.current().method { name = "getAppsList"; superClass() }.call()
                ?: appsViewInstance.current().method { name = "getApps"; superClass() }.call()
        } catch (e: Throwable) {
            Log.e(TAG, "[FuzzySearch] Failed to get apps list: ${e.message}")
            null
        }
    }
    
    private fun getAllAppInfos(appsList: Any): List<*>? {
        return try {
            appsList.current().method { 
                name = "getApps"
                superClass(true)
            }.call() as? List<*>
        } catch (e: Throwable) {
            try {
                val allAppsStore = appsList.current().method { name = "getAllAppsStore"; superClass(true) }.call()
                allAppsStore?.current()?.method { name = "getApps"; superClass(true) }?.call() as? List<*>
            } catch (e2: Throwable) {
                Log.e(TAG, "[FuzzySearch] Failed to get app infos: ${e2.message}")
                null
            }
        }
    }
    
    private fun PackageParam.scoreSearchResults(appInfos: List<*>, query: String): List<FuzzyMatchResult> {
        val scoredResults = ArrayList<FuzzyMatchResult>()
        val appInfoClass = APP_INFO_CLASS.toClass(appClassLoader)
        val queryLower = query.lowercase()
        
        appInfos.filterNotNull().forEach { appInfoObj ->
            try {
                if (!appInfoClass.isInstance(appInfoObj)) return@forEach
                
                val appInfo = appInfoClass.cast(appInfoObj)
                val titleField = appInfo?.javaClass?.field { name = "title"; superClass(true) }
                val appName = titleField?.get(appInfo)?.any()?.toString() ?: ""
                val appNameLower = appName.lowercase()
                
                val score = calculateMatchScore(appNameLower, queryLower)
                
                if (score > 0) {
                    appInfo?.let { FuzzyMatchResult(it, score, appName) }
                        ?.let { scoredResults.add(it) }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "[FuzzySearch] Error processing app: ${e.message}")
            }
        }
        
        return scoredResults
    }
    
    private fun calculateMatchScore(appNameLower: String, queryLower: String): Int {
        return when {
            appNameLower.startsWith(queryLower) -> PREFIX_SCORE
            appNameLower.contains(queryLower) -> SUBSTRING_SCORE
            isSubsequence(queryLower, appNameLower) -> SUBSEQUENCE_SCORE
            else -> 0
        }
    }
    
    private fun isSubsequence(query: String, appName: String): Boolean {
        if (query.isEmpty()) return false
        
        var queryIndex = 0
        var appNameIndex = 0
        
        while (queryIndex < query.length && appNameIndex < appName.length) {
            if (query[queryIndex] == appName[appNameIndex]) {
                queryIndex++
            }
            appNameIndex++
        }
        
        return queryIndex == query.length
    }
    
    private fun PackageParam.convertToAdapterItems(scoredResults: List<FuzzyMatchResult>): ArrayList<Any> {
        // Sort by score (descending), then alphabetically (ascending)
        val sortedResults = scoredResults.sortedWith { o1, o2 ->
            val scoreCompare = o2.score.compareTo(o1.score)
            if (scoreCompare != 0) scoreCompare
            else o1.appName.compareTo(o2.appName, ignoreCase = true)
        }

        val finalAdapterItems = ArrayList<Any>()
        val adapterItemClass = BASE_ADAPTER_ITEM_CLASS.toClass(appClassLoader)
        val appInfoClass = APP_INFO_CLASS.toClass(appClassLoader)
        
        sortedResults.forEach { result -> 
            try {
                val adapterItem = adapterItemClass.method { 
                    name = "asApp"
                    param(appInfoClass) 
                    modifiers { isStatic }
                }.get().call(result.appInfo)
                
                if (adapterItem != null) {
                    finalAdapterItems.add(adapterItem)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "[FuzzySearch] Error converting ${result.appName}: ${e.message}")
            }
        }
        
        return finalAdapterItems
    }
} 