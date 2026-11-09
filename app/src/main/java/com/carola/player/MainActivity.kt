package com.carola.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carola.player.ui.CarolaApp
import com.carola.player.ui.CarolaTheme
import com.carola.player.ui.DefaultAccent
import com.carola.player.ui.EmptyState

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var granted by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            if (ok) vm.onPermissionGranted()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        granted = hasPermission()
        if (granted) vm.onPermissionGranted() else askPermission()

        setContent {
            val accent by vm.accent.collectAsStateWithLifecycle()
            CarolaTheme(accent = accent?.let { Color(it) } ?: DefaultAccent) {
                if (granted) {
                    CarolaApp(vm)
                } else {
                    EmptyState(
                        Icons.Rounded.Usb,
                        "Permiso para leer tu música",
                        "Carola necesita acceso al almacenamiento para ver tus memorias USB.",
                    ) {
                        Row {
                            Button(onClick = { askPermission() }, modifier = Modifier.height(56.dp)) {
                                Text("Dar permiso", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(onClick = { openAppSettings() }, modifier = Modifier.height(56.dp)) {
                                Text("Abrir ajustes", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!granted && hasPermission()) {
            granted = true
            vm.onPermissionGranted()
        } else if (granted) {
            vm.refreshRoots()
        }
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun askPermission() {
        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }
}
