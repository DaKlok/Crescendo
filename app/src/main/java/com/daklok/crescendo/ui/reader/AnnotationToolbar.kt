package com.daklok.crescendo.ui.reader

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Approval
import com.daklok.crescendo.ui.common.Swatch
import com.daklok.crescendo.ui.common.ColorPickerDialog
import kotlin.math.abs
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Slider
import androidx.compose.foundation.shape.RoundedCornerShape
import com.daklok.crescendo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import com.daklok.crescendo.ui.common.Motion
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.data.InkTool
import com.daklok.crescendo.data.ShapeKind
import com.daklok.crescendo.data.ToolbarPosition

/**
 * Plávajúci panel nástrojov pre poznámky. Podľa [position] je zvislý (vľavo/vpravo)
 * alebo vodorovný (hore/dole). Farby a hrúbky sú schované v malých rozbaľovacích ponukách,
 * aby panel zaberal čo najmenej miesta.
 */
@Composable
fun AnnotationToolbar(
    controller: AnnotationController,
    fingerDrawing: Boolean,
    onFingerDrawingChange: (Boolean) -> Unit,
    pages: List<Int>,
    position: ToolbarPosition,
    onPositionChange: (ToolbarPosition) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val tool = controller.tool
    // zadarmo len pero a guma, ostatné nástroje sú v Pro
    val pro = (LocalContext.current.applicationContext as com.daklok.crescendo.CrescendoApp).pro
    val isPro by pro.isPro.collectAsStateWithLifecycle()
    fun free(t: InkTool) = t == InkTool.PEN || t == InkTool.ERASER
    fun pick(t: InkTool) {
        if (free(t) || pro.allow(com.daklok.crescendo.data.pro.ProFeature.ANNOTATIONS)) controller.selectTool(t)
    }
    LaunchedEffect(isPro) { if (!isPro && !free(controller.tool)) controller.selectTool(InkTool.PEN) }
    val vertical = position == ToolbarPosition.LEFT || position == ToolbarPosition.RIGHT

    Surface(
        modifier = modifier,
        // pilulka ako ostatné lišty čítačky
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        ToolbarContainer(
            vertical = vertical,
            // zatváracie tlačidlo je vždy viditeľné – zvyšok panela sa pri nedostatku miesta posúva
            pinned = {
                SmallIconButton(Icons.Outlined.Close, stringResource(R.string.close_annotations), onClick = onClose)
            },
        ) {
            ToolToggle(tool == InkTool.PEN, Icons.Outlined.Edit, stringResource(R.string.tool_pen)) { pick(InkTool.PEN) }
            ToolToggle(tool == InkTool.HIGHLIGHTER, Icons.Outlined.Highlight, stringResource(R.string.tool_highlighter), locked = !isPro) {
                pick(InkTool.HIGHLIGHTER)
            }
            ToolToggle(tool == InkTool.SHAPE, Icons.Outlined.Category, stringResource(R.string.tool_shapes), locked = !isPro) { pick(InkTool.SHAPE) }
            ToolToggle(tool == InkTool.STAMP, Icons.Outlined.Approval, stringResource(R.string.tool_stamp), locked = !isPro) { pick(InkTool.STAMP) }
            ToolToggle(tool == InkTool.LASSO, Icons.Outlined.HighlightAlt, stringResource(R.string.tool_lasso), locked = !isPro) {
                pick(InkTool.LASSO)
            }
            ToolToggle(tool == InkTool.ERASER, Icons.Outlined.AutoFixNormal, stringResource(R.string.tool_eraser)) {
                pick(InkTool.ERASER)
            }

            ToolSection(tool == InkTool.PEN || tool == InkTool.HIGHLIGHTER || tool == InkTool.SHAPE, vertical) {
                Separator(vertical)
                ColorButton(controller)
                WidthButton(controller)
            }

            ToolSection(tool == InkTool.STAMP, vertical) {
                Separator(vertical)
                StampButton(controller)
                ColorButton(controller)
                StampSizeButton(controller)
            }

            // vybraná pečiatka – zmazanie
            ToolSection(tool == InkTool.STAMP && controller.selectedStamp != null, vertical) {
                SmallIconButton(Icons.Outlined.Delete, stringResource(R.string.stamp_delete)) { controller.deleteSelectedStamp() }
            }

            ToolSection(tool == InkTool.ERASER, vertical) {
                Separator(vertical)
                EraserSizeButton(controller)
            }

            ToolSection(tool == InkTool.SHAPE, vertical) {
                Separator(vertical)
                ToolToggle(controller.shapeKind == ShapeKind.LINE, Icons.Outlined.HorizontalRule, stringResource(R.string.shape_line)) {
                    controller.shapeKind = ShapeKind.LINE
                }
                ToolToggle(controller.shapeKind == ShapeKind.RECT, Icons.Outlined.CropSquare, stringResource(R.string.shape_rect)) {
                    controller.shapeKind = ShapeKind.RECT
                }
                ToolToggle(controller.shapeKind == ShapeKind.ELLIPSE, Icons.Outlined.RadioButtonUnchecked, stringResource(R.string.shape_ellipse)) {
                    controller.shapeKind = ShapeKind.ELLIPSE
                }
            }

            ToolSection(tool == InkTool.LASSO && controller.selection.isNotEmpty(), vertical) {
                Separator(vertical)
                SmallIconButton(Icons.Outlined.ContentCopy, stringResource(R.string.selection_duplicate)) { controller.duplicateSelection() }
                SmallIconButton(Icons.Outlined.Delete, stringResource(R.string.selection_delete)) { controller.deleteSelection() }
            }

            Separator(vertical)
            SmallIconButton(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo), enabled = controller.canUndo) { controller.undo() }
            SmallIconButton(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo), enabled = controller.canRedo) { controller.redo() }
            MoreButton(
                fingerDrawing = fingerDrawing,
                onFingerDrawingChange = onFingerDrawingChange,
                position = position,
                onPositionChange = onPositionChange,
                onClearPage = { confirmClear = true },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_annotations_title)) },
            text = { Text(stringResource(R.string.clear_annotations_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pages.forEach { controller.clearPage(it) }
                    confirmClear = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Posúvateľný obsah panela + pevné tlačidlo na konci. */
@Composable
private fun ToolbarContainer(
    vertical: Boolean,
    pinned: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (vertical) {
        Column(
            modifier = Modifier
                .width(56.dp)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) { content() }
            Separator(true)
            pinned()
        }
    } else {
        Row(
            modifier = Modifier
                .height(56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) { content() }
            Separator(false)
            pinned()
        }
    }
}

/** Menšie ikonové tlačidlo (40 dp), aby sa panel zmestil aj na výšku. */
@Composable
private fun SmallIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** Menej používané voľby: kreslenie prstom, vymazanie strany, poloha panela. */
@Composable
private fun MoreButton(
    fingerDrawing: Boolean,
    onFingerDrawingChange: (Boolean) -> Unit,
    position: ToolbarPosition,
    onPositionChange: (ToolbarPosition) -> Unit,
    onClearPage: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SmallIconButton(Icons.Outlined.MoreHoriz, stringResource(R.string.more)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.draw_with_finger)) },
                leadingIcon = { Icon(Icons.Outlined.TouchApp, null) },
                trailingIcon = { com.daklok.crescendo.ui.common.ExpressiveSwitch(checked = fingerDrawing, onCheckedChange = null) },
                onClick = { onFingerDrawingChange(!fingerDrawing) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.clear_page_annotations)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                onClick = {
                    open = false
                    onClearPage()
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                stringResource(R.string.toolbar_position),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            ToolbarPosition.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(stringResource(p.labelRes)) },
                    leadingIcon = {
                        if (p == position) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        onPositionChange(p)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Separator(vertical: Boolean) {
    if (vertical) {
        HorizontalDivider(Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant)
    } else {
        VerticalDivider(Modifier.height(32.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ToolToggle(
    selected: Boolean,
    icon: ImageVector,
    description: String,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    // vybraný nástroj: z kruhu zaoblený štvorec (Material 3 Expressive)
    val corner by animateIntAsState(if (selected) 30 else 50, Motion.bouncy(), label = "toolToggleShape")
    // hviezdička Pro je mimo tlačidla (tlačidlo orezáva obsah na kruh), ale v rámci 40 dp
    Box(Modifier.size(40.dp)) {
        FilledIconToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(percent = corner),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = Color.Transparent,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
        }
        if (locked) {
            com.daklok.crescendo.ui.pro.ProDot(Modifier.align(Alignment.TopEnd), size = 14.dp)
        }
    }
}

/** Kruh s aktuálnou farbou; ťuk otvorí paletu s rýchlymi farbami, vlastnými farbami a kolieskom. */
@Composable
private fun ColorButton(controller: AnnotationController) {
    var open by remember { mutableStateOf(false) }
    var wheelOpen by remember { mutableStateOf(false) }
    val highlighter = controller.tool == InkTool.HIGHLIGHTER
    val colors = if (highlighter) AnnotationController.HIGHLIGHT_COLORS else AnnotationController.PEN_COLORS
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(Color(controller.currentColor), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
        ) {
            // všetko v jednom riadku: rýchle farby | vlastné farby | koliesko
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                colors.forEach { c ->
                    Swatch(Color(c), selected = controller.currentColor == c, size = 26) {
                        controller.setColor(c)
                        open = false
                    }
                }
                val recent = controller.recentColors.toList().take(4)
                if (recent.isNotEmpty()) {
                    VerticalDivider(
                        Modifier
                            .height(24.dp)
                            .padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    recent.forEach { c ->
                        Swatch(Color(c), selected = controller.currentColor == c, size = 26) {
                            controller.setColor(c)
                            open = false
                        }
                    }
                }
                IconButton(
                    onClick = {
                        open = false
                        wheelOpen = true
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = stringResource(R.string.custom_color))
                }
            }
        }
    }
    if (wheelOpen) {
        ColorPickerDialog(
            title = stringResource(if (highlighter) R.string.highlighter_color else R.string.pen_color),
            initial = controller.currentColor,
            presets = emptyList(),
            onPick = {
                controller.pickCustomColor(it)
                wheelOpen = false
            },
            onDismiss = { wheelOpen = false },
        )
    }
}

/** Náhľad pečiatky v štvorčeku [box]. */
@Composable
private fun StampPreview(text: String, color: Color, box: Int = 40, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(box.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Canvas(Modifier.size(box.dp)) {
            val len = if (text.startsWith("§")) 2 else text.length
            val s = (size.height * 0.5f).coerceAtMost(size.width / (0.62f * len.coerceAtLeast(1)))
            drawStampAt(text, color, size.width / 2f, size.height / 2f, s)
        }
    }
}

/** Aktuálna pečiatka; ťuk otvorí výber pečiatok, vlastný text a veľkosť. */
@Composable
private fun StampButton(controller: AnnotationController) {
    var open by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    val color = Color(controller.penColor)
    Box {
        StampPreview(controller.stampText, color, box = 40) { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val ink = MaterialTheme.colorScheme.onSurface
                StampCatalog.GROUPS.forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        group.forEach { t ->
                            StampPreview(t, ink, selected = controller.stampText == t) {
                                controller.stampText = t
                                open = false
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    controller.recentStampTexts.toList().take(4).forEach { t ->
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .background(
                                    if (controller.stampText == t) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    controller.stampText = t
                                    open = false
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    IconButton(onClick = {
                        open = false
                        customOpen = true
                    }) {
                        Icon(Icons.Outlined.TextFields, contentDescription = stringResource(R.string.stamp_custom))
                    }
                }
            }
        }
    }
    if (customOpen) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { customOpen = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.stamp_custom)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.stamp_custom_hint)) },
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        controller.useCustomStampText(text)
                        customOpen = false
                    },
                ) { Text(stringResource(R.string.choose)) }
            },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Veľkosť pečiatky – pre nové pečiatky aj pre práve vybranú. */
@Composable
private fun StampSizeButton(controller: AnnotationController) {
    var open by remember { mutableStateOf(false) }
    val range = AnnotationController.STAMP_RANGE
    fun fontFor(v: Float) = (10 + (v - range.start) / (range.endInclusive - range.start) * 16).sp
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.FormatSize, contentDescription = stringResource(R.string.stamp_size))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = {
                controller.commitStampResize()
                open = false
            },
            shape = RoundedCornerShape(20.dp),
        ) {
            SizeMenuContent(
                presets = AnnotationController.STAMP_SIZES,
                value = controller.stampSize,
                range = range,
                dot = { sz, _ -> Text("A", fontSize = fontFor(sz), fontWeight = FontWeight.Bold) },
                onPreset = {
                    controller.setStampSizeLive(it)
                    controller.commitStampResize()
                },
                onSlide = { controller.setStampSizeLive(it) },
                onSlideFinished = { controller.commitStampResize() },
            )
        }
    }
}

