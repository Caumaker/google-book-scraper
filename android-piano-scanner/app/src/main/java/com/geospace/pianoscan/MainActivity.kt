package com.geospace.pianoscan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geospace.pianoscan.ui.CameraCapture
import com.geospace.pianoscan.ui.CaptureScreen
import com.geospace.pianoscan.ui.PlayerScreen
import com.geospace.pianoscan.ui.ScoreViewModel
import com.geospace.pianoscan.ui.SettingsDialog
import com.geospace.pianoscan.ui.Stage
import com.geospace.pianoscan.ui.theme.PianoScanTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PianoScanTheme {
                Surface(Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App(vm: ScoreViewModel = viewModel()) {
    val state by vm.ui.collectAsState()
    val player by vm.playerState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showCamera by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.dismissError()
        }
    }

    fun share(file: File?, mime: String, label: String) {
        if (file == null) return
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, label))
    }

    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        when {
            showCamera -> CameraCapture(
                onCaptured = { file ->
                    vm.addPage(file)
                    showCamera = false
                },
                onClose = { showCamera = false }
            )

            state.stage == Stage.CAPTURE -> CaptureScreen(
                state = state,
                onPickImage = { vm.addPage(it) },
                onOpenCamera = { showCamera = true },
                onRemovePage = { vm.removePage(it) },
                onHintChange = { vm.setHint(it) },
                onTranscribe = { vm.transcribe() },
                onDemo = { vm.loadDemo() },
                onOpenSettings = { showSettings = true }
            )

            else -> PlayerScreen(
                state = state,
                player = player,
                onBack = { vm.backToCapture() },
                onToggle = { vm.engine.toggle() },
                onStop = { vm.engine.stop() },
                onSeek = { vm.engine.seek(it) },
                onStyle = { vm.setStyle(it) },
                onTempo = { vm.setTempo(it) },
                onExportMidi = {
                    scope.launch {
                        vm.setBusy(true, "Gerando MIDI...")
                        val file = vm.exportMidi()
                        vm.setBusy(false)
                        share(file, "audio/midi", "Compartilhar MIDI")
                    }
                },
                onExportWav = {
                    scope.launch {
                        vm.setBusy(true, "Renderizando audio...")
                        val file = vm.exportWav()
                        vm.setBusy(false)
                        share(file, "audio/wav", "Compartilhar audio")
                    }
                }
            )
        }

        if (showSettings) {
            SettingsDialog(
                currentKey = vm.apiKey(),
                onSave = {
                    vm.saveApiKey(it)
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

