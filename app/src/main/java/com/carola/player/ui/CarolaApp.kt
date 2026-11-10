package com.carola.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carola.player.MainViewModel

enum class Tab(val label: String, val icon: ImageVector) {
    NOW("Sonando", Icons.Rounded.GraphicEq),
    SEARCH("Buscar", Icons.Rounded.Search),
    LISTS("Listas", Icons.AutoMirrored.Rounded.PlaylistPlay),
    FOLDERS("Carpetas", Icons.Rounded.Folder),
    ALBUMS("Álbumes", Icons.Rounded.Album),
    ARTISTS("Artistas", Icons.Rounded.Person),
    SETTINGS("Ajustes", Icons.Rounded.Settings),
}

@Composable
fun CarolaApp(vm: MainViewModel) {
    val pb by vm.playback.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.FOLDERS) }
    var autoOpened by rememberSaveable { mutableStateOf(false) }

    // Si al abrir se reanudó la última canción, mostrar directamente el reproductor
    LaunchedEffect(pb.hasMedia) {
        if (pb.hasMedia && !autoOpened) {
            autoOpened = true
            tab = Tab.NOW
        }
    }

    CompositionLocalProvider(LocalCovers provides vm.covers) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavigationRail(containerColor = RailColor, modifier = Modifier.fillMaxHeight()) {
              Column(
                  Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Spacer(Modifier.height(4.dp))
                Tab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label, modifier = Modifier.size(28.dp)) },
                        label = { Text(t.label, fontSize = 13.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.White.copy(alpha = 0.55f),
                            unselectedTextColor = Color.White.copy(alpha = 0.55f),
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(6.dp))
                CoverToggle(settings.onlineCovers) { vm.setOnlineCovers(!settings.onlineCovers) }
                Spacer(Modifier.height(8.dp))
              }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f)) {
                    val goNow = { tab = Tab.NOW }
                    when (tab) {
                        Tab.NOW -> NowPlayingScreen(vm, onBrowse = { tab = Tab.FOLDERS })
                        Tab.SEARCH -> SearchScreen(
                            vm, onPlayed = goNow,
                            onOpenFolder = { f -> vm.openFolder(f); tab = Tab.FOLDERS },
                        )
                        Tab.LISTS -> ListsScreen(vm, onPlayed = goNow)
                        Tab.FOLDERS -> FolderScreen(vm, onPlayed = goNow)
                        Tab.ALBUMS -> AlbumsScreen(vm, onPlayed = goNow)
                        Tab.ARTISTS -> ArtistsScreen(vm, onPlayed = goNow)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                }
                if (tab != Tab.NOW && pb.hasMedia) {
                    MiniPlayer(vm, pb, onOpen = { tab = Tab.NOW })
                }
            }
        }
        AddToListDialog(vm)
    }
}

/** Botón rápido para activar/desactivar la descarga de portadas por internet. */
@Composable
private fun CoverToggle(enabled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.width(80.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (enabled) Icons.Rounded.CloudDownload else Icons.Rounded.CloudOff,
            contentDescription = "Portadas por internet",
            tint = if (enabled) accent else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(28.dp),
        )
        Text(
            if (enabled) "Portadas\nen línea" else "Portadas\napagadas",
            fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center,
            color = if (enabled) accent else Color.White.copy(alpha = 0.4f),
        )
    }
}
