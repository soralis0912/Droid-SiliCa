package org.soralis.droidsillica.util

import android.content.Context

object ExpertModeManager {

    private const val PREFS_NAME = "expert_mode_prefs"
    private const val KEY_EXPERT_MODE = "expert_mode_enabled"

    fun isExpertModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EXPERT_MODE, false)
    }

    fun setExpertModeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EXPERT_MODE, enabled).apply()
    }
}
