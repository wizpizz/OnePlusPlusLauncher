# <center>OnePlusPlusLauncher Xposed Module</center>
![GitHub Release](https://img.shields.io/github/v/release/wizpizz/OnePlusPlusLauncher?style=for-the-badge)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/wizpizz/OnePlusPlusLauncher/debug_build.yml?style=for-the-badge&label=DEBUG%20BUILD)
![GitHub License](https://img.shields.io/github/license/wizpizz/OnePlusPlusLauncher?style=for-the-badge)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/wizpizz/OnePlusPlusLauncher/total?style=for-the-badge)
![GitHub Repo stars](https://img.shields.io/github/stars/wizpizz/OnePlusPlusLauncher?style=for-the-badge)


## Overview

OnePlusPlusLauncher is an Xposed/LSPosed module for the System Launcher on OxygenOS 15 that hooks into the application using the [YukiHookAPI](https://github.com/HighCapable/YuKiHookAPI) framework. It modifies app drawer search functions: automating keyboard display, enabling instant app launch from search, redirecting search actions to the app drawer, and providing optional fuzzy search.

- tested on System Launcher 15.4.13, 15.6.13 / OxygenOS 15 (should theoretically work on ColorOS as well)

**Please star the repository, if you enjoy using the module! It goes a long way ⭐**

## Features

*   ⌨️ **Automatic Keyboard / Searchbar Focus:** Automatically displays the keyboard when the app drawer is opened and search is focused. Can be toggled separately for opening app drawer by swiping up or redirecting from the Global Search Button.
*   ↩️ **App Launch on Enter:** Launches the first search result directly when the "Enter" key or search action button on the keyboard is pressed.
*   🔎 **Global Search Button Redirect:** Intercepts the search button in the homescreen that would normally open the dedicated global search app, redirecting to the main app drawer instead.
*   📱 **Swipe Down Search Redirect:** Intercepts swipe down search gestures and redirects them to the app drawer instead of the default search interface. Includes optional auto focus for seamless search experience.
*   🍑 **Fuzzy Search:** Replaces the default search logic with a ranked fuzzy search algorithm for more flexible matching.
*   ⚙️ **Configuration UI:** Allows toggling features individually, including auto focus options for different interaction methods.

## To-Do

*   Rewrite the module UI using Jetpack Compose instead of the current Android Views/XML implementation.
*   A decent app icon 

## Troubleshooting / Known Issues

*   **Compatibility / Launcher Updates:** Launcher updates may break hooks. Class names, field names, or method signatures might change, requiring updates to the module.

