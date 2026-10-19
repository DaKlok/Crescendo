package com.daklok.crescendo.ui.library

import com.daklok.crescendo.ui.common.ExpressiveShapes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import java.io.File
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.indication
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.ripple
import androidx.compose.foundation.combinedClickable
import com.daklok.crescendo.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.data.FolderEntity

@Composable
fun FolderCard(
    folder: FolderEntity,
    itemCount: Int,
    /** Náhľady skladieb v priečinku (najviac 3), vytŕčajú z priečinka ako listy papiera. */
    thumbs: List<File?>,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onColor: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
) {
    val haptic = rememberHaptic()
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val tint = folder.color?.let { Color(it) } ?: scheme.primary
    val empty = itemCount == 0
    // zadná stena so záložkou je sýtejšia, predná chlopňa jemne zafarbená
    // v tmavom režime tlmenejšie, inak by bledá zadná stena svietila
    val dark = scheme.background.luminance() < 0.4f
    val back by animateColorAsState(
        when {
            selected -> scheme.primary
            empty -> lerp(scheme.surfaceContainerHighest, tint, if (dark) 0.2f else 0.3f)
            else -> lerp(scheme.surfaceContainerHighest, tint, if (dark) 0.42f else 0.62f)
        },
        tween(250),
        label = "folderBack",
    )
    val front by animateColorAsState(
        when {
            selected -> scheme.secondaryContainer
            folder.color != null -> lerp(scheme.surfaceContainerHigh, tint, 0.14f)
            else -> scheme.surfaceContainerHigh
        },
        tween(250),
        label = "folderFront",
    )
    val backShape = remember { FolderBackShape() }

    Box(
        modifier = modifier
            .height(104.dp)
            .pressScale(interaction)
            .selectedLook(selected, backShape)
            .combinedClickable(
                interactionSource = interaction,
                // vlnka len na prednej chlopni (listy nad priečinkom sa neorežú)
                indication = null,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    if (selectionMode) onSelect() else onOpen()
                },
                onLongClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onSelect()
                },
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(back, backShape),
        )
        // listy nôt, ktoré vytŕčajú z priečinka (najnovší vpredu)
        if (thumbs.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 22.dp, top = 3.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                val tilt = listOf(-4f, 6f, -9f)
                thumbs.take(3).asReversed().forEachIndexed { i, file ->
                    val pos = thumbs.size.coerceAtMost(3) - 1 - i
                    SheetThumb(
                        file = file,
                        elevation = 2.dp,
                        modifier = Modifier
                            .padding(end = (pos * 20).dp)
                            .width(32.dp)
                            .aspectRatio(0.74f)
                            .graphicsLayer { rotationZ = tilt[pos] },
                    )
                }
            }
        }
        // predná chlopňa s názvom
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
            color = front,
            shadowElevation = 1.dp,
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (empty) stringResource(R.string.folder_empty)
                            else pluralStringResource(R.plurals.items, itemCount, itemCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        SelectionCheck(visible = selectionMode, checked = selected)
                        // plný názov: v Box vnútri Row by sa inak vybralo RowScope.AnimatedVisibility
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !selectionMode,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            Box {
                                IconButton(onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    menuOpen = true
                                }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more))
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    shape = RoundedCornerShape(20.dp),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.rename)) },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                        onClick = { menuOpen = false; onRename() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.folder_color)) },
                                        leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                                        onClick = { menuOpen = false; onColor() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.move)) },
                                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                        onClick = { menuOpen = false; onMove() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.remove_folder)) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = { menuOpen = false; onDelete() },
                                    )
                                }
                            }
                        }
                    }
                }
                // vlnka po ťuknutí (orezaná tvarom chlopne)
                Box(
                    Modifier
                        .matchParentSize()
                        .indication(interaction, ripple()),
                )
            }
        }
    }
}

