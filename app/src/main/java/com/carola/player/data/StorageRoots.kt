package com.carola.player.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/**
 * Detecta memorias USB / SD montadas. Los radios Android chinos montan las USB
 * en rutas muy distintas, así que se combinan varias fuentes.
 */
object StorageRoots {

    private val knownPaths = listOf(
        "/storage/usbotg", "/storage/usbdisk", "/storage/udisk", "/storage/usb0",
        "/storage/usb1", "/storage/usb2", "/mnt/usb_storage", "/mnt/usbhost",
        "/mnt/usbhost1", "/mnt/udisk", "/mnt/usb", "/mnt/usbdisk", "/mnt/external_sd",
    )

    private val removableFs = setOf(
        "vfat", "exfat", "ntfs", "fuseblk", "texfat", "tntfs", "msdos",
        "sdfat", "ufsd", "fuse", "sdcardfs", "esdfs",
    )

    fun find(context: Context): List<StorageRoot> {
        val result = LinkedHashMap<String, StorageRoot>()

        fun add(dir: File, name: String, internal: Boolean = false) {
            if (!dir.isDirectory || !dir.canRead()) return
            if (dir.listFiles() == null) return
            val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (!internal && canonical.startsWith("/storage/emulated")) return
            if (result.containsKey(canonical)) return
            result[canonical] = StorageRoot(name, canonical, internal)
        }

        // 1) Volúmenes que reporta el sistema
        val sm = context.getSystemService(StorageManager::class.java)
        sm?.storageVolumes?.forEach { vol ->
            val path = volumePath(vol) ?: return@forEach
            val state = vol.state
            if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) return@forEach
            if (vol.isPrimary) {
                add(File(path), "Memoria interna", internal = true)
            } else {
                add(File(path), vol.getDescription(context) ?: "USB")
            }
        }

        // 2) Todo lo que cuelgue de /storage
        File("/storage").listFiles()?.forEach { f ->
            if (f.name != "emulated" && f.name != "self") add(f, "USB ${f.name}")
        }

        // 3) Puntos de montaje reales
        runCatching { File("/proc/mounts").readLines() }.getOrDefault(emptyList()).forEach { line ->
            val parts = line.split(" ")
            if (parts.size < 3) return@forEach
            val mp = parts[1].replace("\\040", " ")
            val fs = parts[2]
            val ok = (mp.startsWith("/mnt/") || mp.startsWith("/storage/")) &&
                fs in removableFs &&
                !mp.contains("/emulated") &&
                !mp.startsWith("/mnt/runtime") &&
                !mp.startsWith("/mnt/media_rw") &&
                !mp.startsWith("/mnt/user") &&
                !mp.startsWith("/mnt/pass_through") &&
                !mp.startsWith("/mnt/expand")
            if (ok) add(File(mp), "USB ${File(mp).name}")
        }

        // 4) Rutas típicas de radios de carro
        knownPaths.forEach { p ->
            val f = File(p)
            add(f, "USB ${f.name}")
        }

        return result.values.sortedBy { it.isInternal }
    }

    private fun volumePath(vol: StorageVolume): String? {
        if (Build.VERSION.SDK_INT >= 30) {
            return vol.directory?.absolutePath
        }
        return runCatching {
            StorageVolume::class.java.getMethod("getPath").invoke(vol) as String
        }.getOrNull()
    }
}

object AudioFiles {
    val EXTENSIONS = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "mka", "3gp", "amr", "alac",
    )
    private val skipDirs = setOf(
        "lost.dir", "system volume information", "android", "\$recycle.bin",
        ".trashes", "found.000", "recycler",
    )

    fun isAudio(f: File): Boolean =
        !f.name.startsWith(".") && f.extension.lowercase() in EXTENSIONS && f.isFile

    fun isBrowsableDir(f: File): Boolean =
        !f.name.startsWith(".") && f.name.lowercase() !in skipDirs && f.isDirectory

    fun list(dir: File): Listing {
        val all = dir.listFiles() ?: emptyArray()
        val dirs = all.filter(::isBrowsableDir).sortedWith(compareBy(NaturalOrder) { it.name })
        val files = all.filter(::isAudio).sortedWith(compareBy(NaturalOrder) { it.name })
        return Listing(dir, dirs, files)
    }

    /** Recorre la carpeta: primero sus canciones, luego subcarpetas en orden. */
    fun walk(dir: File, out: MutableList<File>, depth: Int = 0) {
        if (depth > 12) return
        val l = list(dir)
        out += l.files
        l.dirs.forEach { walk(it, out, depth + 1) }
    }
}
