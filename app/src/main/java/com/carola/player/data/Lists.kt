package com.carola.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.UUID

data class UserPlaylist(val id: String, val name: String, val paths: List<String>)

data class ResumePoint(val path: String, val positionMs: Long, val updated: Long)

data class ListsState(
    val favorites: List<String> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val playlists: List<UserPlaylist> = emptyList(),
    val resume: Map<String, ResumePoint> = emptyMap(),
) {
    val favoriteSet: Set<String> by lazy { favorites.toSet() }

    /** Las 50 canciones más escuchadas. */
    val topPaths: List<String> by lazy {
        counts.entries.sortedByDescending { it.value }.take(50).map { it.key }
    }
}

enum class ListKind { FAVORITES, TOP, USER, M3U }

/** Lista lista para mostrar: favoritas, más escuchadas, tuyas o de la USB (.m3u). */
data class PlaylistView(val id: String, val name: String, val kind: ListKind, val paths: List<String>)

/** Una canción dentro de una lista; [available] = false si el archivo no está (USB desconectada, borrado...). */
data class ListEntry(
    val path: String,
    val title: String,
    val subtitle: String?,
    val durationMs: Long,
    val available: Boolean,
)

/**
 * Guarda favoritos, conteo de reproducciones, listas propias y puntos de "Continuar"
 * en lists.json. Las rutas que ya no existan se conservan (se muestran en gris).
 */
class ListsRepository(context: Context) {

    private val file = File(context.filesDir, "lists.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ListsState> = _state.asStateFlow()

    // ----- Favoritos y conteo -----

    fun toggleFavorite(path: String) = change { s ->
        val fav = if (path in s.favoriteSet) s.favorites - path else listOf(path) + s.favorites
        s.copy(favorites = fav)
    }

    fun addFavorites(paths: List<String>) = change { s ->
        s.copy(favorites = paths.filter { it !in s.favoriteSet }.distinct() + s.favorites)
    }

    fun removeFavorite(path: String) = change { it.copy(favorites = it.favorites - path) }

    fun addPlay(path: String) = change { s ->
        s.copy(counts = s.counts + (path to (s.counts[path] ?: 0) + 1))
    }

    fun resetCounts() = change { it.copy(counts = emptyMap()) }

    // ----- Listas propias -----

    fun create(name: String, paths: List<String> = emptyList()): String {
        val id = "u:" + UUID.randomUUID().toString()
        change { it.copy(playlists = it.playlists + UserPlaylist(id, name.trim().ifEmpty { "Mi lista" }, paths.distinct())) }
        return id
    }

    fun addTo(id: String, paths: List<String>) = changeList(id) { p ->
        p.copy(paths = p.paths + paths.filter { it !in p.paths })
    }

    fun removeAt(id: String, index: Int) = changeList(id) { p ->
        if (index in p.paths.indices) p.copy(paths = p.paths.toMutableList().apply { removeAt(index) }) else p
    }

    fun rename(id: String, name: String) = changeList(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) = change { s -> s.copy(playlists = s.playlists.filter { it.id != id }) }

    // ----- Continuar por carpeta / álbum -----

    fun setResume(key: String, path: String, positionMs: Long) = change { s ->
        var map = s.resume + (key to ResumePoint(path, positionMs, System.currentTimeMillis()))
        if (map.size > 300) {
            map = map.entries.sortedByDescending { it.value.updated }.take(250).associate { it.key to it.value }
        }
        s.copy(resume = map)
    }

    fun clearResume(key: String) = change { it.copy(resume = it.resume - key) }

    // ----- Guardado -----

    private fun changeList(id: String, f: (UserPlaylist) -> UserPlaylist) = change { s ->
        s.copy(playlists = s.playlists.map { if (it.id == id) f(it) else it })
    }

    private fun change(f: (ListsState) -> ListsState) {
        _state.update(f)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            write(_state.value)
        }
    }

    private fun write(s: ListsState) {
        runCatching {
            val o = JSONObject()
            o.put("fav", JSONArray(s.favorites))
            o.put("counts", JSONObject().apply { s.counts.forEach { (k, v) -> put(k, v) } })
            o.put("lists", JSONArray().apply {
                s.playlists.forEach { p ->
                    put(JSONObject().put("id", p.id).put("name", p.name).put("paths", JSONArray(p.paths)))
                }
            })
            o.put("resume", JSONObject().apply {
                s.resume.forEach { (k, r) ->
                    put(k, JSONObject().put("p", r.path).put("t", r.positionMs).put("u", r.updated))
                }
            })
            val tmp = File(file.path + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }

    private fun load(): ListsState = runCatching {
        val o = JSONObject(file.readText())
        fun strings(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }
        val counts = mutableMapOf<String, Int>()
        o.optJSONObject("counts")?.let { c -> c.keys().forEach { counts[it] = c.optInt(it) } }
        val lists = mutableListOf<UserPlaylist>()
        o.optJSONArray("lists")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                lists += UserPlaylist(p.getString("id"), p.getString("name"), strings(p.optJSONArray("paths")))
            }
        }
        val resume = mutableMapOf<String, ResumePoint>()
        o.optJSONObject("resume")?.let { r ->
            r.keys().forEach { k ->
                val x = r.getJSONObject(k)
                resume[k] = ResumePoint(x.getString("p"), x.optLong("t"), x.optLong("u"))
            }
        }
        ListsState(strings(o.optJSONArray("fav")), counts, lists, resume)
    }.getOrDefault(ListsState())
}

/** Lee listas .m3u / .m3u8 (incluidas las hechas en Windows con rutas tipo D:\Musica\...). */
object M3u {
    private val drive = Regex("^[A-Za-z]:/")

    fun read(file: File, storageRoot: String?): PlaylistView? = runCatching {
        val text = decode(file.readBytes())
        val dir = file.parentFile ?: return null
        val paths = text.lineSequence()
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { resolve(it, dir, storageRoot) }
            .distinct()
            .toList()
        if (paths.isEmpty()) null
        else PlaylistView("m3u:" + file.absolutePath, file.nameWithoutExtension, ListKind.M3U, paths)
    }.getOrNull()

    private fun resolve(line: String, dir: File, root: String?): String? {
        var p = line.replace('\\', '/')
        if (p.startsWith("file://")) p = android.net.Uri.decode(p.removePrefix("file://"))
        if (p.contains("://")) return null // radios por internet, no aplica
        val candidates = mutableListOf<File>()
        when {
            drive.containsMatchIn(p) -> {
                // Ruta de Windows: probar desde la raíz de la USB y junto a la lista
                val noDrive = p.replace(drive, "")
                root?.let { candidates += File(it, noDrive) }
                candidates += File(dir, noDrive)
                candidates += File(dir, File(noDrive).name)
            }
            p.startsWith("/") -> {
                candidates += File(p)
                root?.let { candidates += File(it, p.trimStart('/')) }
            }
            else -> candidates += File(dir, p)
        }
        val found = candidates.firstOrNull { it.isFile } ?: candidates.first()
        return found.normalize().absolutePath
    }

    /** UTF-8 si es válido; si no, Windows-1252 (las .m3u viejas de Windows). */
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1252"))
    }
}
