package com.daklok.crescendo.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.data.PieceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ThumbImage(file: File?, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            file?.takeIf { it.exists() }?.let { f ->
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()
            }
        }
    }
    Box(modifier = modifier.background(Color.White), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = Color(0xFFB0B0B0),
            )
        }
    }
}

private fun progressOf(piece: PieceEntity): Float =
    if (piece.pageCount <= 0 || piece.lastOpenedAt == 0L) 0f
    else ((piece.lastPage + 1).toFloat() / piece.pageCount).coerceIn(0f, 1f)

@Composable
fun PieceCard(
    piece: PieceEntity,
    thumb: File?,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            ThumbImage(
                file = thumb,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .clip(MaterialTheme.shapes.large),
            )
            FilledIconToggleButton(
                checked = piece.favorite,
                onCheckedChange = { onToggleFavorite() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Icon(
                    if (piece.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Obľúbené",
                )
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.padding(8.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                    ),
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Viac")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Premenovať") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; renameOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Presunúť do priečinka") },
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                        onClick = { menuOpen = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Odstrániť") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menuOpen = false; deleteOpen = true },
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                piece.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val progress = progressOf(piece)
            Text(
                if (progress > 0f) "${piece.pageCount} strán · strana ${piece.lastPage + 1}"
                else "${piece.pageCount} strán",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress > 0f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                )
            }
        }
    }

    if (renameOpen) {
        var text by remember { mutableStateOf(piece.title) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Premenovať skladbu") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(text); renameOpen = false }) { Text("Uložiť") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Zrušiť") }
            },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Odstrániť skladbu?") },
            text = { Text("„${piece.title}“ sa odstráni z knižnice aj z úložiska aplikácie.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); deleteOpen = false }) { Text("Odstrániť") }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text("Zrušiť") }
            },
        )
    }
}

/** Veľká karta „Pokračovať v čítaní“ nad mriežkou. */
@Composable
fun ContinueCard(
    piece: PieceEntity,
    thumb: File?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ThumbImage(
                file = thumb,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(0.74f)
                    .clip(MaterialTheme.shapes.medium),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Pokračovať v čítaní",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    piece.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onOpen) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Strana ${piece.lastPage + 1} z ${piece.pageCount}")
                }
            }
        }
    }
}
