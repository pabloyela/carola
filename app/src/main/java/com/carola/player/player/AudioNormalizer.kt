package com.carola.player.player

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Tracks

/**
 * Nivela el volumen entre canciones usando el procesador de dinámica de Android (9+).
 *
 *  - Si la canción trae ReplayGain: se aplica esa ganancia exacta + limitador (sin distorsión).
 *  - Si no: compresor suave que sube lo bajito y frena lo fuerte + limitador.
 *
 * Todo corre dentro del sistema de audio de Android, casi sin uso de procesador.
 * Si el radio no soporta el efecto, simplemente no hace nada.
 */
class AudioNormalizer {

    private var sessionId = 0
    private var enabled = true
    private var trackGainDb: Float? = null
    private var effect: Any? = null // DynamicsProcessing (se guarda como Any por compatibilidad con Android 8)

    fun attach(audioSessionId: Int) {
        if (audioSessionId == sessionId && effect != null) return
        release()
        sessionId = audioSessionId
        if (audioSessionId != 0 && Build.VERSION.SDK_INT >= 28) {
            effect = Impl.create(audioSessionId)
        }
        apply()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        apply()
    }

    fun setTrackGain(db: Float?) {
        trackGainDb = db
        apply()
    }

    private fun apply() {
        if (Build.VERSION.SDK_INT < 28) return
        val e = effect as? DynamicsProcessing ?: return
        Impl.apply(e, enabled, trackGainDb)
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= 28) {
            (effect as? DynamicsProcessing)?.let { runCatching { it.release() } }
        }
        effect = null
    }

    @RequiresApi(28)
    private object Impl {
        /** Ganancia extra sobre ReplayGain para que no quede más bajito que lo nivelado. */
        private const val PREAMP_DB = 6f

        fun create(session: Int): DynamicsProcessing? = try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,          // canales
                false, 0,   // ecualizador previo
                true, 1,    // compresor de 1 banda
                false, 0,   // ecualizador posterior
                true,       // limitador
            ).build()
            DynamicsProcessing(0, session, config)
        } catch (t: Throwable) {
            null
        }

        fun apply(d: DynamicsProcessing, enabled: Boolean, rg: Float?) {
            try {
                if (!enabled) {
                    d.setEnabled(false)
                    return
                }
                val band = if (rg == null) {
                    // Compresor: umbral -26 dB, 3:1, +9 dB de compensación
                    DynamicsProcessing.MbcBand(true, 20000f, 10f, 300f, 3f, -26f, 8f, -90f, 1f, 0f, 9f)
                } else {
                    DynamicsProcessing.MbcBand(false, 20000f, 10f, 300f, 1f, 0f, 0f, -90f, 1f, 0f, 0f)
                }
                d.setMbcBandAllChannelsTo(0, band)
                // Limitador: evita que algo pase de -1.5 dB (sin picos ni distorsión)
                d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1.5f, 0f))
                d.setInputGainAllChannelsTo(rg?.let { (it + PREAMP_DB).coerceIn(-20f, 12f) } ?: 0f)
                d.setEnabled(true)
            } catch (t: Throwable) {
                // Algunos radios tienen efectos de audio incompletos: se ignora
            }
        }
    }
}

/** Busca la etiqueta REPLAYGAIN_TRACK_GAIN en los metadatos de la canción actual. */
object ReplayGain {
    private val pattern = Regex(
        "replaygain_track_gain\\D*?([-+]?\\d+(?:[.,]\\d+)?)\\s*db",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun find(tracks: Tracks): Float? {
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                val md = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until md.length()) {
                    val text = md.get(j).toString()
                    val m = pattern.find(text) ?: continue
                    m.groupValues[1].replace(',', '.').replace("+", "").toFloatOrNull()?.let { return it }
                }
            }
        }
        return null
    }
}
