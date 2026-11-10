package com.carola.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carola.player.MainViewModel
import com.carola.player.data.Album
import com.carola.player.data.SearchResults
import java.io.File

@Composable
fun SearchScreen(vm: MainViewModel, onPlayed: () -> Unit, onOpenFolder: (File) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    var selectedAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedArtist by rememberSaveable { mutableStateOf<String?>(null) }

    val album = selectedAlbum?.let { k -> albums.find { it.key == k } }
    val artist = selectedArtist?.let { n -> artists.find { it.name == n } }

    when {
        album != null -> {
            BackHandler { selectedAlbum = null }
            AlbumDetail(vm, album, onBack = { selectedAlbum = null }, onPlayed = onPlayed)
        }
        artist != null -> {
            BackHandler { selectedArtist = null }
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(artist.name, "${artist.albums.size} álbumes", onBack = { selectedArtist = null }) {
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
        else -> Column(Modifier.fillMaxSize()) {
            SearchBox(query, vm::setQuery)
            when {
                query.isBlank() -> EmptyState(
                    Icons.Rounded.Search, "Buscar música",
                    "Escribe una canción, artista, álbum o carpeta. No importan acentos ni mayúsculas.",
                )
                results.query == query && results.isEmpty -> EmptyState(
                    Icons.Rounded.SearchOff, "Sin resultados",
                    "No encontré nada con \"$query\". Recuerda que solo se busca en la música escaneada.",
                )
                else -> Results(
                    vm, results,
                    onAlbum = { selectedAlbum = it.key },
                    onArtist = { selectedArtist = it },
                    onFolder = onOpenFolder,
                    onPlayed = onPlayed,
                )
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = MaterialTheme.colorScheme.primary
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("¿Qué quieres escuchar?", fontSize = 20.sp, color = Color.White.copy(alpha = 0.4f)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(28.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Close, "Borrar", Modifier.size(28.dp))
                }
            }
        },
        textStyle = TextStyle(fontSize = 22.sp, color = Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            cursorColor = accent,
            focusedLeadingIconColor = accent,
            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.6f),
            focusedTrailingIconColor = Color.White,
            unfocusedTrailingIconColor = Color.White.copy(alpha = 0.7f),
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

@Composable
private fun Results(
    vm: MainViewModel,
    r: SearchResults,
    onAlbum: (Album) -> Unit,
    onArtist: (String) -> Unit,
    onFolder: (File) -> Unit,
    onPlayed: () -> Unit,
) {
    val pb by vm.playback.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        if (r.artists.isNotEmpty()) {
            item(key = "h-ar") { Section("Artistas") }
            item(key = "artists") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(r.artists, key = { "ar" + it.name }) { a ->
                        Column(
                            Modifier.width(120.dp).clip(RoundedCornerShape(12.dp)).clickable { onArtist(a.name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CoverArt(a.albums.first().coverRequest(), Modifier.size(104.dp).clip(CircleShape))
                            Spacer(Modifier.height(6.dp))
                            Text(a.name, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (r.albums.isNotEmpty()) {
            item(key = "h-al") { Section("Álbumes") }
            item(key = "albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(r.albums, key = { "al" + it.key }) { a ->
                        Column(Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).clickable { onAlbum(a) }) {
                            CoverArt(
                                a.coverRequest(),
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(a.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.artist, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (r.tracks.isNotEmpty()) {
            item(key = "h-tr") { Section("Canciones") }
            items(r.tracks, key = { "t" + it.first.path }) { (t, a) ->
                Row(
                    Modifier.fillMaxWidth().height(70.dp).clickable {
                        // Reproduce el álbum completo empezando por esta canción
                        vm.playTracks(a.tracks, a.tracks.indexOfFirst { it.path == t.path }.coerceAtLeast(0))
                        onPlayed()
                    }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(a.coverRequest(), Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        val playing = pb.path == t.path
                        Text(
                            t.title, fontSize = 19.sp,
                            color = if (playing) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(t.artist ?: a.artist, a.title).joinToString(" · "),
                            fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (r.folders.isNotEmpty()) {
            item(key = "h-fo") { Section("Carpetas") }
            items(r.folders, key = { "f" + it.absolutePath }) { f ->
                Row(
                    Modifier.fillMaxWidth().height(66.dp).clickable { onFolder(f) }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            f.parent.orEmpty(), fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}
