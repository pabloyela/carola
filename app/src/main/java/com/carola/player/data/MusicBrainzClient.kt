package com.carola.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cliente mínimo de MusicBrainz (búsqueda) + Cover Art Archive (imágenes).
 * Gratis y sin API key. MusicBrainz pide máximo 1 consulta por segundo.
 */
class MusicBrainzClient {

    sealed interface Result {
        class Found(val bytes: ByteArray) : Result
        data object NotFound : Result
        data object Error : Result
    }

    companion object {
        // MusicBrainz pide identificar la app. Puedes agregar tu correo aquí, ej:
        // "Carola/1.0 ( tu-correo@ejemplo.com )"
        const val USER_AGENT = "Carola/1.0 ( reproductor personal para carro )"
        private const val MB = "https://musicbrainz.org/ws/2"
        private const val CAA = "https://coverartarchive.org"
    }

    private val mutex = Mutex()
    private var lastCall = 0L
    private val special = Regex("[\\\\+\\-!(){}\\[\\]^\"~*?:/&|]")
    private val spaces = Regex("\\s+")
    private val discSuffix = Regex("\\s*[\\(\\[]\\s*(disc|disco|cd)\\s*\\d+\\s*[\\)\\]]", RegexOption.IGNORE_CASE)

    suspend fun findCover(artist: String, album: String?, title: String?): Result =
        withContext(Dispatchers.IO) {
            try {
                val ids = mutableListOf<String>()

                if (album != null) {
                    val q = "releasegroup:\"${clean(album.replace(discSuffix, ""))}\" AND artist:\"${clean(artist)}\""
                    val json = getJson("$MB/release-group/?fmt=json&limit=5&query=${enc(q)}")
                    val arr = json.optJSONArray("release-groups")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            if (o.optInt("score") >= 85) ids += "release-group/" + o.getString("id")
                        }
                    }
                }

                // Sin álbum (o sin resultados): buscar por canción
                if (ids.isEmpty() && !title.isNullOrBlank()) {
                    val q = "recording:\"${clean(title)}\" AND artist:\"${clean(artist)}\""
                    val json = getJson("$MB/recording/?fmt=json&limit=5&query=${enc(q)}")
                    val arr = json.optJSONArray("recordings")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            if (o.optInt("score") < 85) continue
                            val releases = o.optJSONArray("releases") ?: continue
                            for (j in 0 until minOf(releases.length(), 3)) {
                                ids += "release/" + releases.getJSONObject(j).getString("id")
                            }
                            if (ids.size >= 6) break
                        }
                    }
                }

                if (ids.isEmpty()) return@withContext Result.NotFound

                for (id in ids.distinct().take(5)) {
                    val bytes = getBytes("$CAA/$id/front-500")
                    if (bytes != null) return@withContext Result.Found(bytes)
                }
                Result.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error
            }
        }

    private suspend fun throttle() {
        mutex.withLock {
            val wait = lastCall + 1100 - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastCall = System.currentTimeMillis()
        }
    }

    private suspend fun getJson(url: String): JSONObject {
        throttle()
        val c = open(url)
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code != 200) throw IOException("MusicBrainz HTTP $code")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    private fun getBytes(url: String): ByteArray? {
        val c = open(url)
        try {
            return when (val code = c.responseCode) {
                200 -> c.inputStream.use { it.readBytes() }
                404 -> null
                else -> throw IOException("Cover Art Archive HTTP $code")
            }
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun clean(s: String) = s.replace(special, " ").replace(spaces, " ").trim()
}
