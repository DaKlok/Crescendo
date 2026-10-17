package com.daklok.crescendo.ui.library

import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.PostAdd
import com.daklok.crescendo.ui.common.isCompactWidth
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.ripple
import androidx.compose.foundation.combinedClickable
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
    onExport: () -> Unit,
    onAddToSetlist: () -> Unit,
    onAppend: () -> Unit,
    onEditDetails: () -> Unit,
    onEditPages: () -> Unit,
    onSplit: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
) {
    val haptic = rememberHaptic()
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .pressScale(interaction)
            .selectedLook(selected)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    if (selectionMode) onSelect() else onOpen()
                },
                onLongClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onSelect()
                },
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(Modifier.padding(8.dp)) {
            ThumbImage(
                file = thumb,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(18.dp)),
            )
            // plný názov: v Box vnútri Card (ColumnScope) by sa inak vybralo ColumnScope.AnimatedVisibility
            androidx.compose.animation.AnimatedVisibility(
                visible = !selectionMode,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                FavoriteButton(
                    checked = piece.favorite,
                    onToggle = onToggleFavorite,
                    modifier = Modifier.padding(6.dp),
                )
            }
            SelectionCheck(
                visible = selectionMode,
                checked = selected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
            // plný názov: v Box vnútri Card (ColumnScope) by sa inak vybralo ColumnScope.AnimatedVisibility
            androidx.compose.animation.AnimatedVisibility(
                visible = !selectionMode,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
              Box {
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
                        text = { Text(stringResource(R.string.edit_details)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menuOpen = false; onEditDetails() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_to_folder)) },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                        onClick = { menuOpen = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.append_pages)) },
                        leadingIcon = { Icon(Icons.Outlined.PostAdd, null) },
                        onClick = { menuOpen = false; onAppend() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_pages)) },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesomeMosaic, null) },
                        onClick = { menuOpen = false; onEditPages() },
                    )
                    if (piece.pageCount > 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.split_piece)) },
                            leadingIcon = { Icon(Icons.Outlined.CallSplit, null) },
                            onClick = { menuOpen = false; onSplit() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to_setlist)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) },
                        onClick = { menuOpen = false; onAddToSetlist() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_piece)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menuOpen = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_pdf)) },
                        leadingIcon = { Icon(Icons.Outlined.IosShare, null) },
                        onClick = { menuOpen = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menuOpen = false; deleteOpen = true },
                    )
                }
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
            if (piece.composer.isNotBlank()) {
                Text(
                    piece.composer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

/** Pri výbere: vybraná karta sa jemne zmenší a dostane rámik. */
@Composable
fun Modifier.selectedLook(selected: Boolean): Modifier {
    val scale by animateFloatAsState(if (selected) 0.95f else 1f, Motion.bouncy(), label = "sel")
    val border by animateDpAsState(if (selected) 3.dp else 0.dp, Motion.bouncy(), label = "selBorder")
    val color = MaterialTheme.colorScheme.primary
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (border > 0.dp) Modifier.border(border, color, RoundedCornerShape(24.dp)) else Modifier)
}

/** Krúžok s fajkou v rohu karty v režime výberu. */
@Composable
fun SelectionCheck(visible: Boolean, checked: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(Motion.bouncy()),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        val bg by animateColorAsState(
            if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
            label = "checkBg",
        )
        Box(
            Modifier
                .size(30.dp)
                .background(bg, CircleShape)
                .border(2.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = checked,
                enter = scaleIn(Motion.bouncy()) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
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
    val compact = isCompactWidth()
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
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp),
        ) {
            ThumbImage(
                file = thumb,
                modifier = Modifier
                    .width(if (compact) 64.dp else 92.dp)
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
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
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
            if (compact) {
                // mobil: len ikona, aby zostalo miesto na názov
                FilledIconButton(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onOpen()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.page_n, piece.lastPage + 1))
                }
            } else {
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
}
