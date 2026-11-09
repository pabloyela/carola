package com.carola.player.data

import android.content.Context
import androidx.media3.common.Player

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("carola", Context.MODE_PRIVATE)

    var onlineCovers: Boolean
        get() = sp.getBoolean("online_covers", true)
        set(v) = sp.edit().putBoolean("online_covers", v).apply()

    var autoResume: Boolean
        get() = sp.getBoolean("auto_resume", true)
        set(v) = sp.edit().putBoolean("auto_resume", v).apply()

    var savedQueue: String?
        get() = sp.getString("queue", null)
        set(v) = sp.edit().putString("queue", v).apply()

    var savedIndex: Int
        get() = sp.getInt("index", 0)
        set(v) = sp.edit().putInt("index", v).apply()

    var savedPosition: Long
        get() = sp.getLong("position", 0L)
        set(v) = sp.edit().putLong("position", v).apply()

    var savedShuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(v) = sp.edit().putBoolean("shuffle", v).apply()

    var savedRepeat: Int
        get() = sp.getInt("repeat", Player.REPEAT_MODE_ALL)
        set(v) = sp.edit().putInt("repeat", v).apply()
}
