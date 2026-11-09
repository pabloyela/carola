package com.carola.player.ui

import androidx.compose.foundation.background
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
    FOLDERS("Carpetas", Icons.Rounded.Folder),
    ALBUMS("Álbumes", Icons.Rounded.Album),
    ARTISTS("Artistas", Icons.Rounded.Person),
    SETTINGS("Ajustes", Icons.Rounded.Settings),
}

@Composable
fun CarolaApp(vm: MainViewModel) {
    val pb by vm.playback.collectAsStateWithLifecycle()
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
                Spacer(Modifier.height(8.dp))
                Tab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label, modifier = Modifier.size(30.dp)) },
                        label = { Text(t.label, fontSize = 13.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.White.copy(alpha = 0.55f),
                            unselectedTextColor = Color.White.copy(alpha = 0.55f),
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f)) {
                    val goNow = { tab = Tab.NOW }
                    when (tab) {
                        Tab.NOW -> NowPlayingScreen(vm, onBrowse = { tab = Tab.FOLDERS })
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
    }
}
