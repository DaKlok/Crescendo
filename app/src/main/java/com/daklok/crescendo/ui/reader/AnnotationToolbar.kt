package com.daklok.crescendo.ui.reader

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TouchApp
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
            ToolToggle(tool == InkTool.PEN, Icons.Rounded.Edit, "Pero") { controller.selectTool(InkTool.PEN) }
            ToolToggle(tool == InkTool.HIGHLIGHTER, Icons.Rounded.Highlight, "Zvýrazňovač") {
                controller.selectTool(InkTool.HIGHLIGHTER)
            }
            ToolToggle(tool == InkTool.SHAPE, Icons.Rounded.Category, "Tvary") { controller.selectTool(InkTool.SHAPE) }
            ToolToggle(tool == InkTool.LASSO, Icons.Rounded.HighlightAlt, "Výber (lasso)") {
                controller.selectTool(InkTool.LASSO)
            }
            ToolToggle(tool == InkTool.ERASER, Icons.Rounded.AutoFixNormal, "Guma") {
                controller.selectTool(InkTool.ERASER)
            }

            if (tool == InkTool.PEN || tool == InkTool.HIGHLIGHTER || tool == InkTool.SHAPE) {
                Separator(vertical)
                ColorButton(controller)
                WidthButton(controller)
            }

            if (tool == InkTool.SHAPE) {
                Separator(vertical)
                ToolToggle(controller.shapeKind == ShapeKind.LINE, Icons.Rounded.HorizontalRule, "Čiara") {
                    controller.shapeKind = ShapeKind.LINE
                }
                ToolToggle(controller.shapeKind == ShapeKind.RECT, Icons.Rounded.CropSquare, "Obdĺžnik") {
                    controller.shapeKind = ShapeKind.RECT
                }
                ToolToggle(controller.shapeKind == ShapeKind.ELLIPSE, Icons.Rounded.RadioButtonUnchecked, "Elipsa") {
                    controller.shapeKind = ShapeKind.ELLIPSE
                }
            }

            if (tool == InkTool.LASSO && controller.selection.isNotEmpty()) {
                Separator(vertical)
                IconButton(onClick = { controller.duplicateSelection() }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplikovať výber")
                }
                IconButton(onClick = { controller.deleteSelection() }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Zmazať výber")
                }
            }

            Separator(vertical)
            IconButton(onClick = { controller.undo() }, enabled = controller.canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Späť")
            }
            IconButton(onClick = { controller.redo() }, enabled = controller.canRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Vpred")
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
                Icon(Icons.Rounded.TouchApp, contentDescription = "Kresliť prstom")
            }
            IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Zmazať poznámky na strane")
            }
            PositionButton(position, onPositionChange)
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Zavrieť poznámky")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Zmazať poznámky?") },
            text = { Text("Všetky poznámky na zobrazenej strane sa zmažú. Dá sa to vrátiť tlačidlom Späť.") },
            confirmButton = {
                TextButton(onClick = {
                    pages.forEach { controller.clearPage(it) }
                    confirmClear = false
                }) { Text("Zmazať") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Zrušiť") } },
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
            Icon(Icons.Rounded.OpenWith, contentDescription = "Poloha panela")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ToolbarPosition.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label) },
                    leadingIcon = { if (p == position) Icon(Icons.Rounded.Check, null) },
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
