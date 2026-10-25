package com.sayangqoemeet.app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.video.AudioConfig
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor

/**
 * On-device CameraX capture activity. It writes only to app cache until the
 * user chooses SEND in the conversation preview; no cloud upload happens here.
 */
internal class CameraCaptureActivity : ComponentActivity() {
    private lateinit var controller: LifecycleCameraController
    private lateinit var preview: PreviewView
    private lateinit var caption: EditText
    private lateinit var captureButton: Button
    private lateinit var modeText: TextView
    private var mode = MODE_NORMAL
    private var frontCamera = false
    private var flashEnabled = false
    private var activeRecording: Recording? = null
    private var pendingVideoStart = false
    private val executor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        mode = intent.getStringExtra(EXTRA_START_MODE)?.takeIf { it == MODE_VIDEO } ?: MODE_NORMAL
        updateModeUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        modeText = TextView(this).apply {
            text = "NORMAL"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        toolbar.addView(modeText, LinearLayout.LayoutParams(0, dp(48), 1f))
        toolbar.addView(actionButton("TEXT") { setMode(MODE_TEXT) })
        toolbar.addView(actionButton("NORMAL") { setMode(MODE_NORMAL) })
        toolbar.addView(actionButton("VIDEO") { setMode(MODE_VIDEO) })
        toolbar.addView(actionButton("FLASH OFF") { toggleFlash() })
        toolbar.addView(actionButton("FLIP") { flipCamera() })
        root.addView(toolbar)

        val previewContainer = FrameLayout(this)
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        previewContainer.addView(preview, FrameLayout.LayoutParams(-1, -1))
        caption = EditText(this).apply {
            hint = "Add a caption"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFCCCCCC.toInt())
            setBackgroundColor(0x88000000.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        previewContainer.addView(caption, FrameLayout.LayoutParams(-1, dp(100), Gravity.BOTTOM))
        root.addView(previewContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(16))
        }
        controls.addView(actionButton("CANCEL") { cancelCapture() })
        captureButton = Button(this).apply {
            text = "CAPTURE"
            setOnClickListener { onCapturePressed() }
        }
        controls.addView(captureButton, LinearLayout.LayoutParams(0, dp(58), 1f))
        controls.addView(actionButton("DONE") { cancelCapture() })
        root.addView(controls)
        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 10f
            setOnClickListener { action() }
        }

    private fun bindCamera() {
        controller = LifecycleCameraController(this).apply {
            cameraSelector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            imageCaptureFlashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        }
        preview.controller = controller
        controller.bindToLifecycle(this)
    }

    private fun setMode(newMode: String) {
        if (newMode == MODE_VIDEO && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingVideoStart = false
            mode = MODE_VIDEO
            updateModeUi()
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        mode = newMode
        updateModeUi()
    }

    private fun updateModeUi() {
        if (!::modeText.isInitialized) return
        modeText.text = "MODE: $mode"
        caption.visibility = if (mode == MODE_TEXT) View.VISIBLE else View.GONE
        captureButton.text = when {
            mode == MODE_VIDEO && activeRecording != null -> "STOP"
            mode == MODE_VIDEO -> "RECORD"
            mode == MODE_TEXT -> "CAPTURE TEXT"
            else -> "CAPTURE"
        }
    }

    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        if (::controller.isInitialized) {
            controller.imageCaptureFlashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        }
        Toast.makeText(this, if (flashEnabled) "Flash on" else "Flash off", Toast.LENGTH_SHORT).show()
    }

    private fun flipCamera() {
        frontCamera = !frontCamera
        if (::controller.isInitialized) {
            controller.cameraSelector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun onCapturePressed() {
        if (!::controller.isInitialized) return
        if (mode == MODE_VIDEO) {
            if (activeRecording == null) startVideoRecording() else activeRecording?.stop()
        } else {
            if (mode == MODE_TEXT && caption.text.isNullOrBlank()) {
                caption.error = "Enter text before capturing."
                return
            }
            takePhoto()
        }
    }

    private fun takePhoto() {
        val file = File(cacheDir, "capture-${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        controller.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    if (mode == MODE_TEXT) burnCaptionIntoImage(file, caption.text.toString().trim())
                    returnCapture(file, "image/jpeg", file.name)
                } catch (error: Exception) {
                    file.delete()
                    Toast.makeText(this@CameraCaptureActivity, error.message ?: "Could not prepare photo.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                Toast.makeText(this@CameraCaptureActivity, exception.message ?: "Photo capture failed.", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun startVideoRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingVideoStart = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        val file = File(cacheDir, "capture-${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        try {
            activeRecording = controller.startRecording(options, AudioConfig.create(true), executor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                    updateModeUi()
                    if (event.hasError()) {
                        file.delete()
                        Toast.makeText(this, "Video capture failed (${event.error}).", Toast.LENGTH_LONG).show()
                    } else {
                        returnCapture(file, "video/mp4", file.name)
                    }
                }
                updateModeUi()
            }
            updateModeUi()
        } catch (error: Exception) {
            file.delete()
            activeRecording = null
            Toast.makeText(this, error.message ?: "Could not start video recording.", Toast.LENGTH_LONG).show()
        }
    }

    private fun burnCaptionIntoImage(file: File, text: String) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not process captured photo.")
        val canvas = Canvas(bitmap)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (bitmap.width * 0.055f).coerceIn(28f, 72f)
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 1f, 2f, Color.BLACK)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, (bitmap.width * 0.86f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()
        val padding = (bitmap.width * 0.04f).toInt()
        val top = (bitmap.height - layout.height - padding * 2).coerceAtLeast(0)
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
        canvas.drawRoundRect(
            RectF(padding.toFloat(), top.toFloat(), (bitmap.width - padding).toFloat(), (bitmap.height - padding).toFloat()),
            18f, 18f, box
        )
        canvas.save()
        canvas.translate(bitmap.width / 2f, top + padding.toFloat())
        canvas.translate(-layout.width / 2f, 0f)
        layout.draw(canvas)
        canvas.restore()
        FileOutputStream(file, false).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "Could not save captioned photo." }
        }
        bitmap.recycle()
    }

    private fun returnCapture(file: File, mime: String, name: String) {
        setResult(Activity.RESULT_OK, intent.apply {
            putExtra(EXTRA_CAPTURE_URI, Uri.fromFile(file).toString())
            putExtra(EXTRA_CAPTURE_MIME, mime)
            putExtra(EXTRA_CAPTURE_NAME, name)
        })
        finish()
    }

    private fun cancelCapture() {
        activeRecording?.stop()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) bindCamera()
                else {
                    Toast.makeText(this, "Camera permission is required to capture media.", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
            REQUEST_AUDIO -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    mode = MODE_VIDEO
                    updateModeUi()
                } else {
                    mode = MODE_NORMAL
                    updateModeUi()
                    Toast.makeText(this, "Microphone permission is required for video audio.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_START_MODE = "camera_start_mode"
        const val EXTRA_CAPTURE_URI = "capture_uri"
        const val EXTRA_CAPTURE_MIME = "capture_mime"
        const val EXTRA_CAPTURE_NAME = "capture_name"
        const val MODE_TEXT = "TEXT"
        const val MODE_NORMAL = "NORMAL"
        const val MODE_VIDEO = "VIDEO"
        private const val REQUEST_CAMERA = 7301
        private const val REQUEST_AUDIO = 7302
    }
}
