package com.carola.player.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carola.player.MainViewModel

private val DangerRed = Color(0xFFFF5A5F)

/** Menú de acciones, confirmación de borrado, permiso de la USB y avisos cortos. */
@Composable
fun ManageDialogs(vm: MainViewModel) {
    val context = LocalContext.current
    val menu by vm.menu.collectAsStateWithLifecycle()
    val confirm by vm.confirmDelete.collectAsStateWithLifecycle()
    val access by vm.needAccess.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.onAccessGranted(uri)
    }

    menu?.let { m ->
        AlertDialog(
            onDismissRequest = vm::closeMenu,
            title = { Text(m.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text(m.subtitle, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 2)
                    Spacer(Modifier.height(12.dp))
                    if (m.addPaths.isNotEmpty()) {
                        MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Agregar a lista…", MaterialTheme.colorScheme.primary) {
                            vm.menuAddToList()
                        }
                    }
                    MenuRow(
                        Icons.Rounded.DeleteForever,
                        if (m.isFolder) "Eliminar carpeta" else if (m.targets.size > 1) "Eliminar álbum" else "Eliminar canción",
                        DangerRed,
                    ) { vm.menuDelete() }
                }
            },
            confirmButton = { TextButton(onClick = vm::closeMenu) { Text("Cerrar") } },
        )
    }

    confirm?.let { m ->
        val what = when {
            m.isFolder -> "la carpeta \"${m.title}\" con todo lo que contiene"
            m.targets.size > 1 -> "el álbum \"${m.title}\" (${m.targets.size} canciones)"
            else -> "\"${m.title}\""
        }
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = DangerRed, modifier = Modifier.size(36.dp)) },
            title = { Text("¿Eliminar del dispositivo?") },
            text = {
                Text(
                    "Se borrará $what de la memoria. Esto no se puede deshacer.",
                    fontSize = 17.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = vm::performDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancelar") } },
        )
    }

    access?.let { root ->
        AlertDialog(
            onDismissRequest = vm::dismissAccess,
            icon = { Icon(Icons.Rounded.Usb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text("Permiso para borrar en la USB") },
            text = {
                Text(
                    "Android pide que autorices a Carola una sola vez para modificar \"${root.name}\".\n\n" +
                        "En la siguiente pantalla entra a esa memoria (sin abrir ninguna carpeta) y toca " +
                        "\"Usar esta carpeta\" o \"Seleccionar\", y luego \"Permitir\".",
                    fontSize = 16.sp,
                )
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        treePicker.launch(null)
                    } catch (e: Exception) {
                        vm.dismissAccess()
                        Toast.makeText(
                            context,
                            "Este radio no tiene el selector de archivos de Android, no se puede dar el permiso.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text("Elegir memoria") }
            },
            dismissButton = { TextButton(onClick = vm::dismissAccess) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, fontSize = 19.sp, color = if (tint == DangerRed) DangerRed else Color.White)
    }
}
