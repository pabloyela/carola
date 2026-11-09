package com.carola.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class ScanState(val running: Boolean = false, val done: Int = 0, val total: Int = 0)

/**
 * Biblioteca de canciones leída de las USB. Las etiquetas se guardan en caché
 * (library.json) para que reconectar la USB sea casi instantáneo.
 */
class LibraryRepository(context: Context) {

    private val cacheFile = File(context.filesDir, "library.json")
    private val cache = ConcurrentHashMap<String, Track>()
    private val scanMutex = Mutex()
    @Volatile private var cacheLoaded = false

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    fun find(path: String): Track? = cache[path]

    suspend fun loadCache(): Unit = withContext(Dispatchers.IO) {
        if (cacheLoaded) return@withContext
        cacheLoaded = true
        runCatching {
            val arr = JSONArray(cacheFile.readText())
            for (i in 0 until arr.length()) {
                val t = fromJson(arr.getJSONObject(i))
                cache[t.path] = t
            }
        }
        if (_tracks.value.isEmpty()) {
            _tracks.value = cache.values.filter { File(it.path).exists() }
        }
    }

    suspend fun scan(roots: List<StorageRoot>, extraFolders: List<File> = emptyList()) {
        if (scanMutex.isLocked) return
        scanMutex.withLock {
            withContext(Dispatchers.IO) {
                loadCache()
                _scan.value = ScanState(running = true)
                val found = LinkedHashSet<File>()
                roots.forEach { root ->
                    val base = if (root.isInternal) File(root.path, "Music") else File(root.path)
                    if (base.isDirectory) {
                        val list = mutableListOf<File>()
                        AudioFiles.walk(base, list)
                        found += list
                    }
                }
                // Carpetas marcadas por el usuario como "de música"
                extraFolders.filter { it.isDirectory }.forEach { folder ->
                    val list = mutableListOf<File>()
                    AudioFiles.walk(folder, list)
                    found += list
                }
                val files = found.toList()
                _scan.value = ScanState(true, 0, files.size)
                val result = ArrayList<Track>(files.size)
                files.forEachIndexed { i, f ->
                    val p = f.absolutePath
                    val cached = cache[p]
                    val t = if (cached != null && cached.lastModified == f.lastModified() && cached.size == f.length()) {
                        cached
                    } else {
                        TagReader.read(f).also { cache[p] = it }
                    }
                    result += t
                    if (i % 25 == 0) _scan.value = ScanState(true, i, files.size)
                }
                _tracks.value = result
                save()
                _scan.value = ScanState(false, files.size, files.size)
            }
        }
    }

    private fun save() {
        runCatching {
            val arr = JSONArray()
            cache.values.forEach { arr.put(toJson(it)) }
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(cacheFile)
        }
    }

    private fun toJson(t: Track) = JSONObject().apply {
        put("p", t.path)
        put("t", t.title)
        put("a", t.artist ?: JSONObject.NULL)
        put("al", t.album ?: JSONObject.NULL)
        put("aa", t.albumArtist ?: JSONObject.NULL)
        put("n", t.trackNo)
        put("d", t.discNo)
        put("du", t.durationMs)
        put("m", t.lastModified)
        put("s", t.size)
    }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

    private fun fromJson(o: JSONObject) = Track(
        path = o.getString("p"),
        title = o.getString("t"),
        artist = o.str("a"),
        album = o.str("al"),
        albumArtist = o.str("aa"),
        trackNo = o.optInt("n"),
        discNo = o.optInt("d"),
        durationMs = o.optLong("du"),
        lastModified = o.optLong("m"),
        size = o.optLong("s"),
    )
}

object TagReader {
    private val leadingNumber = Regex("^\\s*(\\d{1,3}\\s*[-._)]|\\d{2}\\s)\\s*")
    private val digits = Regex("^\\s*(\\d{1,3})")

    fun read(file: File): Track {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var trackNo = 0
        var discNo = 0
        var duration = 0L
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            title = r.tag(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = r.tag(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            albumArtist = r.tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            trackNo = num(r.tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
            discNo = num(r.tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER))
            duration = r.tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            // Archivo sin etiquetas legibles: se usa el nombre del archivo
        } finally {
            try { r.release() } catch (e: Exception) { }
        }

        val base = file.nameWithoutExtension
        val cleaned = base.replace(leadingNumber, "").ifBlank { base }
        var finalTitle: String = title ?: cleaned
        if (title == null && artist == null && cleaned.contains(" - ")) {
            artist = cleaned.substringBefore(" - ").trim().ifBlank { null }
            finalTitle = cleaned.substringAfter(" - ").trim().ifBlank { cleaned }
        }
        if (trackNo == 0) {
            trackNo = digits.find(base)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        return Track(
            path = file.absolutePath,
            title = finalTitle,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            trackNo = trackNo,
            discNo = discNo,
            durationMs = duration,
            lastModified = file.lastModified(),
            size = file.length(),
        )
    }

    private fun MediaMetadataRetriever.tag(key: Int): String? =
        extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }

    private fun num(s: String?): Int = s?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
}
