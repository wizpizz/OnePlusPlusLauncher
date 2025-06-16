# Technical Details

This document describes the specific hooks and reflection strategies used by the OnePlusPlusLauncher module.

## Module Architecture

The module is organized into feature-specific hook files:

- **`AutoFocusHook.kt`** - Auto focus search bar when app drawer opens
- **`EnterKeyLaunchHook.kt`** - Launch first result on Enter key (cross-version compatibility)
- **`GlobalSearchRedirectHook.kt`** - Redirect global search button to app drawer  
- **`SwipeDownSearchRedirectHook.kt`** - Redirect swipe down search to app drawer with optional auto focus
- **`FuzzySearchHook.kt`** - Enhanced fuzzy search with multiple match types
- **`HookUtils.kt`** - Shared utilities for all hooks

## Hook Implementations

### 1. Auto Focus & Keyboard (`AutoFocusHook.kt`)

- **Hook Target:** `com.android.launcher3.Launcher#onStateSetStart(com.android.launcher3.LauncherState state)`
- **Timing:** Hooked `after` the method executes.
- **Logic:**
  - Checks if the `targetState` is `LauncherState.ALL_APPS`.
  - If so, it attempts to retrieve the `AppsView`, then `SearchUiManager`, find the search input `View`, and call `showKeyboard()` on the `SearchUiManager`.

### 2. Enter Key Launch - Unified Cross-Version Hook (`EnterKeyLaunchHook.kt`)

This hook provides Enter key app launching for both launcher versions using a dual-hook approach:

#### For System Launcher 15.4.13 (SearchView-based):
- **Hook Target:** `androidx.appcompat.widget.SearchView#onSubmitQuery()`
- **Timing:** Hooked `before` the original method runs.
- **Logic:**
  - Retrieves the current query text from SearchView's SearchAutoComplete.
  - If the query is not empty, gets the `Launcher` context, finds the first search result's `ItemInfo`, gets its `Intent`, calls `Launcher.startActivitySafely(View, Intent, ItemInfo)` to launch it, and prevents the original method.
- **Compatibility:** Works with System Launcher 15.4.13 (SearchView-based search).

#### For System Launcher 15.6.13+ (EditText-based):
- **Hook Target:** `com.android.launcher3.allapps.search.OplusAllAppsSearchBarController#initialize(...)`
- **Timing:** Hooked `after` the method executes.
- **Purpose:** Provides Enter key app launching for System Launcher 15.6.13+ where OnePlus switched from SearchView to EditText.
- **Logic:**
  - Extracts the `EditText` parameter from the initialize method (3rd parameter, `args[2]`).
  - Fallback: If parameter is null, tries common EditText field names (`mEditText`, `mSearchInput`, etc.).
  - Sets a custom `OnEditorActionListener` that detects Enter key presses (action IDs 2 or 3).
  - On Enter detection, retrieves launcher instance via multiple methods (`mLauncher` field, `mActivityContext` field, or EditText context traversal).
  - Gets search results and launches the first result using `startActivitySafely`.
  - Automatically hides keyboard after successful launch.
- **Compatibility:** Works with System Launcher 15.6.13+ (EditText-based search).

### 3. Global Search Button Redirect (`GlobalSearchRedirectHook.kt`)

- **Hook Target:** `com.android.launcher3.search.SearchEntry#startSearchApp(Intent intent)`
- **Timing:** Hooked `before` the original method runs.
- **Purpose:** Intercepts actions (e.g., triggered by a dedicated search button or null-intent context) that attempt to launch the dedicated Global Search app (e.g., `com.oppo.quicksearchbox`).
- **Logic:**
  - Checks if the `intent` package matches the global search app package or if the `intent` is `null`.
  - If it matches, retrieves the `Launcher` instance from the `SearchEntry` instance (via `mLauncher` field reflection).
  - Attempts to call `launcherInstance.showAllAppsFromIntent(true)`.
  - If that fails, attempts the static method `com.android.launcher3.taskbar.TaskbarUtils.showAllApps(Context)`.
  - If either succeeds, prevents the original `startSearchApp` method.

### 4. Swipe Down Search Redirect (`SwipeDownSearchRedirectHook.kt`)

- **Hook Target:** `com.oplus.launcher.FeatureFlags#showSearchBar(com.android.launcher3.Launcher launcher, Bundle args)`
- **Timing:** Hooked `before` the original method runs.
- **Purpose:** Intercepts swipe down search gestures that would normally open the default search interface and redirects them to the app drawer instead.
- **Logic:**
  - Checks if swipe down search redirect preference is enabled.
  - Sets a redirect-in-progress flag to prevent interference with `AutoFocusHook`.
  - Attempts to open the app drawer using `launcher.showAllAppsFromIntent(true)` or fallback methods.
  - If auto focus is enabled for swipe down redirect (`PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT`), automatically focuses the search input after a 100ms delay.
  - Cleans up pull down animation/blur overlay to ensure proper visual state.
  - Clears the redirect-in-progress flag after completion or failure.
