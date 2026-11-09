package com.carola.player.data

/**
 * Limpia nombres típicos de música bajada de YouTube:
 * "Joe Bonamassa Official", "Artista - Topic", "ArtistaVEVO",
 * "(Official Video)", "[Official Audio]", "(Lyrics)", "(HD)"...
 */
object NameCleaner {
    private val bracketJunk = Regex(
        "\\s*[\\(\\[][^\\)\\]]*\\b(official|oficial|video|vídeo|videoclip|audio|lyrics?|letra|hd|hq|4k|" +
            "visuali[sz]er|music video)\\b[^\\)\\]]*[\\)\\]]",
        RegexOption.IGNORE_CASE,
    )
    private val artistSuffix = Regex(
        "(\\s*-\\s*topic|\\s+official|\\s+oficial|\\s*vevo)$",
        RegexOption.IGNORE_CASE,
    )
    private val spaces = Regex("\\s{2,}")

    fun title(s: String): String =
        s.replace(bracketJunk, "").replace(spaces, " ").trim().ifBlank { s.trim() }

    fun artist(s: String): String {
        var r = s.replace(bracketJunk, "").trim()
        repeat(2) { r = r.replace(artistSuffix, "").trim() }
        return r.replace(spaces, " ").ifBlank { s.trim() }
    }
}
