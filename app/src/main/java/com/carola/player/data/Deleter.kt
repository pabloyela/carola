package com.carola.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Borra canciones y carpetas.
 *
 * 1. Intenta borrar directo (memoria interna, y algunos radios también la USB).
 * 2. Si Android no lo permite (lo normal en memorias USB), usa el permiso que el usuario
 *    da UNA vez eligiendo la memoria en el selector del sistema.
 */
class Deleter(private val context: Context, private val prefs: Prefs) {

    sealed interface Result {
        val deleted: List<String>

        /** Todo borrado. [deleted] = rutas de canciones eliminadas. */
        class Ok(override val deleted: List<String>) : Result

        /** Falta el permiso de esa memoria: hay que pedirlo y volver a intentar. */
        class NeedsAccess(val root: StorageRoot, override val deleted: List<String>) : Result

        class Failed(val message: String, override val deleted: List<String>) : Result
    }

    suspend fun delete(targets: List<File>, roots: List<StorageRoot>): Result = withContext(Dispatchers.IO) {
        val deleted = mutableListOf<String>()
        for (t in targets) {
            if (!t.exists()) continue
            // Canciones que contiene (para limpiar biblioteca y listas después)
            val songs = if (t.isDirectory) {
                mutableListOf<File>().also { AudioFiles.walk(t, it) }.map { it.absolutePath }
            } else {
                listOf(t.absolutePath)
            }

            // 1) Borrado directo
            runCatching { if (t.isDirectory) t.deleteRecursively() else t.delete() }
            if (!t.exists()) {
                deleted += songs
                continue
            }

            // 2) Con el permiso de la memoria
            val root = roots
                .filter { t.absolutePath.startsWith(it.path + "/") }
                .maxByOrNull { it.path.length }
                ?: return@withContext Result.Failed("No se pudo borrar ${t.name}.", deleted)
            val tree = prefs.treeUris[root.path]?.let(Uri::parse)
            val treeDoc = tree?.let { DocumentFile.fromTreeUri(context, it) }
            if (treeDoc == null || !treeDoc.canWrite()) {
                return@withContext Result.NeedsAccess(root, deleted)
            }
            val rel = t.absolutePath.removePrefix(root.path).trim('/')
            var doc: DocumentFile? = treeDoc
            for (part in rel.split('/')) {
                doc = doc?.findFile(part)
                if (doc == null) break
            }
            val found = doc
            val ok = found != null && runCatching { found.delete() }.getOrDefault(false)
            if (ok) {
                deleted += songs
            } else {
                return@withContext Result.Failed("No se pudo borrar ${t.name}. ¿La memoria está protegida contra escritura?", deleted)
            }
        }
        Result.Ok(deleted)
    }

    /**
     * Guarda el permiso que dio el usuario para [root]. Revisa que haya elegido la raíz
     * de esa memoria (y no otra carpeta) comparando su contenido.
     */
    suspend fun grant(root: StorageRoot, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return@withContext false
        val chosen = doc.listFiles().mapNotNull { it.name }.toSet()
        val real = File(root.path).list()?.toSet() ?: emptySet()
        val common = chosen.intersect(real).size
        val total = (chosen + real).size
        val matches = total == 0 || common.toFloat() / total >= 0.6f
        if (!matches) return@withContext false
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.treeUris = prefs.treeUris + (root.path to uri.toString())
        true
    }
}