/** Mapuje hodnotu z rozsahu na priemer ukážky v dp. */
private fun previewDiameter(value: Float, range: ClosedFloatingPointRange<Float>, minDp: Float, maxDp: Float): Float {
    val t = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    return minDp + (maxDp - minDp) * t
}

/** Ukážka aktuálnej hrúbky; ťuk otvorí tri rýchle hrúbky a posuvník na presné nastavenie. */
@Composable
private fun WidthButton(controller: AnnotationController) {
    var open by remember { mutableStateOf(false) }
    val highlighter = controller.tool == InkTool.HIGHLIGHTER
    val widths = if (highlighter) AnnotationController.HIGHLIGHT_WIDTHS else AnnotationController.PEN_WIDTHS
    val range = controller.widthRange
    val (minDp, maxDp) = if (highlighter) 7f to 30f else 3f to 22f
    val color = Color(controller.currentColor)
    val current = controller.currentWidth
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(previewDiameter(current, range, minDp, maxDp).coerceAtMost(30f).dp).background(color, CircleShape))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
        ) {
            SizeMenuContent(
                presets = widths,
                value = current,
                range = range,
                dot = { w, mod ->
                    Box(mod.size(previewDiameter(w, range, minDp, maxDp).dp).background(color, CircleShape))
                },
                onPreset = {
                    controller.setWidth(it)
                    open = false
                },
                onSlide = { controller.setWidth(it) },
            )
        }
    }
}

