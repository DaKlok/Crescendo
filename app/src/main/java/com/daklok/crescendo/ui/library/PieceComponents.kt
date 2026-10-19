package com.daklok.crescendo.ui.library

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.indication
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.PlayCookie
import com.daklok.crescendo.ui.common.PageProgress
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.PostAdd
import com.daklok.crescendo.ui.common.isCompactWidth
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
            .size(34.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        shape = ExpressiveShapes.Cookie,
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
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Skladba v knižnici. V mriežke je to list nôt „na poličke“ (papier s tieňom, pod ním názov),
 * v zozname ([list]) riadok s listom vľavo a všetkými údajmi vpravo. [dense] = úzka bunka hustej mriežky.
 */
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
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    list: Boolean = false,
    dense: Boolean = false,
) {
    val haptic = rememberHaptic()
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    // ukazovateľ strán má zmysel len pri otvorenej skladbe s viac ako jednou stranou
    val opened = piece.lastOpenedAt > 0L && piece.pageCount > 1
    val pageLabel = stringResource(R.string.page_x_of_y, piece.lastPage + 1, piece.pageCount)

    val menu: @Composable (Dp) -> Unit = { size ->
        PieceMenu(
            piece = piece,
            expanded = menuOpen,
            onExpandedChange = { menuOpen = it },
            buttonSize = size,
            onToggleFavorite = onToggleFavorite,
            onEditDetails = onEditDetails,
            onMove = onMove,
            onAppend = onAppend,
            onEditPages = onEditPages,
            onSplit = onSplit,
            onAddToSetlist = onAddToSetlist,
            onExport = onExport,
            onDelete = { deleteOpen = true },
        )
    }
    // strany: posuvník a „4/6“ (neotvorená skladba: len počet strán)
    val pages: @Composable (Modifier, Boolean) -> Unit = { m, small ->
        Row(
            modifier = m.clearAndSetSemantics { if (opened) contentDescription = pageLabel },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (opened) {
                PageProgress(
                    page = piece.lastPage,
                    pages = piece.pageCount,
                    modifier = Modifier.weight(1f),
                    trackHeight = if (small) 4.dp else 5.dp,
                    handleHeight = if (small) 12.dp else 14.dp,
                )
                Spacer(Modifier.width(if (small) 8.dp else 10.dp))
                Text(
                    stringResource(R.string.page_fraction, piece.lastPage + 1, piece.pageCount),
                    style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            } else {
                Text(
                    pluralStringResource(R.plurals.pages, piece.pageCount, piece.pageCount),
                    style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
    val click = Modifier.combinedClickable(
        interactionSource = interaction,
        // v mriežke sa vlnka kreslí len na liste papiera (SheetThumb), tieň listu sa tak neoreže
        indication = if (list) ripple() else null,
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            if (selectionMode) onSelect() else onOpen()
        },
        onLongClick = {
            haptic(HapticFeedbackType.LongPress)
            onSelect()
        },
    )

    if (list) {
        PieceRow(
            piece = piece,
            thumb = thumb,
            opened = opened,
            interaction = interaction,
            click = click,
            selectionMode = selectionMode,
            selected = selected,
            onToggleFavorite = onToggleFavorite,
            menu = menu,
            pages = pages,
            modifier = modifier,
        )
    } else {
        // skladba = list nôt „na poličke“: papier s tieňom, pod ním názov (bez rámčeka karty)
        Column(
            modifier = modifier
                .pressScale(interaction)
                .then(click),
        ) {
            Box {
                SheetThumb(
                    file = thumb,
                    interaction = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.74f)
                        .selectedLook(selected, SheetShape),
                )
                // obľúbená skladba: hviezda v rohu listu (ťuk ju odoberie)
                androidx.compose.animation.AnimatedVisibility(
                    visible = piece.favorite && !selectionMode,
                    enter = fadeIn() + scaleIn(Motion.bouncy()),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    FavoriteButton(checked = true, onToggle = onToggleFavorite, modifier = Modifier.padding(4.dp))
                }
                SelectionCheck(
                    visible = selectionMode,
                    checked = selected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
            Row(Modifier.padding(top = if (dense) 8.dp else 10.dp), verticalAlignment = Alignment.Top) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp, top = 2.dp),
                ) {
                    Text(
                        piece.title,
                        style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (piece.composer.isNotBlank()) {
                        Text(
                            piece.composer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // plný názov: v Row by sa inak vybralo RowScope.AnimatedVisibility
                androidx.compose.animation.AnimatedVisibility(
                    visible = !selectionMode,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    menu(if (dense) 32.dp else 36.dp)
                }
            }
            pages(Modifier.padding(start = 4.dp, top = if (dense) 6.dp else 8.dp, bottom = 2.dp), dense)
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
            icon = { com.daklok.crescendo.ui.common.DialogIcon(Icons.Outlined.Delete, destructive = true) },
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

/** Tlačidlo ⋮ s ponukou skladby (rovnaká v mriežke aj v zozname). */
@Composable
private fun PieceMenu(
    piece: PieceEntity,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    buttonSize: Dp,
    onToggleFavorite: () -> Unit,
    onEditDetails: () -> Unit,
    onMove: () -> Unit,
    onAppend: () -> Unit,
    onEditPages: () -> Unit,
    onSplit: () -> Unit,
    onAddToSetlist: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = rememberHaptic()
    val close = { onExpandedChange(false) }
    Box {
        IconButton(
            onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                onExpandedChange(true)
            },
            modifier = Modifier.size(buttonSize),
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more), modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = close,
            shape = RoundedCornerShape(20.dp),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(if (piece.favorite) R.string.favorite_remove else R.string.favorite_add)) },
                leadingIcon = { Icon(if (piece.favorite) Icons.Rounded.Star else Icons.Outlined.StarOutline, null) },
                onClick = { close(); onToggleFavorite() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_details)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { close(); onEditDetails() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.move_to_folder)) },
                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                onClick = { close(); onMove() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.append_pages)) },
                leadingIcon = { Icon(Icons.Outlined.PostAdd, null) },
                onClick = { close(); onAppend() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_pages)) },
                leadingIcon = { Icon(Icons.Outlined.AutoAwesomeMosaic, null) },
                onClick = { close(); onEditPages() },
            )
            if (piece.pageCount > 1) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.split_piece)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, null) },
                    onClick = { close(); onSplit() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_setlist)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) },
                onClick = { close(); onAddToSetlist() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_pdf)) },
                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                onClick = { close(); onExport() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = { close(); onDelete() },
            )
        }
    }
}

