package com.carola.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carola.player.MainViewModel
import com.carola.player.data.NaturalOrder
import com.carola.player.data.StorageRoot
import com.carola.player.data.Track
import com.carola.player.data.formatTime
import java.io.File

@Composable
fun FolderScreen(vm: MainViewModel, onPlayed: () -> Unit) {
    val folder by vm.folder.collectAsStateWithLifecycle()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val musicFolders by vm.musicFolders.collectAsStateWithLifecycle()
    val pb by vm.playback.collectAsStateWithLifecycle()
    val scrollStates = remember { mutableMapOf<String, LazyListState>() }

    BackHandler(enabled = folder != null) { vm.folderUp() }

    val dir = folder
    Column(Modifier.fillMaxSize()) {
        if (dir == null) {
            ScreenHeader("Música", "Tus carpetas y memorias conectadas") {
                BigIconButton(Icons.Rounded.Refresh, { vm.refreshRoots() }, size = 56.dp)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            RootsList(
                roots = roots,
                musicFolders = musicFolders.sortedWith(compareBy(NaturalOrder) { File(it).name }),
                onOpenRoot = { vm.openFolder(File(it.path)) },
                onOpenFolder = { vm.openFolder(File(it)) },
                onRefresh = { vm.refreshRoots() },
            )
        } else {
            FolderContent(vm, dir, musicFolders, scrollStates, pb.path, onPlayed)
        }
    }
}

@Composable
private fun FolderContent(
    vm: MainViewModel,
    dir: File,
    musicFolders: Set<String>,
    scrollStates: MutableMap<String, LazyListState>,
    playingPath: String?,
    onPlayed: () -> Unit,
) {
    val listing by vm.listing.collectAsStateWithLifecycle()
    val lists by vm.listsState.collectAsStateWithLifecycle()
    val root = vm.rootFor(dir)
    val rel = root?.let { dir.absolutePath.removePrefix(it.path).trim('/') }.orEmpty()
    val crumb = listOfNotNull(root?.name, rel.ifEmpty { null }).joinToString(" / ").replace("/", " › ")
    val ready = listing.dir == dir
    val marked = dir.absolutePath in musicFolders
    val accent = MaterialTheme.colorScheme.primary

    ScreenHeader(if (rel.isEmpty()) (root?.name ?: dir.name) else dir.name, crumb, onBack = { vm.folderUp() }) {
        // Marcar / desmarcar como carpeta de música (no se ofrece en la raíz de una memoria)
        if (rel.isNotEmpty()) {
            BigIconButton(
                if (marked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                { vm.toggleMusicFolder(dir) },
                size = 56.dp,
                tint = if (marked) accent else Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(10.dp))
        }
        if (ready && (listing.files.isNotEmpty() || listing.musicDirs.isNotEmpty())) {
            FilledTonalButton(
                onClick = { vm.playFolder(dir, shuffle = false, recursive = listing.files.isEmpty()); onPlayed() },
                modifier = Modifier.height(54.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (listing.files.isEmpty()) "Todo" else "Reproducir", fontSize = 17.sp)
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(
                onClick = { vm.playFolder(dir, shuffle = true, recursive = true); onPlayed() },
                modifier = Modifier.height(54.dp),
            ) {
                Icon(Icons.Rounded.Shuffle, null, Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aleatorio", fontSize = 17.sp)
            }
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

    when {
        !ready -> Unit
        listing.dirs.isEmpty() && listing.files.isEmpty() ->
            EmptyState(Icons.Rounded.Folder, "Carpeta vacía", "Aquí no hay música ni subcarpetas.")
        else -> {
            val state = scrollStates.getOrPut(dir.absolutePath) { LazyListState() }
            val resume = lists.resume[vm.folderKey(dir)]
                ?.takeIf { it.path.startsWith(dir.absolutePath + "/") && playingPath != it.path }
            LazyColumn(state = state, contentPadding = PaddingValues(vertical = 6.dp)) {
                if (resume != null) {
                    item(key = "resume") {
                        val name = vm.library.find(resume.path)?.title ?: File(resume.path).nameWithoutExtension
                        ResumeBanner(name, resume.positionMs) { vm.resumeFolder(dir); onPlayed() }
                    }
                }
                items(listing.dirs, key = { "d" + it.absolutePath }) { d ->
                    FolderRow(
                        dir = d,
                        hasMusic = d.absolutePath in listing.musicDirs,
                        marked = d.absolutePath in musicFolders,
                    ) { vm.openFolder(d) }
                }
                itemsIndexed(listing.files, key = { _, f -> "f" + f.absolutePath }) { i, f ->
                    FileRow(
                        file = f,
                        number = i + 1,
                        track = vm.library.find(f.absolutePath),
                        playing = playingPath == f.absolutePath,
                        onLongClick = { vm.requestAddToList(listOf(f.absolutePath)) },
                    ) {
                        vm.playFiles(listing.files, i, context = vm.folderKey(dir))
                        onPlayed()
                    }
                }
            }
        }
    }
}

@Composable
private fun RootsList(
    roots: List<StorageRoot>,
    musicFolders: List<String>,
    onOpenRoot: (StorageRoot) -> Unit,
    onOpenFolder: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    if (roots.isEmpty() && musicFolders.isEmpty()) {
        EmptyState(Icons.Rounded.Usb, "No se detectó ninguna USB", "Conéctala y toca Actualizar.") {
            Button(onClick = onRefresh, modifier = Modifier.height(56.dp)) { Text("Actualizar", fontSize = 18.sp) }
        }
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        if (musicFolders.isNotEmpty()) {
            item(key = "h-music") { SectionTitle("Mis carpetas de música") }
            items(musicFolders, key = { "m$it" }) { path ->
                val f = File(path)
                val available = f.isDirectory
                BigRow(
                    icon = Icons.Rounded.LibraryMusic,
                    iconTint = if (available) accent else Color.White.copy(alpha = 0.3f),
                    title = f.name,
                    subtitle = if (available) path else "No disponible (¿USB desconectada?)",
                    enabled = available,
                ) { onOpenFolder(path) }
            }
            item(key = "h-storage") { SectionTitle("Almacenamiento") }
        }
        items(roots, key = { it.path }) { r ->
            BigRow(
                icon = if (r.isInternal) Icons.Rounded.PhoneAndroid else Icons.Rounded.Usb,
                iconTint = accent,
                title = r.name,
                subtitle = r.path,
            ) { onOpenRoot(r) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun BigRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(88.dp).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle, fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (enabled) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun FolderRow(dir: File, hasMusic: Boolean, marked: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().height(70.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                marked -> Icons.Rounded.LibraryMusic
                hasMusic -> Icons.Rounded.Folder
                else -> Icons.Rounded.FolderOpen
            },
            null,
            tint = if (marked || hasMusic) accent else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                dir.name, fontSize = 20.sp,
                color = if (marked || hasMusic) Color.White else Color.White.copy(alpha = 0.55f),
                fontWeight = if (marked) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (marked || hasMusic) {
                Text(
                    if (marked) "Carpeta de música" else "Tiene música",
                    fontSize = 13.sp, color = accent.copy(alpha = 0.85f),
                )
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.35f))
    }
}

@Composable
fun FileRow(
    file: File,
    number: Int,
    track: Track?,
    playing: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    TrackRowContent(
        number = number,
        title = track?.title ?: file.nameWithoutExtension,
        subtitle = track?.artist,
        durationMs = track?.durationMs ?: 0,
        playing = playing,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRowContent(
    number: Int,
    title: String,
    subtitle: String?,
    durationMs: Long,
    playing: Boolean,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val main = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    Row(
        Modifier.fillMaxWidth().height(70.dp)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterStart) {
            if (playing) {
                Icon(Icons.Rounded.Equalizer, null, tint = accent, modifier = Modifier.size(28.dp))
            } else {
                Text("$number", fontSize = 16.sp, color = Color.White.copy(alpha = 0.45f))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 19.sp,
                color = if (playing) accent else main,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle, fontSize = 14.sp, color = main.copy(alpha = main.alpha * 0.55f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (durationMs > 0) {
            Text(formatTime(durationMs), fontSize = 15.sp, color = Color.White.copy(alpha = 0.5f))
        }
        trailing?.invoke()
    }
}

/** Tarjeta "Continuar donde te quedaste". */
@Composable
fun ResumeBanner(title: String, positionMs: Long, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayCircle, null, tint = accent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Continuar donde te quedaste", fontSize = 14.sp, color = accent, fontWeight = FontWeight.SemiBold)
            Text(
                "$title · ${formatTime(positionMs)}", fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
