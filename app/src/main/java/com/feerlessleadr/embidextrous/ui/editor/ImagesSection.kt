package com.feerlessleadr.embidextrous.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.data.IMAGE_TYPE_LABELS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The list's custom images: show, replace, delete, and add one from the phone's photo picker. */
@Composable
fun ImagesSection(state: EditorUiState, vm: EditorViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingType by remember { mutableStateOf("Primary") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read the photo.")
                    Triple(mime, bytes, extensionFor(mime))
                }
            }
            picked.onSuccess { (mime, bytes, ext) -> vm.uploadImage(pendingType, "image.$ext", mime, bytes) }
                .onFailure { vm.imageProblem(it.message ?: "Could not read the photo.") }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Your own pictures for the Emby playlist or collection. Without them Emby shows a collage of the items.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.images.forEach { image ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bitmap = state.previews[image.type]
                    Box(Modifier.size(72.dp)) {
                        if (bitmap != null) {
                            Image(bitmap.asImageBitmap(), contentDescription = image.type, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
                        } else {
                            CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center), strokeWidth = 2.dp)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(IMAGE_TYPE_LABELS.firstOrNull { it.first == image.type }?.second ?: image.type, style = MaterialTheme.typography.titleSmall)
                        Text("${image.size / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.deleteImage(image.type) }, enabled = !state.imageBusy) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete image", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        DropdownField(
            label = "Add or replace",
            selectedText = IMAGE_TYPE_LABELS.firstOrNull { it.first == pendingType }?.second ?: pendingType,
            options = IMAGE_TYPE_LABELS,
            onSelect = { pendingType = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !state.imageBusy,
        ) {
            if (state.imageBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Choose a photo")
        }
        state.imageMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun extensionFor(mime: String): String = when (mime.lowercase()) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/bmp" -> "bmp"
    "image/avif" -> "avif"
    else -> "jpg"
}
