package com.carola.player.data

import java.io.File
import java.text.Normalizer

data class SearchResults(
    val query: String = "",
    val tracks: List<Pair<Track, Album>> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<File> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && folders.isEmpty()
}

/** Índice de búsqueda precalculado: ignora mayúsculas y acentos ("mana" encuentra "Maná"). */
class SearchIndex(albums: List<Album>, artists: List<Artist>) {

    private class Entry<T>(val item: T, val text: String)

    private val trackEntries = albums.flatMap { a ->
        a.tracks.map { t -> Entry(t to a, norm("${t.title} ${t.artist.orEmpty()} ${t.album.orEmpty()} ${a.artist}")) }
    }
    private val albumEntries = albums.map { Entry(it, norm("${it.title} ${it.artist}")) }
    private val artistEntries = artists.map { Entry(it, norm(it.name)) }
    private val folderEntries = albums.map { File(it.folder) }
        .distinctBy { it.absolutePath }
        .map { Entry(it, norm(it.name)) }

    fun search(query: String): SearchResults {
        val words = norm(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return SearchResults(query)
        fun match(text: String) = words.all { text.contains(it) }
        return SearchResults(
            query = query,
            tracks = trackEntries.filter { match(it.text) }.take(60).map { it.item },
            albums = albumEntries.filter { match(it.text) }.take(30).map { it.item },
            artists = artistEntries.filter { match(it.text) }.take(20).map { it.item },
            folders = folderEntries.filter { match(it.text) }.take(20).map { it.item },
        )
    }

    companion object {
        private val marks = Regex("\\p{Mn}+")
        private val spaces = Regex("\\s+")
        val EMPTY = SearchIndex(emptyList(), emptyList())

        fun norm(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(marks, "")
                .lowercase()
                .replace(spaces, " ")
                .trim()
    }
}
