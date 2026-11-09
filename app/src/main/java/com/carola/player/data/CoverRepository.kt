package com.carola.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Busca la portada de un álbum en este orden:
 *  1. Portada ya guardada en caché
 *  2. Imagen en la carpeta (cover.jpg, folder.jpg, front.jpg...)
 *  3. Imagen incrustada en la canción (ID3 / FLAC)
 *  4. MusicBrainz + Cover Art Archive (si hay internet y está activado)
 */
class CoverRepository(context: Context, private val prefs: Prefs) {

    private val dir = File(context.filesDir, "covers").apply { mkdirs() }
    private val mb = MusicBrainzClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolved = ConcurrentHashMap<String, File>()
    private val inflight = ConcurrentHashMap<String, Deferred<File?>>()
    @Volatile private var offlineUntil = 0L
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _version = MutableStateFlow(0)
    /** Cambia cuando conviene volver a intentar portadas (internet volvió, ajuste cambiado...). */
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        // Cuando vuelve el internet, reintentar las portadas que faltan
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    offlineUntil = 0L
                    _version.value = _version.value + 1
                }
            })
        }
    }

    fun refresh() {
        offlineUntil = 0L
        _version.value = _version.value + 1
    }

    private fun hasInternet(): Boolean {
        val manager = cm ?: return true
        return try {
            val n = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(n) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    private val imageExt = setOf("jpg", "jpeg", "png")
    private val preferredNames = listOf(
        "cover", "folder", "front", "portada", "caratula", "carátula", "albumart", "album",
    )
    private val yearInBrackets = Regex("[\\(\\[]\\s*\\d{4}\\s*[\\)\\]]")

    /** Respuesta inmediata si ya se conoce la portada (sin red). */
    fun peek(key: String): File? {
        resolved[key]?.let { return it }
        val f = cacheFile(key)
        if (f.length() > 0) {
            resolved[key] = f
            return f
        }
        return null
    }

    suspend fun cover(req: CoverRequest): File? {
        peek(req.key)?.let { return it }
        val d = inflight.computeIfAbsent(req.key) {
            val job = scope.async(start = CoroutineStart.LAZY) { resolve(req) }
            job.invokeOnCompletion { inflight.remove(req.key, job) }
            job
        }
        d.start()
        val r = d.await()
        if (r != null) resolved[req.key] = r
        return r
    }

    fun clear() {
        resolved.clear()
        dir.listFiles()?.forEach { it.delete() }
        refresh()
    }

    private suspend fun resolve(req: CoverRequest): File? {
        val out = cacheFile(req.key)
        if (out.length() > 0) return out

        folderImage(req.folder)?.let { return it }
        embedded(req.sample)?.let { bytes -> if (saveScaled(bytes, out)) return out }

        if (!prefs.onlineCovers) return null
        if (System.currentTimeMillis() < offlineUntil) return null
        if (!hasInternet()) return null // sin internet: no busca ni marca como "no encontrada"

        var artist = req.artist?.takeIf { it.isNotBlank() }
        var album = req.album?.takeIf { it.isNotBlank() }
        if (album == null) {
            val parsed = parseFolderName(req.folder.name)
            if (parsed != null) {
                if (artist == null) artist = parsed.first
                album = parsed.second
            }
        }
        val searchArtist = artist ?: return null

        val miss = File(dir, hash(req.key) + ".miss")
        val threeDays = 3L * 24 * 3600 * 1000
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < threeDays) return null

        when (val r = mb.findCover(searchArtist, album, req.title)) {
            is MusicBrainzClient.Result.Found -> {
                if (saveScaled(r.bytes, out)) {
                    miss.delete()
                    return out
                }
            }
            MusicBrainzClient.Result.NotFound -> runCatching { miss.writeText("") }
            MusicBrainzClient.Result.Error -> offlineUntil = System.currentTimeMillis() + 60_000
        }
        return null
    }

    /** "Queen - Greatest Hits (1981)" → (Queen, Greatest Hits) */
    private fun parseFolderName(name: String): Pair<String, String>? {
        if (!name.contains(" - ")) return null
        val a = name.substringBefore(" - ").trim()
        val b = name.substringAfter(" - ").replace(yearInBrackets, "").trim()
        if (a.isEmpty() || b.isEmpty() || a.all { it.isDigit() }) return null
        return a to b
    }

    private fun folderImage(folder: File): File? {
        val imgs = folder.listFiles { f ->
            f.isFile && !f.name.startsWith(".") && f.extension.lowercase() in imageExt
        } ?: return null
        if (imgs.isEmpty()) return null
        for (p in preferredNames) {
            imgs.firstOrNull { it.nameWithoutExtension.lowercase() == p }?.let { return it }
        }
        imgs.firstOrNull {
            val n = it.name.lowercase()
            n.startsWith("albumart") && n.contains("large")
        }?.let { return it }
        return imgs.maxByOrNull { it.length() }
    }

    private fun embedded(f: File): ByteArray? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            r.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (e: Exception) { }
        }
    }

    private fun saveScaled(bytes: ByteArray, out: File): Boolean {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return false
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false
            val tmp = File(out.path + ".tmp")
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            return tmp.renameTo(out)
        } catch (e: Exception) {
            return false
        }
    }

    private fun cacheFile(key: String) = File(dir, hash(key) + ".jpg")

    private fun hash(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
