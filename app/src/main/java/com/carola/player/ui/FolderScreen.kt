package com.carola.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carola.player.MainViewModel
import com.carola.player.data.StorageRoot
import com.carola.player.data.Track
import com.carola.player.data.formatTime
import java.io.File

@Composable
fun FolderScreen(vm: MainViewModel, onPlayed: () -> Unit) {
    val folder by vm.folder.collectAsStateWithLifecycle()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val pb by vm.playback.collectAsStateWithLifecycle()
    val scrollStates = remember { mutableMapOf<String, LazyListState>() }

    BackHandler(enabled = folder != null) { vm.folderUp() }

    val dir = folder
    Column(Modifier.fillMaxSize()) {
        if (dir == null) {
            ScreenHeader("Música", "Memorias conectadas") {
                BigIconButton(Icons.Rounded.Refresh, { vm.refreshRoots() }, size = 56.dp)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            RootsList(roots, onOpen = { vm.openFolder(File(it.path)) }, onRefresh = { vm.refreshRoots() })
        } else {
            FolderContent(vm, dir, scrollStates, pb.path, onPlayed)
        }
    }
}

@Composable
private fun FolderContent(
    vm: MainViewModel,
    dir: File,
    scrollStates: MutableMap<String, LazyListState>,
    playingPath: String?,
    onPlayed: () -> Unit,
) {
    val listing by vm.listing.collectAsStateWithLifecycle()
    val root = vm.rootFor(dir)
    val rel = root?.let { dir.absolutePath.removePrefix(it.path).trim('/') }.orEmpty()
    val crumb = listOfNotNull(root?.name, rel.ifEmpty { null }).joinToString(" / ").replace("/", " › ")
    val ready = listing.dir == dir

    ScreenHeader(if (rel.isEmpty()) (root?.name ?: dir.name) else dir.name, crumb, onBack = { vm.folderUp() }) {
        if (ready && (listing.files.isNotEmpty() || listing.dirs.isNotEmpty())) {
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
            LazyColumn(state = state, contentPadding = PaddingValues(vertical = 6.dp)) {
                items(listing.dirs, key = { "d" + it.absolutePath }) { d ->
                    FolderRow(d) { vm.openFolder(d) }
                }
                itemsIndexed(listing.files, key = { _, f -> "f" + f.absolutePath }) { i, f ->
                    FileRow(
                        file = f,
                        number = i + 1,
                        track = vm.library.find(f.absolutePath),
                        playing = playingPath == f.absolutePath,
                    ) {
                        vm.playFiles(listing.files, i)
                        onPlayed()
                    }
                }
            }
        }
    }
}

@Composable
private fun RootsList(roots: List<StorageRoot>, onOpen: (StorageRoot) -> Unit, onRefresh: () -> Unit) {
    if (roots.isEmpty()) {
        EmptyState(Icons.Rounded.Usb, "No se detectó ninguna USB", "Conéctala y toca Actualizar.") {
            Button(onClick = onRefresh, modifier = Modifier.height(56.dp)) { Text("Actualizar", fontSize = 18.sp) }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(roots, key = { it.path }) { r ->
            Row(
                Modifier.fillMaxWidth().height(92.dp).clickable { onOpen(r) }.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (r.isInternal) Icons.Rounded.PhoneAndroid else Icons.Rounded.Usb, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(r.path, fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun FolderRow(dir: File, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(70.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Folder, null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            dir.name, fontSize = 20.sp, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.35f))
    }
}

@Composable
fun FileRow(file: File, number: Int, track: Track?, playing: Boolean, onClick: () -> Unit) {
    TrackRowContent(
        number = number,
        title = track?.title ?: file.nameWithoutExtension,
        subtitle = track?.artist,
        durationMs = track?.durationMs ?: 0,
        playing = playing,
        onClick = onClick,
    )
}

@Composable
fun TrackRowContent(
    number: Int,
    title: String,
    subtitle: String?,
    durationMs: Long,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().height(70.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
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
                color = if (playing) accent else Color.White,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle, fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (durationMs > 0) {
            Text(formatTime(durationMs), fontSize = 15.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}