- **Features:**
  - **Configurable Auto Focus:** Separate toggle for auto focus on swipe down redirect
  - **Conflict Prevention:** Uses redirect flag to prevent AutoFocusHook interference
  - **Visual Cleanup:** Properly handles animation state cleanup
  - **Robust Error Handling:** Graceful fallback and flag cleanup on failure

### 5. Fuzzy Search Override (`FuzzySearchHook.kt`)

- **Hook Target:** `com.android.launcher3.allapps.search.LauncherTaskbarAppsSearchContainerLayout#onSearchResult(String query, ArrayList<BaseAllAppsAdapter.AdapterItem> results)` (Class name might vary).
- **Timing:** Hooked `before` the original method runs.
- **Logic:**
  - If fuzzy search preference is enabled and query is not blank:
  - Retrieves the full app list (`List<AppInfo>`) via `getApps()` or fallback `getAllAppsStore().getApps()`.
  - Calculates match scores (Prefix > Substring > Subsequence) for all apps against the query.
  - Sorts matches by score (desc), then name (asc).
  - Creates new `AdapterItem` list from sorted matches using `BaseAllAppsAdapter.AdapterItem.asApp(AppInfo)` factory method.
  - Replaces original results list (`args[1]`) with the new list.

## Launcher Version Compatibility

### System Launcher 15.4.13 (SearchView-based)
- Uses `COUISearchView` (extends AndroidX SearchView)
- Enter key handling via `SearchView.onSubmitQuery()` hook
- Hook target: `androidx.appcompat.widget.SearchView#onSubmitQuery()`

### System Launcher 15.6.13+ (EditText-based)  
- Uses plain `EditText` with `OnEditorActionListener`
- Enter key handling via `OplusAllAppsSearchBarController.initialize()` hook
- Hook target: `com.android.launcher3.allapps.search.OplusAllAppsSearchBarController#initialize(...)`
- **Key Change:** OnePlus replaced SearchView with EditText, requiring new hook strategy

### Version Detection Strategy
The `EnterKeyLaunchHook` implements both hooks simultaneously:
- Both hooks attempt to register but only the applicable one will find its target class
- The unused hook fails gracefully with a debug log message
- This ensures compatibility across launcher updates without requiring version detection

## Reflection Strategies & Considerations

This module uses Java Reflection extensively.

- **YukiHookAPI Helpers vs. Standard Reflection:** Standard Java Reflection combined with YukiHookAPI's finders (`.give()`) often proved more robust during development than relying solely on YukiHookAPI's direct call/get helpers (`.call()`, `.get().any()`), which can be sensitive to type inference or API variations.

- **Current Implementation Approach:**
  - **Field Access:** Primarily uses standard Java Reflection (`getDeclaredField` loop, `setAccessible`, `field.get`). Used for `mLauncher`, `mActivityContext`, EditText field detection.
  - **Method Invocation (Static):** Uses Yuki's finder (`.method{}`) + `.give()` + standard `method.invoke(null, ...)`. Used for `TaskbarUtils.showAllApps`.
  - **Method Invocation (Instance):** Generally uses Yuki's `.call()` helper (e.g., `showAllAppsFromIntent`, `getApps`). Fallback to `.give().invoke(...)` if needed.
  - **Specific Exceptions:** Uses `field{}.get(instance).any()` for `mAppsView` in fuzzy search hook, as it was reliable in testing.

- **Cross-Version Robustness:**
  - Multiple fallback strategies for field access (try multiple field names)
  - Try-catch patterns for version-specific components
  - Graceful failure with informative logging

- **Troubleshooting Reflection Errors:** Crashes or failures after launcher updates likely stem from changed internal code.
  1. Check logs (`adb logcat -s OnePlusPlusLauncherHook`) for reflection errors.
  2. Decompile the launcher APK (e.g., with JADX) to verify class/field/method names/signatures.
  3. Update the reflection logic in the hook code accordingly.

## Development Notes

### OnePlus 15.6.13+ Compatibility Fix
The major breakthrough was discovering that OnePlus switched from SearchView to EditText in launcher 15.6.13+:
- **Old approach:** Hook `SearchView.onSubmitQuery()` - no longer works
- **New approach:** Hook `OplusAllAppsSearchBarController.initialize()` to access EditText parameter
- **Unified solution:** `EnterKeyLaunchHook` implements both hooks for automatic version compatibility
- **Fallback strategy:** Multiple methods to locate EditText and launcher instance for maximum robustness
- **Timing:** Hook `after` initialize completes to ensure EditText is fully configured

### Shared Utilities (`HookUtils.kt`)
Common functionality extracted to reduce code duplication:
- `getLauncherFromContext()` - Context traversal to find Launcher instance
- `launchFirstSearchResult()` - Generic search result launching logic
- `findFieldValue()` - Robust reflection field access with multiple fallbacks
- `setRedirectInProgress()` / `isRedirectInProgress()` - State management for redirect coordination
- `focusSearchInput()` - Unified search input focusing logic
- Logging utilities and constants