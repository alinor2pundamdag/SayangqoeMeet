package com.sayangqoemeet.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LocalMediaItem(
    val uri: Uri,
    val mimeType: String,
    val name: String,
    val album: String,
    val dateAdded: Long,
    val size: Long
)

@Composable
internal fun LocalMediaAlbumPicker(
    onSelected: (Uri, String, String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasLocalMediaAccess(context)) }
    var items by remember { mutableStateOf<List<LocalMediaItem>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf("All photos") }
    var albumMenuExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasAccess = grants.values.any { it } || hasLocalMediaAccess(context)
        if (!hasAccess) error = "Media permission was denied. You can still use the Android system picker."
    }

    LaunchedEffect(Unit) {
        if (!hasAccess) {
            val permissions = when {
                Build.VERSION.SDK_INT >= 34 -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
                Build.VERSION.SDK_INT >= 33 -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(hasAccess) {
        if (!hasAccess) return@LaunchedEffect
        loading = true
        error = null
        try {
            items = withContext(Dispatchers.IO) { queryLocalMedia(context) }
            if (items.isEmpty()) error = "No accessible photos or videos were found on this device."
        } catch (exception: Exception) {
            error = exception.message ?: "Could not read local albums."
        } finally {
            loading = false
        }
    }

    val albums = remember(items) {
        listOf("All photos") + items.map { it.album }.filter { it.isNotBlank() && it != "All photos" }.distinct().sorted()
    }
    val visibleItems = remember(items, selectedAlbum) {
        (if (selectedAlbum == "All photos") items else items.filter { it.album == selectedAlbum })
            .filter { supportedChatAttachmentMimeType(it.mimeType) != null && it.size in 1..(25L * 1024L * 1024L) }
            .sortedByDescending { it.dateAdded }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF10182B)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SELECT ALBUM", color = Color(0xFF55F4FF), fontSize = 13.sp)
                Box {
                    TextButton(onClick = { albumMenuExpanded = true }) {
                        Text("$selectedAlbum  ▾", color = Color.White, fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = albumMenuExpanded, onDismissRequest = { albumMenuExpanded = false }) {
                        albums.forEach { album ->
                            DropdownMenuItem(
                                text = { Text(album) },
                                onClick = {
                                    selectedAlbum = album
                                    albumMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                when {
                    loading -> Text("Loading local albums…", color = Color.LightGray, fontSize = 12.sp)
                    visibleItems.isEmpty() -> Text(error ?: "No supported media in this album.", color = Color.LightGray, fontSize = 12.sp)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(visibleItems, key = { it.uri.toString() }) { item ->
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(0.78f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF202B40))
                                    .clickable { onSelected(item.uri, item.mimeType, item.name) }
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (item.mimeType.startsWith("video/")) {
                                    Text(
                                        "VIDEO",
                                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                            .background(Color(0x99000000)).padding(4.dp),
                                        color = Color.White,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = Color(0xFFFF9DAF), fontSize = 11.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("CLOSE", color = Color.LightGray) }
                }
            }
        }
    }
}

private fun hasLocalMediaAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    val image = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    val video = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    val selected = Build.VERSION.SDK_INT >= 34 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    return image || video || selected
}

private fun queryLocalMedia(context: Context): List<LocalMediaItem> {
    val resolver = context.contentResolver
    val result = mutableListOf<LocalMediaItem>()
    val imageAccess = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
        (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
    val videoAccess = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
        (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)

    fun readCollection(collection: Uri, isVideo: Boolean) {
        val idColumn = MediaStore.MediaColumns._ID
        val nameColumn = MediaStore.MediaColumns.DISPLAY_NAME
        val mimeColumn = MediaStore.MediaColumns.MIME_TYPE
        val dateColumn = MediaStore.MediaColumns.DATE_ADDED
        val sizeColumn = MediaStore.MediaColumns.SIZE
        val bucketColumn = if (isVideo) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        val projection = arrayOf(idColumn, nameColumn, mimeColumn, dateColumn, sizeColumn, bucketColumn)
        resolver.query(collection, projection, null, null, "$dateColumn DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(idColumn)
            val nameIndex = cursor.getColumnIndexOrThrow(nameColumn)
            val mimeIndex = cursor.getColumnIndexOrThrow(mimeColumn)
            val dateIndex = cursor.getColumnIndexOrThrow(dateColumn)
            val sizeIndex = cursor.getColumnIndexOrThrow(sizeColumn)
            val bucketIndex = cursor.getColumnIndexOrThrow(bucketColumn)
            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeIndex).orEmpty()
                val name = cursor.getString(nameIndex).orEmpty()
                val size = cursor.getLong(sizeIndex)
                if (supportedChatAttachmentMimeType(mime) == null || size !in 1..(25L * 1024L * 1024L)) continue
                val id = cursor.getLong(idIndex)
                val album = cursor.getString(bucketIndex)?.takeIf { it.isNotBlank() } ?: "Other"
                result += LocalMediaItem(
                    uri = ContentUris.withAppendedId(collection, id),
                    mimeType = mime,
                    name = name.ifBlank { "media-$id" },
                    album = album,
                    dateAdded = cursor.getLong(dateIndex),
                    size = size
                )
            }
        }
    }

    if (imageAccess) readCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
    if (videoAccess) readCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
    return result.distinctBy { it.uri }.sortedByDescending { it.dateAdded }
}