/**
 * Zadná stena priečinka so záložkou vľavo hore: záložka plynulo (krivkou) prechádza do hornej hrany.
 * Šírka záložky je [tabFraction] šírky (najviac [maxTab]).
 */
private class FolderBackShape(
    private val tabFraction: Float = 0.36f,
    private val tabHeight: Dp = 12.dp,
    private val radius: Dp = 22.dp,
    private val maxTab: Dp = 150.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val th = with(density) { tabHeight.toPx() }
        val tw = (w * tabFraction).coerceAtMost(with(density) { maxTab.toPx() })
        val r = with(density) { radius.toPx() }.coerceAtMost(minOf(w, h - th) / 2f).coerceAtLeast(0f)
        val tr = (r * 0.7f).coerceAtMost(th + r)
        val slope = th * 1.6f
        val path = Path().apply {
            // ľavý horný roh záložky
            moveTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            // horná hrana záložky a mäkký schod dole na hornú hranu priečinka
            lineTo(tw, 0f)
            cubicTo(tw + slope * 0.55f, 0f, tw + slope * 0.45f, th, tw + slope, th)
            lineTo(w - tr, th)
            arcTo(Rect(w - 2 * tr, th, w, th + 2 * tr), 270f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Dialóg s jedným textovým poľom (nový priečinok / premenovanie). */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    /** Návrh (napr. rozpoznaný názov) – nahradí predvolený text, ak ho používateľ ešte nezmenil. */
    suggestion: String? = null,
    /** Drobný text pod políčkom. */
    supporting: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(suggestion) {
        if (!suggestion.isNullOrBlank() && text == initial) text = suggestion
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = if (icon != null) {
            { com.daklok.crescendo.ui.common.DialogIcon(icon, ExpressiveShapes.Squircle, com.daklok.crescendo.ui.common.Accent.Secondary) }
        } else {
            null
        },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.name)) },
                supportingText = if (supporting != null) {
                    { Text(supporting) }
                } else {
                    null
                },
                shape = RoundedCornerShape(16.dp),
                // Enter na klávesnici potvrdí
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { if (text.isNotBlank()) onConfirm(text) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Výber cieľa pri presúvaní: knižnica alebo ľubovoľný priečinok (stromovo odsadené).
 * [excluded] = priečinky, ktoré sa nedajú vybrať (napr. presúvaný priečinok a jeho podpriečinky).
 */
@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    currentLocation: Long?,
    excluded: Set<Long> = emptySet(),
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tree = remember(folders, excluded) { flattenTree(folders, excluded) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.move_to)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    DestinationRow(
                        name = stringResource(R.string.library),
                        depth = 0,
                        isRoot = true,
                        selected = currentLocation == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(tree, key = { it.first.id }) { (folder, depth) ->
                    DestinationRow(
                        name = folder.name,
                        depth = depth + 1,
                        isRoot = false,
                        color = folder.color,
                        selected = currentLocation == folder.id,
                        onClick = { onSelect(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DestinationRow(
    name: String,
    depth: Int,
    isRoot: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    color: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !selected, onClick = onClick)
            .padding(start = (depth * 20).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isRoot) Icons.Outlined.LibraryMusic else Icons.Outlined.Folder,
            contentDescription = null,
            tint = color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
        )
        Box(Modifier.width(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.current_location))
    }
}

/** Strom priečinkov -> zoznam (priečinok, hĺbka) v poradí na zobrazenie. */
private fun flattenTree(folders: List<FolderEntity>, excluded: Set<Long>): List<Pair<FolderEntity, Int>> {
    val byParent = folders.filter { it.id !in excluded }.groupBy { it.parentId }
    val out = ArrayList<Pair<FolderEntity, Int>>()
    fun walk(parent: Long?, depth: Int) {
        if (depth > 32) return
        byParent[parent].orEmpty().sortedBy { it.name.lowercase() }.forEach { f ->
            out.add(f to depth)
            walk(f.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}
