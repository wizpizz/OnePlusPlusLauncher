# <center>OnePlusPlusLauncher Xposed Module</center>

## Overview

OnePlusPlusLauncher is an Xposed/LSPosed module for specific Android launchers (tested on System Launcher 15.4.13 / OxygenOS 15) that hooks into the application using the [YukiHookAPI](https://github.com/HighCapable/YuKiHookAPI) framework. It modifies app drawer search functions: automating keyboard display, enabling instant app launch from search, redirecting the global search button to the app drawer, and providing optional fuzzy search.

## Features

*   ⌨️ **Automatic Keyboard / Searchbar Focus:** Automatically displays the keyboard when the app drawer is opened and search is focused.
*   ↩️ **App Launch on Enter:** Launches the first search result directly when the "Enter" key or search action button on the keyboard is pressed.
*   🔎 **Global Search Button Redirect:** Intercepts the search button in the homescreeen that would normally open the dedicated global search app, redirecting to the main app drawer instead.
*   🍑 **Fuzzy Search (Optional):** Replaces the default search logic with a ranked fuzzy search algorithm for more flexible matching.
*   ⚙️ **Configuration UI:** Allows toggling features (Hide Icon, Fuzzy Search).

## Troubleshooting / Known Issues

*   **Compatibility / Launcher Updates:** Launcher updates may break hooks. Class names, field names, or method signatures might change, requiring updates to the module.

## To-Do

*   Rewrite the Configuration UI using Jetpack Compose instead of the current Android Views/XML implementation.
