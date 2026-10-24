package org.amnezia.awg.preference

import android.content.Context
import android.content.SharedPreferences

object ServerPreferences {
    private const val PREFS_NAME = "zaza_server_preferences"
    private const val KEY_PINNED_SERVERS = "pinned_servers"
    private const val KEY_SPEED_WARNING_PREFIX = "speed_warning_suppressed_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPinned(context: Context, serverId: String): Boolean {
        val pinnedSet = getPrefs(context).getStringSet(KEY_PINNED_SERVERS, emptySet()) ?: emptySet()
        return pinnedSet.contains(serverId)
    }

    fun setPinned(context: Context, serverId: String, pinned: Boolean) {
        val prefs = getPrefs(context)
        val currentSet = HashSet(prefs.getStringSet(KEY_PINNED_SERVERS, emptySet()) ?: emptySet())
        if (pinned) {
            currentSet.add(serverId)
        } else {
            currentSet.remove(serverId)
        }
        prefs.edit().putStringSet(KEY_PINNED_SERVERS, currentSet).apply()
    }

    fun isSpeedWarningSuppressed(context: Context, serverId: String): Boolean {
        return getPrefs(context).getBoolean(KEY_SPEED_WARNING_PREFIX + serverId, false)
    }

    fun setSpeedWarningSuppressed(context: Context, serverId: String, suppressed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SPEED_WARNING_PREFIX + serverId, suppressed).apply()
    }
}
