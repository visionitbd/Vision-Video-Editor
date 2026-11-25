package com.visionit.vcut

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.visionit.vcut.model.EditorTab
import com.visionit.vcut.ui.screens.EditorScreen
import com.visionit.vcut.ui.screens.HomeScreen
import com.visionit.vcut.ui.theme.VisionCutTheme
import com.visionit.vcut.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisionCutTheme {
                val vm: EditorViewModel = viewModel()
                val drafts by vm.drafts.collectAsState()

                var inEditor by remember { mutableStateOf(false) }
                var pendingInitialTab by remember { mutableStateOf(EditorTab.EDIT) }

                val newVideoPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) { }
                        vm.startNewProject(uri, pendingInitialTab)
                        inEditor = true
                    }
                }

                val extraClipPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) { }
                        vm.addClip(uri)
                    }
                }

                val musicPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) { }
                        vm.attachMusic(uri, "Music Track")
                    }
                }

                val imageOverlayPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        vm.setImageOverlay(uri)
                    }
                }

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        vm.toggleVoiceRecording()
                    }
                }

                if (!inEditor) {
                    HomeScreen(
                        drafts = drafts,
                        onPickNewVideo = { tab ->
                            pendingInitialTab = tab
                            newVideoPicker.launch(arrayOf("video/*"))
                        },
                        onOpenDraft = { draft ->
                            vm.openDraft(draft)
                            inEditor = true
                        },
                        onDeleteDraft = { id -> vm.deleteDraft(id) }
                    )
                } else {
                    EditorScreen(
                        viewModel = vm,
                        onBack = { inEditor = false },
                        onPickExtraClip = { extraClipPicker.launch(arrayOf("video/*")) },
                        onPickMusic = { musicPicker.launch(arrayOf("audio/*")) },
                        onPickImageOverlay = { imageOverlayPicker.launch(arrayOf("image/*")) },
                        onRequestRecordVoice = {
                            val hasMic = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                vm.toggleVoiceRecording()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
            }
        }
    }
}
