package com.daklok.crescendo.ui.library

import com.daklok.crescendo.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Náhľad prvej strany; po načítaní sa jemne prelínaním objaví. */
@Composable
fun ThumbImage(file: File?, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            file?.takeIf { it.exists() }?.let { f -> BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
        }
    }
    Box(modifier = modifier.background(Color.White), contentAlignment = Alignment.Center) {
        Crossfade(targetState = bitmap, animationSpec = tween(350), label = "thumb") { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.LibraryMusic, contentDescription = null, tint = Color(0xFFB8B8B8))
                }
            }
        }
    }
}

private fun progressOf(piece: PieceEntity): Float =
    if (piece.pageCount <= 0 || piece.lastOpenedAt == 0L) 0f
    else ((piece.lastPage + 1).toFloat() / piece.pageCount).coerceIn(0f, 1f)

/** Hviezdička, ktorá pri označení pružne „poskočí“. */
@Composable
private fun FavoriteButton(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, Motion.bouncy())
        }
    }
    FilledIconButton(
        onClick = {
            haptic(HapticFeedbackType.LongPress)
            onToggle()
        },
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (checked) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            contentColor = if (checked) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            if (checked) Icons.Rounded.Star else Icons.Outlined.StarOutline,
            contentDescription = if (checked) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add),
            modifier = Modifier.size(20.dp),
        )
    }
}

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
    val haptic = rememberHaptic()
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    Card(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onOpen()
        },
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.padding(8.dp)) {
            ThumbImage(
                file = thumb,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(18.dp)),
            )
            FavoriteButton(
                checked = piece.favorite,
                onToggle = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                FilledIconButton(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        menuOpen = true
                    },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menuOpen = false; renameOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_to_folder)) },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                        onClick = { menuOpen = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menuOpen = false; deleteOpen = true },
                    )
                }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = 2.dp)) {
            Text(
                piece.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val progress = progressOf(piece)
            Text(
                if (progress > 0f) stringResource(R.string.page_x_of_y, piece.lastPage + 1, piece.pageCount)
                else pluralStringResource(R.plurals.pages, piece.pageCount, piece.pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = progress > 0f,
                enter = expandVertically(Motion.bouncy()) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    drawStopIndicator = {},
                )
            }
        }
    }

    if (renameOpen) {
        var text by remember { mutableStateOf(piece.title) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.rename_piece)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(text); renameOpen = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.delete_piece_title)) },
            text = { Text(stringResource(R.string.delete_piece_text, piece.title)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onDelete()
                    deleteOpen = false
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Veľká karta „Pokračovať v čítaní“. */
@Composable
fun ContinueCard(
    piece: PieceEntity,
    thumb: File?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val progress = progressOf(piece)
    Card(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onOpen()
        },
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ThumbImage(
                file = thumb,
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.continue_reading),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    piece.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    drawStopIndicator = {},
                )
            }
            Button(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onOpen()
                },
                shape = RoundedCornerShape(20.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.page_n, piece.lastPage + 1))
            }
        }
    }
}
