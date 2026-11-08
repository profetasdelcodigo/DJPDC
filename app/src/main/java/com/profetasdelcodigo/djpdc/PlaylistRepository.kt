package com.profetasdelcodigo.djpdc

import android.content.Context

class PlaylistRepository(context: Context) {
    private val preferences = context.getSharedPreferences("djpdc_playlists", Context.MODE_PRIVATE)

    fun names(): List<String> = preferences.getStringSet(KEY_NAMES, emptySet())
        .orEmpty()
        .toList()
        .sortedBy { it.lowercase() }

    fun save(name: String, trackIds: List<Long>) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || trackIds.isEmpty()) return
        val names = preferences.getStringSet(KEY_NAMES, emptySet()).orEmpty().toMutableSet()
        names.add(cleanName)
        preferences.edit()
            .putStringSet(KEY_NAMES, names)
            .putStringSet(playlistKey(cleanName), trackIds.map { it.toString() }.toSet())
            .apply()
    }

    fun trackIds(name: String): Set<Long> = preferences
        .getStringSet(playlistKey(name), emptySet())
        .orEmpty()
        .mapNotNull { it.toLongOrNull() }
        .toSet()

    companion object {
        private const val KEY_NAMES = "playlist_names"
        private fun playlistKey(name: String) = "playlist_${name.lowercase()}"
    }
}
