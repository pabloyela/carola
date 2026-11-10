package com.carola.player.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carola.player.MainViewModel
import com.carola.player.data.ListEntry
import com.carola.player.data.ListKind
import com.carola.player.data.PlaylistView

private fun ListKind.icon(): ImageVector = when (this) {
    ListKind.FAVORITES -> Icons.Rounded.Favorite
    ListKind.TOP -> Icons.AutoMirrored.Rounded.TrendingUp
    ListKind.USER -> Icons.AutoMirrored.Rounded.QueueMusic
    ListKind.M3U -> Icons.Rounded.Usb
}

private fun songCount(n: Int) = if (n == 1) "1 canción" else "$n canciones"

@Composable
fun ListsScreen(vm: MainViewModel, onPlayed: () -> Unit) {
    val lists by vm.allLists.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val sel = selected?.let { id -> lists.find { it.id == id } }

    if (sel != null) {
        BackHandler { selected = null }
        ListDetail(vm, sel, onBack = { selected = null }, onPlayed = onPlayed)
    } else {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Listas", "Favoritas, más escuchadas y tus listas") {
                FilledTonalButton(onClick = { creating = true }, modifier = Modifier.height(54.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nueva lista", fontSize = 17.sp)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            val auto = lists.filter { it.kind == ListKind.FAVORITES || it.kind == ListKind.TOP }
            val mine = lists.filter { it.kind == ListKind.USER }
            val usb = lists.filter { it.kind == ListKind.M3U }
            LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                items(auto, key = { it.id }) { ListRow(it) { selected = it.id } }
                item(key = "h-mine") { ListSection("Mis listas") }
                if (mine.isEmpty()) {
                    item(key = "mine-empty") {
                        Text(
                            "Aún no tienes listas. Toca \"Nueva lista\", o mantén presionada una canción para agregarla.",
                            fontSize = 15.sp, color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                items(mine, key = { it.id }) { ListRow(it) { selected = it.id } }
                if (usb.isNotEmpty()) {
                    item(key = "h-usb") { ListSection("En la USB (.m3u)") }
                    items(usb, key = { it.id }) { ListRow(it) { selected = it.id } }
                }
            }
        }
    }

    if (creating) {
        NameDialog("Nueva lista", "", onDismiss = { creating = false }) { name ->
            vm.listsRepo.create(name)
            creating = false
        }
    }
}

@Composable
private fun ListSection(text: String) {
    Text(
        text.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun ListRow(list: PlaylistView, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().height(84.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(list.kind.icon(), null, tint = accent, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(list.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(songCount(list.paths.size), fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.35f))
    }
}

@Composable
private fun ListDetail(vm: MainViewModel, list: PlaylistView, onBack: () -> Unit, onPlayed: () -> Unit) {
    val roots by vm.roots.collectAsStateWithLifecycle()
    val state by vm.listsState.collectAsStateWithLifecycle()
    val pb by vm.playback.collectAsStateWithLifecycle()
    // Se vuelve a revisar qué canciones existen al cambiar la lista o al conectar/quitar una USB
    val entries by produceState<List<ListEntry>?>(null, list.paths, roots) { value = vm.entries(list.paths) }
    var renaming by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    val loaded = entries
    val missing = loaded?.count { !it.available } ?: 0
    val subtitle = buildString {
        append(songCount(list.paths.size))
        if (missing > 0) append(" · $missing no disponibles")
        if (list.kind == ListKind.M3U) append(" · desde la USB")
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(list.name, subtitle, onBack = onBack) {
            when (list.kind) {
                ListKind.USER -> {
                    BigIconButton(Icons.Rounded.Edit, { renaming = true }, size = 52.dp)
                    BigIconButton(Icons.Rounded.Delete, { confirm = true }, size = 52.dp)
                    Spacer(Modifier.width(8.dp))
                }
                ListKind.TOP -> if (list.paths.isNotEmpty()) {
                    BigIconButton(Icons.Rounded.RestartAlt, { confirm = true }, size = 52.dp)
                    Spacer(Modifier.width(8.dp))
                }
                else -> Unit
            }
            if (loaded != null && missing < loaded.size) {
                FilledTonalButton(onClick = { vm.playList(loaded); onPlayed() }, modifier = Modifier.height(54.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { vm.playList(loaded, shuffle = true); onPlayed() }, modifier = Modifier.height(54.dp)) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(24.dp))
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        when {
            loaded == null -> Unit
            loaded.isEmpty() -> EmptyState(
                list.kind.icon(),
                "Lista vacía",
                when (list.kind) {
                    ListKind.FAVORITES -> "Toca el corazón en Sonando para guardar canciones aquí."
                    ListKind.TOP -> "Se llena sola con las canciones que más escuchas."
                    else -> "Mantén presionada una canción, o usa el botón de agregar en Sonando."
                },
            )
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                itemsIndexed(loaded, key = { i, e -> "$i|${e.path}" }) { i, e ->
                    val accent = MaterialTheme.colorScheme.primary
                    val trailing: (@Composable () -> Unit)? = when (list.kind) {
                        ListKind.USER -> @Composable {
                            BigIconButton(
                                Icons.Rounded.Close, { vm.listsRepo.removeAt(list.id, i) },
                                size = 48.dp, tint = Color.White.copy(alpha = 0.5f),
                            )
                        }
                        ListKind.FAVORITES -> @Composable {
                            BigIconButton(
                                Icons.Rounded.Favorite, { vm.listsRepo.removeFavorite(e.path) },
                                size = 48.dp, tint = accent,
                            )
                        }
                        ListKind.TOP -> @Composable {
                            Text(
                                "${state.counts[e.path] ?: 0}×", fontSize = 15.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        ListKind.M3U -> null
                    }
                    TrackRowContent(
                        number = i + 1,
                        title = e.title,
                        subtitle = if (e.available) e.subtitle else "No disponible (¿USB desconectada?)",
                        durationMs = if (e.available) e.durationMs else 0,
                        playing = pb.path == e.path,
                        enabled = e.available,
                        onClick = { vm.playList(loaded, e.path); onPlayed() },
                        onLongClick = if (e.available) ({ vm.requestAddToList(listOf(e.path)) }) else null,
                        trailing = trailing,
                    )
                }
            }
        }
    }

    if (renaming) {
        NameDialog("Cambiar nombre", list.name, onDismiss = { renaming = false }) { name ->
            vm.listsRepo.rename(list.id, name)
            renaming = false
        }
    }
    if (confirm) {
        val reset = list.kind == ListKind.TOP
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (reset) "¿Reiniciar el conteo?" else "¿Borrar \"${list.name}\"?") },
            text = {
                Text(
                    if (reset) "Se borra cuántas veces escuchaste cada canción. Tu música no se toca."
                    else "Solo se borra la lista. Tus canciones siguen en la USB.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (reset) vm.listsRepo.resetCounts() else vm.listsRepo.delete(list.id)
                    confirm = false
                }) { Text(if (reset) "Reiniciar" else "Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

/** Diálogo global "Agregar a lista": se abre con vm.requestAddToList(...). */
@Composable
fun AddToListDialog(vm: MainViewModel) {
    val request by vm.addRequest.collectAsStateWithLifecycle()
    val lists by vm.allLists.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val paths = request ?: return

    fun done(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        naming = false
        vm.dismissAddRequest()
    }

    if (naming) {
        NameDialog("Nueva lista", "", onDismiss = { naming = false }) { name ->
            vm.listsRepo.create(name, paths)
            done("Lista \"$name\" creada")
        }
        return
    }

    val accent = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = { vm.dismissAddRequest() },
        title = { Text(if (paths.size == 1) "Agregar a…" else "Agregar ${paths.size} canciones a…") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                DialogRow(Icons.Rounded.Add, "Nueva lista…", accent) { naming = true }
                DialogRow(Icons.Rounded.Favorite, "Favoritas", accent) {
                    vm.listsRepo.addFavorites(paths)
                    done("Agregada a Favoritas")
                }
                lists.filter { it.kind == ListKind.USER }.forEach { l ->
                    DialogRow(Icons.AutoMirrored.Rounded.QueueMusic, l.name, Color.White.copy(alpha = 0.7f)) {
                        vm.listsRepo.addTo(l.id, paths)
                        done("Agregada a \"${l.name}\"")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.dismissAddRequest() }) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogRow(icon: ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val accent = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Nombre de la lista") },
                textStyle = TextStyle(fontSize = 20.sp, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, cursorColor = accent),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
