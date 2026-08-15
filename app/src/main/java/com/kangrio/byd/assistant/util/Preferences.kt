package com.kangrio.byd.assistant.util

import android.content.Context
import android.content.SharedPreferences
import com.kangrio.byd.assistant.Constant
import androidx.core.content.edit

object Preferences {
    lateinit var prefs: SharedPreferences
    fun init(context: Context) {
        prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    var startHotword: Boolean
        get() = prefs.getBoolean(Constant.PREFS_START_HOTWORD, true)
        set(value) = prefs.edit { putBoolean(Constant.PREFS_START_HOTWORD, value) }

    var hotwordSensitivity: Float
        get() = prefs.getFloat(Constant.PREFS_HOTWORD_SENSITIVITY, 0.5f)
        set(value) = prefs.edit { putFloat(Constant.PREFS_HOTWORD_SENSITIVITY, value) }

    var hotwordGain: Float
        get() = prefs.getFloat(Constant.PREFS_HOTWORD_GAIN, 1.0f)
        set(value) = prefs.edit { putFloat(Constant.PREFS_HOTWORD_GAIN, value) }

    var playDingOnStart: Boolean
        get() = prefs.getBoolean(Constant.PREFS_PLAY_DING_ON_START, true)
        set(value) = prefs.edit { putBoolean(Constant.PREFS_PLAY_DING_ON_START, value) }
}