package com.daklok.crescendo.ui.reader

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
import androidx.compose.material.icons.outlined.OpenWith
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
    val vertical = position == ToolbarPosition.LEFT || position == ToolbarPosition.RIGHT

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        ToolbarContainer(vertical) {
            ToolToggle(tool == InkTool.PEN, Icons.Outlined.Edit, stringResource(R.string.tool_pen)) { controller.selectTool(InkTool.PEN) }
            ToolToggle(tool == InkTool.HIGHLIGHTER, Icons.Outlined.Highlight, stringResource(R.string.tool_highlighter)) {
                controller.selectTool(InkTool.HIGHLIGHTER)
            }
            ToolToggle(tool == InkTool.SHAPE, Icons.Outlined.Category, stringResource(R.string.tool_shapes)) { controller.selectTool(InkTool.SHAPE) }
            ToolToggle(tool == InkTool.LASSO, Icons.Outlined.HighlightAlt, stringResource(R.string.tool_lasso)) {
                controller.selectTool(InkTool.LASSO)
            }
            ToolToggle(tool == InkTool.ERASER, Icons.Outlined.AutoFixNormal, stringResource(R.string.tool_eraser)) {
                controller.selectTool(InkTool.ERASER)
            }

            ToolSection(tool == InkTool.PEN || tool == InkTool.HIGHLIGHTER || tool == InkTool.SHAPE, vertical) {
                Separator(vertical)
                ColorButton(controller)
                WidthButton(controller)
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
                IconButton(onClick = { controller.duplicateSelection() }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.selection_duplicate))
                }
                IconButton(onClick = { controller.deleteSelection() }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.selection_delete))
                }
            }

            Separator(vertical)
            IconButton(onClick = { controller.undo() }, enabled = controller.canUndo) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.undo))
            }
            IconButton(onClick = { controller.redo() }, enabled = controller.canRedo) {
                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.redo))
            }

            Separator(vertical)
            FilledIconToggleButton(
                checked = fingerDrawing,
                onCheckedChange = onFingerDrawingChange,
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = Color.Transparent,
                    checkedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Icon(Icons.Outlined.TouchApp, contentDescription = stringResource(R.string.draw_with_finger))
            }
            IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.clear_page_annotations))
            }
            PositionButton(position, onPositionChange)
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close_annotations))
            }
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

@Composable
private fun ToolbarContainer(vertical: Boolean, content: @Composable () -> Unit) {
    if (vertical) {
        Column(
            modifier = Modifier
                .width(64.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    } else {
        Row(
            modifier = Modifier
                .height(64.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
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
    onClick: () -> Unit,
) {
    FilledIconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = Color.Transparent,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** Kruh s aktuálnou farbou; ťuk otvorí paletu. */
@Composable
private fun ColorButton(controller: AnnotationController) {
    var open by remember { mutableStateOf(false) }
    val colors = if (controller.tool == InkTool.HIGHLIGHTER) AnnotationController.HIGHLIGHT_COLORS
    else AnnotationController.PEN_COLORS
    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                colors.forEach { c ->
                    ColorDot(Color(c), selected = controller.currentColor == c) {
                        controller.setColor(c)
                        open = false
                    }
                }
            }
        }
    }
}

/** Ukážka aktuálnej hrúbky; ťuk otvorí výber hrúbky. */
@Composable
private fun WidthButton(controller: AnnotationController) {
    var open by remember { mutableStateOf(false) }
    val highlighter = controller.tool == InkTool.HIGHLIGHTER
    val widths = if (highlighter) AnnotationController.HIGHLIGHT_WIDTHS else AnnotationController.PEN_WIDTHS
    val dots = if (highlighter) floatArrayOf(10f, 16f, 24f) else floatArrayOf(4f, 8f, 13f)
    val idx = widths.indexOfFirst { it == controller.currentWidth }.coerceAtLeast(0)
    val color = Color(controller.currentColor)
    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(dots[idx].dp).background(color, CircleShape))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                widths.forEachIndexed { i, w ->
                    WidthDot(dots[i], selected = i == idx, color = color) {
                        controller.setWidth(w)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionButton(position: ToolbarPosition, onChange: (ToolbarPosition) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.OpenWith, contentDescription = stringResource(R.string.toolbar_position))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ToolbarPosition.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(stringResource(p.labelRes)) },
                    leadingIcon = { if (p == position) Icon(Icons.Outlined.Check, null) },
                    onClick = {
                        onChange(p)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 32.dp else 28.dp)
                .background(color, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun WidthDot(diameter: Float, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(diameter.dp).background(color, CircleShape))
    }
}

/** Veľkosť gumy: ukážka aktuálneho kruhu, ťuk otvorí výber z troch veľkostí. */
@Composable
private fun EraserSizeButton(controller: AnnotationController) {
    var open by remember { mutableStateOf(false) }
    val sizes = AnnotationController.ERASER_SIZES
    val dots = floatArrayOf(10f, 18f, 28f)
    val idx = sizes.indexOfFirst { it == controller.eraserSize }.coerceAtLeast(0)
    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            EraserDot(dots[idx])
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sizes.forEachIndexed { i, sz ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (i == idx) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                CircleShape,
                            )
                            .clickable {
                                controller.eraserSize = sz
                                open = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        EraserDot(dots[i])
                    }
                }
            }
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
