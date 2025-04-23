# Technical Details

This document describes the specific hooks and reflection strategies used by the OnePlusPlusLauncher module.

## Hook Implementations

### 1. Auto Focus & Keyboard (`Launcher.onStateSetStart`)

*   **Hook Target:** `com.android.launcher3.Launcher#onStateSetStart(com.android.launcher3.LauncherState state)`
*   **Timing:** Hooked `after` the method executes.
*   **Logic:**
    *   Checks if the `targetState` is `LauncherState.ALL_APPS`.
    *   If so, it attempts to retrieve the `AppsView`, then `SearchUiManager`, find the search input `View`, and call `showKeyboard()` on the `SearchUiManager`.

### 2. Instant Launch (`SearchView.onSubmitQuery`)

*   **Hook Target:** `androidx.appcompat.widget.SearchView#onSubmitQuery()` (or subclasses).
*   **Timing:** Hooked `before` the original method runs.
*   **Logic:**
    *   Retrieves the current query text.
    *   If the query is not empty, it gets the `Launcher` context, finds the first search result's `ItemInfo`, gets its `Intent`, calls `Launcher.startActivitySafely(View, Intent, ItemInfo)` to launch it, and prevents the original method.

### 3. Global Search Button Redirect (`SearchEntry.startSearchApp`)

*   **Hook Target:** `com.android.launcher3.search.SearchEntry#startSearchApp(Intent intent)`
*   **Timing:** Hooked `before` the original method runs.
*   **Purpose:** Intercepts actions (e.g., triggered by a dedicated search button or null-intent context) that attempt to launch the dedicated Global Search app (e.g., `com.oppo.quicksearchbox`).
*   **Logic:**
    *   Checks if the `intent` package matches the global search app package or if the `intent` is `null`.
    *   If it matches, retrieves the `Launcher` instance from the `SearchEntry` instance (via `mLauncher` field reflection).
    *   Attempts to call `launcherInstance.showAllAppsFromIntent(true)`.
    *   If that fails, attempts the static method `com.android.launcher3.taskbar.TaskbarUtils.showAllApps(Context)`.
    *   If either succeeds, prevents the original `startSearchApp` method.

### 4. Fuzzy Search Override (`LauncherTaskbarAppsSearchContainerLayout.onSearchResult`)

*   **Hook Target:** `com.android.launcher3.allapps.search.LauncherTaskbarAppsSearchContainerLayout#onSearchResult(String query, ArrayList<BaseAllAppsAdapter.AdapterItem> results)` (Class name might vary).
*   **Timing:** Hooked `before` the original method runs.
*   **Logic:**
    *   If fuzzy search preference is enabled and query is not blank:
    *   Retrieves the full app list (`List<AppInfo>`) via `getApps()` or fallback `getAllAppsStore().getApps()`.
    *   Calculates match scores (Prefix > Substring > Subsequence) for all apps against the query.
    *   Sorts matches by score (desc), then name (asc).
    *   Creates new `AdapterItem` list from sorted matches using `BaseAllAppsAdapter.AdapterItem.asApp(AppInfo)` factory method.
    *   Replaces original results list (`args[1]`) with the new list.

## Reflection Strategies & Considerations

This module uses Java Reflection extensively.

*   **YukiHookAPI Helpers vs. Standard Reflection:** Standard Java Reflection combined with YukiHookAPI's finders (`.give()`) often proved more robust during development than relying solely on YukiHookAPI's direct call/get helpers (`.call()`, `.get().any()`), which can be sensitive to type inference or API variations.

*   **Current Implementation Approach:**
    *   **Field Access:** Primarily uses standard Java Reflection (`getDeclaredField` loop, `setAccessible`, `field.get`). Used for `mLauncher`.
    *   **Method Invocation (Static):** Uses Yuki's finder (`.method{}`) + `.give()` + standard `method.invoke(null, ...)`. Used for `TaskbarUtils.showAllApps`.
    *   **Method Invocation (Instance):** Generally uses Yuki's `.call()` helper (e.g., `showAllAppsFromIntent`, `getApps`). Fallback to `.give().invoke(...)` if needed.
    *   **Specific Exceptions:** Uses `field{}.get(instance).any()` for `mAppsView` in fuzzy search hook, as it was reliable in testing.

*   **Troubleshooting Reflection Errors:** Crashes or failures after launcher updates likely stem from changed internal code.
    1.  Check logs (`adb logcat -s OnePlusPlusLauncherHook`) for reflection errors.
    2.  Decompile the launcher APK (e.g., with JADX) to verify class/field/method names/signatures.
    3.  Update the reflection logic in the hook code accordingly. 