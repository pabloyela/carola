package com.carola.player

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.carola.player.data.Album
import com.carola.player.data.Artist
import com.carola.player.data.AudioFiles
import com.carola.player.data.CoverRepository
import com.carola.player.data.CoverRequest
import com.carola.player.data.Keys
import com.carola.player.data.LibraryRepository
import com.carola.player.data.Listing
import com.carola.player.data.Palettes
import com.carola.player.data.Prefs
import com.carola.player.data.ScanState
import com.carola.player.data.SearchIndex
import com.carola.player.data.SearchResults
import com.carola.player.data.StorageRoot
import com.carola.player.data.StorageRoots
import com.carola.player.data.Track
import com.carola.player.data.buildAlbums
import com.carola.player.data.buildArtists
import com.carola.player.player.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.random.Random

data class PlaybackUi(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val path: String? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val index: Int = 0,
    val count: Int = 0,
    val queueVersion: Int = 0,
)

data class SettingsUi(
    val onlineCovers: Boolean,
    val autoResume: Boolean,
    val scanWholeUsb: Boolean,
    val normalize: Boolean,
)

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    val library = LibraryRepository(app)
    val covers = CoverRepository(app, prefs)

    private val _roots = MutableStateFlow<List<StorageRoot>>(emptyList())
    val roots: StateFlow<List<StorageRoot>> = _roots.asStateFlow()

    private val _folder = MutableStateFlow<File?>(null)
    val folder: StateFlow<File?> = _folder.asStateFlow()

    private val _listing = MutableStateFlow(Listing(null, emptyList(), emptyList()))
    val listing: StateFlow<Listing> = _listing.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackUi())
    val playback: StateFlow<PlaybackUi> = _playback.asStateFlow()

    private val _cover = MutableStateFlow<File?>(null)
    val cover: StateFlow<File?> = _cover.asStateFlow()

    private val _accent = MutableStateFlow<Int?>(null)
    val accent: StateFlow<Int?> = _accent.asStateFlow()

    private val _settings = MutableStateFlow(SettingsUi(prefs.onlineCovers, prefs.autoResume, prefs.scanWholeUsb, prefs.normalize))
    val settings: StateFlow<SettingsUi> = _settings.asStateFlow()

    val scan: StateFlow<ScanState> = library.scan

    private val _musicFolders = MutableStateFlow(prefs.musicFolders)
    /** Carpetas que el usuario marcó como "de música". */
    val musicFolders: StateFlow<Set<String>> = _musicFolders.asStateFlow()

    val albums: StateFlow<List<Album>> = library.tracks
        .map { buildAlbums(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = albums
        .map { buildArtists(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- Búsqueda ----------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val searchIndex: StateFlow<SearchIndex> = combine(albums, artists) { al, ar -> SearchIndex(al, ar) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchIndex.EMPTY)

    val searchResults: StateFlow<SearchResults> = combine(_query.debounce(200), searchIndex) { q, idx -> idx.search(q) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchResults())

    fun setQuery(q: String) {
        _query.value = q
    }

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var started = false
    private var restored = false
    private var queueVersion = 0
    private var currentCoverKey: String? = null
    private var coverJob: Job? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshRoots()
            if (intent.action == Intent.ACTION_MEDIA_MOUNTED) {
                viewModelScope.launch {
                    delay(1500) // algunos radios tardan en dejar lista la USB
                    refreshRoots()
                    tryRestore()
                    rescan()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        app.registerReceiver(usbReceiver, filter)
        connect()
        // Reintentar la portada de lo que suena cuando cambia el ajuste o vuelve el internet
        viewModelScope.launch {
            covers.version.drop(1).collect {
                if (_cover.value == null) {
                    currentCoverKey = null
                    update()
                }
            }
        }
        viewModelScope.launch {
            var ticks = 0
            while (isActive) {
                delay(500)
                val c = controller ?: continue
                if (c.isPlaying) {
                    _playback.update { it.copy(positionMs = c.currentPosition, durationMs = durationOf(c)) }
                    if (++ticks % 10 == 0) saveState()
                }
            }
        }
    }

    // ---------- Arranque ----------

    fun onPermissionGranted() {
        refreshRoots()
        if (started) return
        started = true
        viewModelScope.launch {
            library.loadCache()
            rescan()
        }
        tryRestore()
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication<Application>(), Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_TIMELINE_CHANGED)) queueVersion++
                    update()
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                        (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying)
                    ) {
                        saveState()
                    }
                }
            })
            update()
            if (hasPermission()) tryRestore()
        }, ContextCompat.getMainExecutor(app))
    }

    // ---------- Almacenamiento y carpetas ----------

    fun refreshRoots() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { StorageRoots.find(getApplication<Application>()) }
            _roots.value = r
            val current = _folder.value
            if (current != null && !withContext(Dispatchers.IO) { current.exists() }) openFolder(null)
        }
    }

    fun rescan() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { StorageRoots.find(getApplication<Application>()) }
            _roots.value = r
            library.scan(r, _musicFolders.value.map { File(it) }, prefs.scanWholeUsb)
        }
    }

    fun isMusicFolder(dir: File): Boolean = dir.absolutePath in _musicFolders.value

    /** Marca o desmarca una carpeta como "de música" y vuelve a escanear. */
    fun toggleMusicFolder(dir: File) {
        val path = dir.absolutePath
        val updated = _musicFolders.value.toMutableSet()
        if (!updated.add(path)) updated.remove(path)
        prefs.musicFolders = updated
        _musicFolders.value = updated
        rescan()
    }

    fun openFolder(dir: File?) {
        _folder.value = dir
        viewModelScope.launch {
            _listing.value = if (dir == null) {
                Listing(null, emptyList(), emptyList())
            } else {
                withContext(Dispatchers.IO) { AudioFiles.list(dir, markMusic = true) }
            }
        }
    }

    /** Sube un nivel. Devuelve false si ya está en la lista de memorias. */
    fun folderUp(): Boolean {
        val cur = _folder.value ?: return false
        val isRoot = _roots.value.any { it.path == cur.absolutePath }
        openFolder(if (isRoot) null else cur.parentFile)
        return true
    }

    fun rootFor(dir: File): StorageRoot? =
        _roots.value
            .filter { dir.absolutePath == it.path || dir.absolutePath.startsWith(it.path + "/") }
            .maxByOrNull { it.path.length }

    // ---------- Reproducción ----------

    fun playFiles(files: List<File>, start: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (files.isEmpty()) return
        viewModelScope.launch {
            val items = withContext(Dispatchers.Default) { files.map(::mediaItem) }
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(items, start.coerceIn(0, items.lastIndex), 0L)
            c.prepare()
            c.play()
            restored = true
            prefs.savedQueue = JSONArray(files.map { it.absolutePath }).toString()
            saveState()
        }
    }

    fun playTracks(tracks: List<Track>, start: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val files = tracks.map { File(it.path) }
        playFiles(files, if (shuffle) Random.nextInt(files.size) else start, shuffle)
    }

    fun playFolder(dir: File, shuffle: Boolean, recursive: Boolean) {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                if (recursive) mutableListOf<File>().also { AudioFiles.walk(dir, it) }
                else AudioFiles.list(dir).files
            }
            if (files.isNotEmpty()) {
                playFiles(files, if (shuffle) Random.nextInt(files.size) else 0, shuffle)
            }
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seek(ms: Long) {
        controller?.seekTo(ms)
        _playback.update { it.copy(positionMs = ms) }
    }
    fun playIndex(i: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(i)
        c.play()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun queueItem(i: Int): MediaItem? {
        val c = controller ?: return null
        return if (i in 0 until c.mediaItemCount) c.getMediaItemAt(i) else null
    }

    private fun mediaItem(f: File): MediaItem {
        val t = library.find(f.absolutePath)
        val md = MediaMetadata.Builder()
        if (t != null) {
            md.setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album ?: File(t.folder).name)
                .setAlbumArtist(t.albumArtist)
            if (t.trackNo > 0) md.setTrackNumber(t.trackNo)
        } else {
            md.setDisplayTitle(f.nameWithoutExtension)
        }
        val key = t?.albumKey ?: Keys.albumKey(null, null, f.parent ?: "")
        covers.peek(key)?.let { md.setArtworkUri(Uri.fromFile(it)) }
        return MediaItem.Builder()
            .setMediaId(f.absolutePath)
            .setUri(Uri.fromFile(f))
            .setMediaMetadata(md.build())
            .build()
    }

    private fun durationOf(c: Player): Long {
        val d = c.duration
        return if (d == C.TIME_UNSET || d < 0) 0 else d
    }

    private fun update() {
        val c = controller ?: return
        val md = c.mediaMetadata
        val item = c.currentMediaItem
        val path = item?.mediaId?.takeIf { it.isNotEmpty() }
        _playback.value = PlaybackUi(
            hasMedia = item != null,
            isPlaying = c.isPlaying,
            title = (md.title ?: md.displayTitle)?.toString()
                ?: path?.let { File(it).nameWithoutExtension } ?: "",
            artist = (md.artist ?: md.albumArtist)?.toString() ?: "",
            album = md.albumTitle?.toString() ?: path?.let { File(it).parentFile?.name } ?: "",
            path = path,
            durationMs = durationOf(c),
            positionMs = c.currentPosition,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            index = c.currentMediaItemIndex,
            count = c.mediaItemCount,
            queueVersion = queueVersion,
        )
        updateCover(path, md)
    }

    private fun updateCover(path: String?, md: MediaMetadata) {
        if (path == null) {
            currentCoverKey = null
            _cover.value = null
            _accent.value = null
            return
        }
        val file = File(path)
        val folder = file.parentFile ?: File("/")
        val t = library.find(path)
        val req = if (t != null) {
            CoverRequest(t.albumKey, folder, file, t.albumArtist ?: t.artist, t.album, t.title)
        } else {
            val album = md.albumTitle?.toString()
            val albumArtist = md.albumArtist?.toString()
            CoverRequest(
                key = Keys.albumKey(album, albumArtist, folder.absolutePath),
                folder = folder,
                sample = file,
                artist = (albumArtist ?: md.artist?.toString()),
                album = album,
                title = md.title?.toString(),
            )
        }
        if (req.key == currentCoverKey) return
        currentCoverKey = req.key
        coverJob?.cancel()
        val known = covers.peek(req.key)
        _cover.value = known
        coverJob = viewModelScope.launch {
            val f = known ?: covers.cover(req)
            _cover.value = f
            _accent.value = f?.let { Palettes.accentFrom(it) }
        }
    }

    // ---------- Guardar / reanudar ----------

    private fun saveState() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        prefs.savedIndex = c.currentMediaItemIndex
        prefs.savedPosition = c.currentPosition
        prefs.savedShuffle = c.shuffleModeEnabled
        prefs.savedRepeat = c.repeatMode
    }

    private fun tryRestore() {
        val c = controller ?: return
        if (restored || c.mediaItemCount > 0 || !hasPermission()) return
        val json = prefs.savedQueue ?: return
        restored = true
        viewModelScope.launch {
            val paths = runCatching {
                val a = JSONArray(json)
                List(a.length()) { a.getString(it) }
            }.getOrDefault(emptyList())
            if (paths.isEmpty()) return@launch
            val idx = prefs.savedIndex.coerceIn(0, paths.lastIndex)
            val current = paths[idx]
            val result = withContext(Dispatchers.IO) {
                if (!File(current).exists()) {
                    null
                } else {
                    val existing = paths.filter { File(it).exists() }
                    existing.map { mediaItem(File(it)) } to existing.indexOf(current)
                }
            }
            if (result == null) {
                restored = false // la USB todavía no está; se reintenta al montarse
                return@launch
            }
            if (c.mediaItemCount > 0) return@launch
            c.setMediaItems(result.first, result.second.coerceAtLeast(0), prefs.savedPosition)
            c.shuffleModeEnabled = prefs.savedShuffle
            c.repeatMode = prefs.savedRepeat
            c.prepare()
            if (prefs.autoResume) c.play()
        }
    }

    // ---------- Ajustes ----------

    fun setOnlineCovers(v: Boolean) {
        prefs.onlineCovers = v
        _settings.value = _settings.value.copy(onlineCovers = v)
        covers.refresh()
    }

    fun setScanWholeUsb(v: Boolean) {
        prefs.scanWholeUsb = v
        _settings.value = _settings.value.copy(scanWholeUsb = v)
        rescan()
    }

    fun setNormalize(v: Boolean) {
        prefs.normalize = v // el servicio de reproducción escucha este cambio
        _settings.value = _settings.value.copy(normalize = v)
    }

    fun setAutoResume(v: Boolean) {
        prefs.autoResume = v
        _settings.value = _settings.value.copy(autoResume = v)
    }

    fun clearCovers() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { covers.clear() }
            currentCoverKey = null
            _cover.value = null
            update()
        }
    }

    override fun onCleared() {
        saveState()
        runCatching { getApplication<Application>().unregisterReceiver(usbReceiver) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