/** Spoločný obsah ponuky veľkosti: rýchle voľby + posuvník + ukážka. */
@Composable
private fun SizeMenuContent(
    presets: FloatArray,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    dot: @Composable (Float, Modifier) -> Unit,
    onPreset: (Float) -> Unit,
    onSlide: (Float) -> Unit,
    onSlideFinished: () -> Unit = {},
) {
    // jeden riadok: tri rýchle veľkosti a posuvník
    Row(
        modifier = Modifier.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        presets.forEach { w ->
            val selected = abs(w - value) < 0.0002f
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        CircleShape,
                    )
                    .clip(CircleShape)
                    .clickable { onPreset(w) },
                contentAlignment = Alignment.Center,
            ) { dot(w, Modifier) }
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onSlide,
            onValueChangeFinished = onSlideFinished,
            valueRange = range,
            modifier = Modifier
                .width(170.dp)
                .padding(start = 6.dp, end = 4.dp),
        )
    }
}

@Composable
private fun EraserSizeButton(controller: AnnotationController) {
    var open by remember { mutableStateOf(false) }
    val range = AnnotationController.ERASER_RANGE
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            EraserDot(previewDiameter(controller.eraserSize, range, 8f, 34f).coerceAtMost(32f))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
        ) {
            SizeMenuContent(
                presets = AnnotationController.ERASER_SIZES,
                value = controller.eraserSize,
                range = range,
                dot = { sz, _ -> EraserDot(previewDiameter(sz, range, 8f, 34f).coerceAtMost(32f)) },
                onPreset = {
                    controller.eraserSize = it
                    open = false
                },
                onSlide = { controller.eraserSize = it },
            )
        }
    }
}

@Composable
private fun EraserDot(diameter: Float) {
    Box(
        Modifier
            .size(diameter.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
    )
}

/**
 * Skupina tlačidiel, ktorá sa podľa nástroja pružne rozbalí / zbalí,
 * takže panel mení veľkosť plynulo namiesto skoku.
 */
@Composable
private fun ToolSection(visible: Boolean, vertical: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, delayMillis = 60)) +
            if (vertical) expandVertically(Motion.bouncy(), expandFrom = Alignment.Top)
            else expandHorizontally(Motion.bouncy(), expandFrom = Alignment.Start),
        exit = fadeOut(tween(120)) +
            if (vertical) shrinkVertically(tween(220), shrinkTowards = Alignment.Top)
            else shrinkHorizontally(tween(220), shrinkTowards = Alignment.Start),
    ) {
        if (vertical) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { content() }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { content() }
        }
    }
}
