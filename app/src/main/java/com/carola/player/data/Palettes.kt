package com.carola.player.data

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Palettes {
    /** Color de acento sacado de la portada (null = usar el naranja por defecto). */
    suspend fun accentFrom(file: File): Int? = withContext(Dispatchers.Default) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 120) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            bmp.recycle()
            val sw = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch
                ?: p.mutedSwatch ?: p.dominantSwatch ?: return@withContext null
            val hsv = FloatArray(3)
            Color.colorToHSV(sw.rgb, hsv)
            if (hsv[1] < 0.18f) return@withContext null
            if (hsv[2] < 0.75f) hsv[2] = 0.88f
            Color.HSVToColor(hsv)
        } catch (e: Exception) {
            null
        }
    }
}
