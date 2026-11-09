package com.carola.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carola.player.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text("Ajustes", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SettingSwitch(
            "Descargar portadas de internet",
            "Si la canción no trae portada ni hay un cover.jpg en la carpeta, se busca en MusicBrainz. Necesita internet (por ejemplo, el hotspot del teléfono).",
            settings.onlineCovers, vm::setOnlineCovers,
        )
        SettingSwitch(
            "Reanudar al encender",
            "Al abrir la app continúa la última canción donde se quedó.",
            settings.autoResume, vm::setAutoResume,
        )

        Spacer(Modifier.height(20.dp))
        Row {
            FilledTonalButton(onClick = { vm.rescan() }, enabled = !scan.running, modifier = Modifier.height(54.dp)) {
                Text(if (scan.running) "Escaneando…" else "Volver a escanear la música", fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { vm.clearCovers() }, modifier = Modifier.height(54.dp)) {
                Text("Borrar portadas guardadas", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Almacenamiento detectado", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (roots.isEmpty()) {
            Text("Ninguno por ahora.", fontSize = 16.sp, color = Color.White.copy(alpha = 0.6f))
        }
        roots.forEach {
            Text("• ${it.name}   ${it.path}", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Carola 1.0 · Portadas: MusicBrainz y Cover Art Archive",
            fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp)
            Text(description, fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}
