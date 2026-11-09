package com.carola.player.data

import java.io.File

const val UNKNOWN_ARTIST = "Artista desconocido"
const val VARIOUS_ARTISTS = "Varios artistas"

data class StorageRoot(
    val name: String,
    val path: String,
    val isInternal: Boolean,
)

data class Track(
    val path: String,
    val title: String,
    val artist: String?,
    /** Álbum según la etiqueta; null si el archivo no la trae. */
    val album: String?,
    val albumArtist: String?,
    val trackNo: Int,
    val discNo: Int,
    val durationMs: Long,
    val lastModified: Long,
    val size: Long,
) {
    val folder: String get() = File(path).parent ?: ""
    val albumKey: String get() = Keys.albumKey(album, albumArtist, folder)
}

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val tagged: Boolean,
    val tracks: List<Track>,
) {
    val folder: String get() = tracks.first().folder

    fun coverRequest(): CoverRequest {
        val first = tracks.first()
        return CoverRequest(
            key = key,
            folder = File(folder),
            sample = File(first.path),
            artist = artist.takeUnless { it == UNKNOWN_ARTIST || it == VARIOUS_ARTISTS },
            album = if (tagged) title else null,
            title = first.title,
        )
    }
}

data class Artist(val name: String, val albums: List<Album>)

data class CoverRequest(
    val key: String,
    val folder: File,
    val sample: File,
    val artist: String?,
    val album: String?,
    val title: String?,
)

data class Listing(
    val dir: File?,
    val dirs: List<File>,
    val files: List<File>,
    /** Subcarpetas que tienen música (directa o hasta dos niveles adentro). */
    val musicDirs: Set<String> = emptySet(),
)

object Keys {
    private val spaces = Regex("\\s+")
    fun norm(s: String): String = s.lowercase().trim().replace(spaces, " ")

    /**
     * Agrupa canciones en álbumes:
     * - con artista del álbum: artista + álbum (une discos en carpetas distintas)
     * - solo con nombre de álbum: álbum + carpeta (recopilaciones)
     * - sin etiquetas: la carpeta es el álbum
     */
    fun albumKey(album: String?, albumArtist: String?, folder: String): String = when {
        album.isNullOrBlank() -> "dir|$folder"
        !albumArtist.isNullOrBlank() -> "aa|${norm(albumArtist)}|${norm(album)}"
        else -> "al|${norm(album)}|$folder"
    }
}

/** Orden "natural": 2 - x va antes que 10 - y. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val cb = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0')
                val ny = y.trimStart('0')
                if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
            } else {
                x.compareTo(y)
            }
            if (c != 0) return c
        }
        return ca.size - cb.size
    }
}

fun buildAlbums(tracks: List<Track>): List<Album> =
    tracks.groupBy { it.albumKey }.map { (key, list) ->
        val sorted = list.sortedWith(
            compareBy<Track>({ it.discNo }, { it.trackNo })
                .thenComparator { a, b -> NaturalOrder.compare(File(a.path).name, File(b.path).name) }
        )
        val first = sorted.first()
        val artist = sorted.firstNotNullOfOrNull { it.albumArtist } ?: run {
            val counts = sorted.mapNotNull { it.artist }.groupingBy { it }.eachCount()
            if (counts.isEmpty()) {
                UNKNOWN_ARTIST
            } else {
                val top = counts.maxBy { it.value }
                if (counts.size > 1 && top.value * 2 < sorted.size) VARIOUS_ARTISTS else top.key
            }
        }
        Album(
            key = key,
            title = first.album ?: File(first.folder).name,
            artist = artist,
            tagged = first.album != null,
            tracks = sorted,
        )
    }.sortedWith(
        compareBy<Album, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    )

fun buildArtists(albums: List<Album>): List<Artist> =
    albums.groupBy { it.artist }
        .map { Artist(it.key, it.value) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
