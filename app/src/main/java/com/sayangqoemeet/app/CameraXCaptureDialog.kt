package com.sayangqoemeet.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import java.io.File

/** Camera capture stays on-device until the user explicitly chooses Send. */
@Composable
internal fun CameraXCaptureDialog(
    onDismiss: () -> Unit,
    onCaptured: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var captureInProgress by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                imageCapture = capture
                cameraError = null
            } catch (error: Exception) {
                cameraError = error.message ?: "Camera could not be started on this device."
            }
        }, mainExecutor)
        onDispose {
            disposed = true
            imageCapture = null
            if (providerFuture.isDone) {
                runCatching { providerFuture.get().unbindAll() }
            }
        }
    }

    BackHandler(enabled = true, onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0B1222)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("CAMERAX CAPTURE", color = Color(0xFF55F4FF))
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxWidth().height(390.dp)
                )
                cameraError?.let { Text(it, color = Color(0xFFFF9DAF)) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !captureInProgress,
                        modifier = Modifier.weight(1f)
                    ) { Text("CANCEL", color = Color(0xFF9BAAC5)) }
                    Button(
                        onClick = {
                            val capture = imageCapture
                            if (capture == null) {
                                cameraError = "Camera is still starting. Try again in a moment."
                                return@Button
                            }
                            captureInProgress = true
                            cameraError = null
                            val directory = File(context.cacheDir, "attachments").apply { mkdirs() }
                            val file = File(directory, "camera-${java.util.UUID.randomUUID()}.jpg")
                            val output = ImageCapture.OutputFileOptions.Builder(file).build()
                            capture.takePicture(output, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    captureInProgress = false
                                    runCatching {
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                    }.onSuccess(onCaptured)
                                        .onFailure {
                                            file.delete()
                                            cameraError = it.message ?: "Could not prepare the captured photo."
                                        }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    captureInProgress = false
                                    file.delete()
                                    cameraError = exception.message ?: "Photo capture failed."
                                }
                            })
                        },
                        enabled = !captureInProgress,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12364A))
                    ) { Text(if (captureInProgress) "CAPTURING…" else "CAPTURE", color = Color(0xFF55F4FF)) }
                }
            }
        }
    }
}
