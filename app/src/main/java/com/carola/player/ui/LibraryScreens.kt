package com.carola.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carola.player.MainViewModel
import com.carola.player.data.Album
import com.carola.player.data.ScanState
import com.carola.player.data.formatTime

@Composable
fun AlbumsScreen(vm: MainViewModel, onPlayed: () -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val album = selected?.let { key -> albums.find { it.key == key } }

    if (album != null) {
        BackHandler { selected = null }
        AlbumDetail(vm, album, onBack = { selected = null }, onPlayed = onPlayed)
    } else {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Álbumes", "${albums.size} álbumes") {
                BigIconButton(Icons.Rounded.Refresh, { vm.rescan() }, size = 56.dp)
            }
            ScanProgress(scan)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            if (albums.isEmpty() && !scan.running) {
                EmptyState(
                    Icons.Rounded.Album, "Aún no hay álbumes",
                    "Conecta tu USB o marca tus carpetas de música con el marcador 🔖 en Carpetas.",
                ) {
                    Button(onClick = { vm.rescan() }, modifier = Modifier.height(56.dp)) {
                        Text("Escanear", fontSize = 18.sp)
                    }
                }
            } else {
                AlbumGrid(albums) { selected = it.key }
            }
        }
    }
}

@Composable
fun ArtistsScreen(vm: MainViewModel, onPlayed: () -> Unit) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    var selectedArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbum by rememberSaveable { mutableStateOf<String?>(null) }

    val artist = selectedArtist?.let { name -> artists.find { it.name == name } }
    val album = selectedAlbum?.let { key -> albums.find { it.key == key } }

    when {
        album != null -> {
            BackHandler { selectedAlbum = null }
            AlbumDetail(vm, album, onBack = { selectedAlbum = null }, onPlayed = onPlayed)
        }
        artist != null -> {
            BackHandler { selectedArtist = null }
            Column(Modifier.fillMaxSize()) {
                val count = artist.albums.sumOf { it.tracks.size }
                ScreenHeader(artist.name, "${artist.albums.size} álbumes · $count canciones", onBack = { selectedArtist = null }) {
                    FilledTonalButton(
                        onClick = { vm.playTracks(artist.albums.flatMap { it.tracks }, shuffle = true); onPlayed() },
                        modifier = Modifier.height(54.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aleatorio", fontSize = 17.sp)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                AlbumGrid(artist.albums) { selectedAlbum = it.key }
            }
        }
        else -> {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Artistas", "${artists.size} artistas")
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                if (artists.isEmpty()) {
                    EmptyState(Icons.Rounded.Person, "Sin artistas", "Escanea tu USB desde la pestaña Álbumes.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(artists, key = { it.name }) { a ->
                            Row(
                                Modifier.fillMaxWidth().height(80.dp).clickable { selectedArtist = a.name }
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CoverArt(a.albums.first().coverRequest(), Modifier.size(56.dp).clip(CircleShape))
                                Spacer(Modifier.width(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.name, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${a.albums.size} álbum" + if (a.albums.size == 1) "" else "es",
                                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f),
                                    )
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.35f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanProgress(scan: ScanState) {
    if (!scan.running) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        Text(
            if (scan.total == 0) "Buscando música…" else "Leyendo canciones… ${scan.done} / ${scan.total}",
            fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(6.dp))
        if (scan.total == 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { scan.done.toFloat() / scan.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AlbumGrid(albums: List<Album>, onClick: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.key }) { a ->
            Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick(a) }) {
                CoverArt(
                    a.coverRequest(),
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    a.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    a.artist, fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AlbumDetail(vm: MainViewModel, album: Album, onBack: () -> Unit, onPlayed: () -> Unit) {
    val pb by vm.playback.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            BigIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, size = 56.dp)
            Spacer(Modifier.height(10.dp))
            CoverArt(album.coverRequest(), Modifier.size(260.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.height(16.dp))
            Text(album.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(album.artist, fontSize = 18.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val total = album.tracks.sumOf { it.durationMs }
            Text(
                "${album.tracks.size} canciones" + if (total > 0) " · ${formatTime(total)}" else "",
                fontSize = 15.sp, color = Color.White.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            Row {
                Button(
                    onClick = { vm.playTracks(album.tracks); onPlayed() },
                    modifier = Modifier.height(54.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reproducir", fontSize = 16.sp)
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { vm.playTracks(album.tracks, shuffle = true); onPlayed() },
                    modifier = Modifier.height(54.dp),
                ) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(album.tracks, key = { _, t -> t.path }) { i, t ->
                TrackRowContent(
                    number = if (t.trackNo > 0) t.trackNo else i + 1,
                    title = t.title,
                    subtitle = t.artist.takeIf { it != null && it != album.artist },
                    durationMs = t.durationMs,
                    playing = pb.path == t.path,
                ) {
                    vm.playTracks(album.tracks, i)
                    onPlayed()
                }
            }
        }
    }
}
