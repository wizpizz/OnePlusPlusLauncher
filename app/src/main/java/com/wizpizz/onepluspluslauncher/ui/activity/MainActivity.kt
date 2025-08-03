package com.wizpizz.onepluspluslauncher.ui.activity


import androidx.core.view.isVisible
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import com.wizpizz.onepluspluslauncher.BuildConfig
import com.wizpizz.onepluspluslauncher.R
import com.wizpizz.onepluspluslauncher.databinding.ActivityMainBinding
import com.wizpizz.onepluspluslauncher.ui.activity.base.BaseActivity
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_SWIPE
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_ENTER_KEY_LAUNCH
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_GLOBAL_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_SWIPE_DOWN_SEARCH_REDIRECT
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.PREF_USE_FUZZY_SEARCH

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val prefs: YukiHookPrefsBridge by lazy { prefs() }

    override fun onCreate() {
        refreshModuleStatus()
        binding.mainTextVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)
        
        // Setup feature toggles - all enabled by default
        setupFeatureToggle(binding.autoFocusSearchSwipeSwitch, PREF_AUTO_FOCUS_SEARCH_SWIPE)
        setupFeatureToggle(binding.autoFocusSearchRedirectSwitch, PREF_AUTO_FOCUS_SEARCH_REDIRECT)
        setupFeatureToggle(binding.autoFocusSwipeDownRedirectSwitch, PREF_AUTO_FOCUS_SWIPE_DOWN_REDIRECT)
        setupFeatureToggle(binding.enterKeyLaunchSwitch, PREF_ENTER_KEY_LAUNCH)
        setupFeatureToggle(binding.globalSearchRedirectSwitch, PREF_GLOBAL_SEARCH_REDIRECT)
        setupFeatureToggle(binding.swipeDownSearchRedirectSwitch, PREF_SWIPE_DOWN_SEARCH_REDIRECT)
        setupFeatureToggle(binding.fuzzySearchSwitchNew, PREF_USE_FUZZY_SEARCH)
    }

    /**
     * Setup a feature toggle switch
     * @param switch The MaterialSwitch to setup
     * @param prefKey The preference key to store the value
     * Note: All features default to enabled (true)
     */
    private fun setupFeatureToggle(
        switch: com.wizpizz.onepluspluslauncher.ui.view.MaterialSwitch,
        prefKey: String
    ) {
        switch.isChecked = prefs.getBoolean(prefKey, true)
        switch.setOnCheckedChangeListener { button, isChecked ->
            if (button.isPressed) {
                prefs.native().edit { putBoolean(prefKey, isChecked) }
            }
        }
    }



    /**
     * Refresh module status
     *
     * 刷新模块状态
     */
    private fun refreshModuleStatus() {
        binding.mainLinStatus.setBackgroundResource(
            when {
                YukiHookAPI.Status.isXposedModuleActive -> R.drawable.bg_green_round
                else -> R.drawable.bg_dark_round
            }
        )
        binding.mainImgStatus.setImageResource(
            when {
                YukiHookAPI.Status.isXposedModuleActive -> R.mipmap.ic_success
                else -> R.mipmap.ic_warn
            }
        )
        binding.mainTextStatus.text = getString(
            when {
                YukiHookAPI.Status.isXposedModuleActive -> R.string.module_is_activated
                else -> R.string.module_not_activated
            }
        )
        binding.mainTextApiWay.isVisible = YukiHookAPI.Status.isXposedModuleActive
        binding.mainTextApiWay.text = if (YukiHookAPI.Status.Executor.apiLevel > 0)
            "Activated by ${YukiHookAPI.Status.Executor.name} API ${YukiHookAPI.Status.Executor.apiLevel}"
        else "Activated by ${YukiHookAPI.Status.Executor.name}"
    }
}