package com.wizpizz.onepluspluslauncher.hook.features

import android.util.Log
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_USE_FUZZY_SEARCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlin.math.roundToInt


object FuzzySearchHook {

    private const val SEARCH_CONTAINER_CLASS =
        "com.android.launcher3.allapps.search.LauncherTaskbarAppsSearchContainerLayout"
    private const val BASE_ADAPTER_ITEM_CLASS =
        "com.android.launcher3.allapps.BaseAllAppsAdapter\$AdapterItem"
    private const val APP_INFO_CLASS = "com.android.launcher3.model.data.AppInfo"
    private const val ARRAY_LIST_CLASS = "java.util.ArrayList"

    private const val MATCH_THRESHOLD = 50
    private const val PREFIX_MATCH_MULTIPLIER = 1.5
    private const val SUBSTRING_MATCH_MULTIPLIER = 1.3
    private const val SUBSEQUENCE_MATCH_MULTIPLIER = 1.1

    data class FuzzyMatchResult(val appInfo: Any, val score: Int, val appName: String)

    fun apply(packageParam: PackageParam) {
        packageParam.apply {
            SEARCH_CONTAINER_CLASS.toClassOrNull(appClassLoader)?.method {
                name = "onSearchResult"
                param(String::class.java.name, ARRAY_LIST_CLASS)
            }?.hook {
                before {
                    val rawQuery = args[0] as? String ?: return@before

                    // Read preference - default to true for better search experience
                    val useFuzzySearch = prefs.getBoolean(PREF_USE_FUZZY_SEARCH, true)
                    if (!useFuzzySearch) return@before

                    // IME delimiters: strip spaces and single quotes from the query
                    val sanitizedQuery = sanitizeSearchQuery(rawQuery)
                    if (sanitizedQuery.isBlank()) return@before

                    try {
                        val sortedResults = performFuzzySearch(instance, sanitizedQuery)
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

    private fun sanitizeSearchQuery(input: String): String {
        // Remove spaces and single quote marks which are often used as IME delimiters
        if (input.isEmpty()) return input
        val builder = StringBuilder(input.length)
        input.forEach { ch ->
            if (ch != ' ' && ch != '\'') builder.append(ch)
        }
        return builder.toString()
    }

    private fun PackageParam.performFuzzySearch(
        containerInstance: Any,
        query: String
    ): ArrayList<Any> {
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
            val appsViewField =
                containerInstance.javaClass.field { name = "mAppsView"; superClass(true) }
            val appsViewInstance = appsViewField.get(containerInstance).any() ?: return null

            appsViewInstance.current().method { name = "getAlphabeticalAppsList"; superClass() }
                .call()
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
                val allAppsStore =
                    appsList.current().method { name = "getAllAppsStore"; superClass(true) }.call()
                allAppsStore?.current()?.method { name = "getApps"; superClass(true) }
                    ?.call() as? List<*>
            } catch (e2: Throwable) {
                Log.e(TAG, "[FuzzySearch] Failed to get app infos: ${e2.message}")
                null
            }
        }
    }

    private fun PackageParam.scoreSearchResults(
        appInfos: List<*>,
        query: String
    ): List<FuzzyMatchResult> {
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

                if (score >= MATCH_THRESHOLD) {
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
        // Base score using Weighted Ratio from FuzzyWuzzy (0..100)
        val baseScore = try {
            FuzzySearch.weightedRatio(appNameLower, queryLower)
        } catch (t: Throwable) {
            0
        }

        // Apply boosts based on match type
        val multiplier = when {
            queryLower.isEmpty() -> 1.0
            appNameLower.startsWith(queryLower) -> PREFIX_MATCH_MULTIPLIER
            appNameLower.contains(queryLower) -> SUBSTRING_MATCH_MULTIPLIER
            isSubsequence(appNameLower, queryLower) -> SUBSEQUENCE_MATCH_MULTIPLIER
            else -> 1.0
        }

        return (baseScore * multiplier).roundToInt()
    }

    private fun isSubsequence(text: String, pattern: String): Boolean {
        if (pattern.isEmpty()) return true
        var textIndex = 0
        var patternIndex = 0
        while (textIndex < text.length && patternIndex < pattern.length) {
            if (text[textIndex] == pattern[patternIndex]) {
                patternIndex++
            }
            textIndex++
        }
        return patternIndex == pattern.length
    }

    private fun PackageParam.convertToAdapterItems(scoredResults: List<FuzzyMatchResult>): ArrayList<Any> {
        val sortedResults = scoredResults.sortedByDescending { it.score }

        // if (sortedResults.isNotEmpty()) {
        //     Log.d(TAG, "[FuzzySearch] Matched apps and scores:")
        //     sortedResults.forEach { result ->
        //         Log.d(TAG, "[FuzzySearch] ${result.appName} => ${result.score}")
        //     }
        // }

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