/**
 * Skladba v zozname: list nôt vľavo, vpravo názov, skladateľ, údaje (nástroj, tónina, štítky, strany)
 * a kde si skončil. Obľúbená má hviezdu, v režime výberu je vpravo krúžok s fajkou.
 */
@Composable
private fun PieceRow(
    piece: PieceEntity,
    thumb: File?,
    opened: Boolean,
    interaction: MutableInteractionSource,
    click: Modifier,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleFavorite: () -> Unit,
    menu: @Composable (Dp) -> Unit,
    pages: @Composable (Modifier, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val compact = isCompactWidth()
    val shape = RoundedCornerShape(24.dp)
    val bg by animateColorAsState(
        if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainer,
        label = "rowBg",
    )
    val meta = buildList {
        if (piece.instrument.isNotBlank()) add(piece.instrument)
        if (piece.keySignature.isNotBlank()) add(piece.keySignature)
        addAll(piece.tagList.take(2))
        // pri neotvorenej skladbe je počet strán v riadku so stranami
        if (opened) add(pluralStringResource(R.plurals.pages, piece.pageCount, piece.pageCount))
    }.joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.985f)
            .selectedLook(selected, shape)
            .clip(shape)
            .background(bg)
            .then(click)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetThumb(
            file = thumb,
            elevation = 3.dp,
            modifier = Modifier
                .width(if (compact) 64.dp else 76.dp)
                .aspectRatio(0.74f),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                piece.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (piece.composer.isNotBlank()) {
                Text(
                    piece.composer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            pages(
                Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 360.dp),
                false,
            )
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.animation.AnimatedVisibility(
            visible = piece.favorite && !selectionMode,
            enter = fadeIn() + scaleIn(Motion.bouncy()),
            exit = fadeOut() + scaleOut(),
        ) {
            FavoriteButton(checked = true, onToggle = onToggleFavorite)
        }
        if (selectionMode) {
            SelectionCheck(visible = true, checked = selected, modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            menu(40.dp)
        }
    }
}

/** Pri výbere: vybraná karta sa jemne zmenší a dostane rámik. */
@Composable
fun Modifier.selectedLook(selected: Boolean, shape: Shape = RoundedCornerShape(24.dp)): Modifier {
    val scale by animateFloatAsState(if (selected) 0.95f else 1f, Motion.bouncy(), label = "sel")
    val border by animateDpAsState(if (selected) 3.dp else 0.dp, Motion.bouncy(), label = "selBorder")
    val color = MaterialTheme.colorScheme.primary
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (border > 0.dp) Modifier.border(border, color, shape) else Modifier)
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

/** Tvar listu nôt (jemne zaoblený papier). */
val SheetShape = RoundedCornerShape(10.dp)

/** Prvá strana nôt ako list papiera s tieňom; s [interaction] sa po ťuknutí po papieri rozleje vlnka. */
@Composable
fun SheetThumb(
    file: File?,
    modifier: Modifier = Modifier,
    elevation: Dp = 3.dp,
    interaction: InteractionSource? = null,
) {
    Surface(
        modifier = modifier,
        shape = SheetShape,
        color = Color.White,
        shadowElevation = elevation,
    ) {
        Box {
            ThumbImage(file = file, modifier = Modifier.fillMaxSize())
            if (interaction != null) {
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
 * Hlavná karta knižnice „Pokračuj“: list nôt (mierne naklonený, ako položený na pulte),
 * veľký názov, postup ako crescendo a vlnité tlačidlo prehrať.
 */
@Composable
fun ContinueCard(
    piece: PieceEntity,
    thumb: File?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val compact = isCompactWidth()
    val scheme = MaterialTheme.colorScheme
    val deco = scheme.primary.copy(alpha = 0.08f)
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onOpen()
        },
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.985f),
        shape = RoundedCornerShape(if (compact) 32.dp else 40.dp),
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier
                // veľký jemný vlnitý kruh za tlačidlom prehrať (orezaný tvarom karty)
                .drawBehind {
                    val big = size.height * if (compact) 1.1f else 1.25f
                    val outline = ExpressiveShapes.Cookie12.createOutline(Size(big, big), layoutDirection, this)
                    translate(left = size.width - big * 0.62f, top = (size.height - big) / 2f) {
                        rotate(8f, pivot = Offset(big / 2f, big / 2f)) { drawOutline(outline, deco) }
                    }
                }
                .padding(
                    start = if (compact) 18.dp else 28.dp,
                    end = if (compact) 14.dp else 24.dp,
                    top = if (compact) 20.dp else 26.dp,
                    bottom = if (compact) 20.dp else 26.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetThumb(
                file = thumb,
                elevation = 8.dp,
                modifier = Modifier
                    .width(if (compact) 72.dp else 108.dp)
                    .aspectRatio(0.74f)
                    .graphicsLayer { rotationZ = -3f },
            )
            Spacer(Modifier.width(if (compact) 18.dp else 28.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.continue_reading),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    piece.title,
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (piece.composer.isNotBlank()) {
                    Text(
                        piece.composer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                PageProgress(
                    page = piece.lastPage,
                    pages = piece.pageCount,
                    modifier = Modifier.widthIn(max = 480.dp),
                    color = scheme.primary,
                    trackColor = scheme.primary.copy(alpha = 0.22f),
                    trackHeight = if (compact) 7.dp else 9.dp,
                    handleHeight = if (compact) 18.dp else 22.dp,
                    handleWidth = 4.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.page_x_of_y, piece.lastPage + 1, piece.pageCount.coerceAtLeast(1)),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            PlayCookie(
                size = if (compact) 56.dp else 76.dp,
                contentDescription = stringResource(R.string.continue_reading),
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onOpen()
                },
            )
        }
    }
}

/** Malá karta naposledy otvorenej skladby (pás pod „Pokračuj“). */
@Composable
fun RecentChip(piece: PieceEntity, thumb: File?, onOpen: () -> Unit) {
    val haptic = rememberHaptic()
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onOpen()
        },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(232.dp),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SheetThumb(
                file = thumb,
                elevation = 1.dp,
                modifier = Modifier
                    .width(32.dp)
                    .aspectRatio(0.74f),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(piece.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.page_n, piece.lastPage + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
