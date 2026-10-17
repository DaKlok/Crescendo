package com.daklok.crescendo.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.pdf.PdfDocument
import java.io.File

/** Jedna strana v editore: pôvodný index, otočenie a či je označená na zmazanie. */
private class EditPage(val index: Int, rotation: Int = 0, removed: Boolean = false) {
    var rotation by mutableStateOf(rotation)
    var removed by mutableStateOf(removed)
}

/**
 * Úprava strán skladby: otočenie, zmazanie a zmena poradia (šípkami).
 * [onSave] dostane nové poradie ako (pôvodný index, otočenie).
 */
@Composable
fun PageEditorDialog(
    piece: PieceEntity,
    pdf: File,
    onSave: (List<Pair<Int, Int>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val document = remember(pdf) { runCatching { PdfDocument(pdf) }.getOrNull() }
    DisposableEffect(document) { onDispose { document?.close() } }
    val pages = remember(document) {
        mutableStateListOf<EditPage>().apply { repeat(document?.pageCount ?: 0) { add(EditPage(it)) } }
    }
    val remaining = pages.count { !it.removed }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) } },
                    title = {
                        Column {
                            Text(stringResource(R.string.edit_pages), fontWeight = FontWeight.Bold)
                            Text(piece.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        Button(
                            onClick = { onSave(pages.filter { !it.removed }.map { it.index to it.rotation }) },
                            enabled = remaining > 0,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.padding(end = 12.dp),
                        ) { Text(stringResource(R.string.save)) }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Text(
                    stringResource(R.string.edit_pages_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                if (document == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(pages, key = { _, p -> p.index }) { i, p ->
                        PageTile(
                            document = document,
                            page = p,
                            position = i,
                            canLeft = i > 0,
                            canRight = i < pages.lastIndex,
                            onLeft = { pages.add(i - 1, pages.removeAt(i)) },
                            onRight = { pages.add(i + 1, pages.removeAt(i)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTile(
    document: PdfDocument,
    page: EditPage,
    position: Int,
    canLeft: Boolean,
    canRight: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(null, document, page.index) {
        value = runCatching { document.render(page.index, 300).asImageBitmap() }.getOrNull()
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .alpha(if (page.removed) 0.3f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    Image(
                        it,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(page.rotation.toFloat())
                            .background(Color.White),
                    )
                } ?: CircularProgressIndicator(Modifier.size(28.dp))
                Text(
                    "${position + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onLeft, enabled = canLeft, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.move_up), Modifier.size(20.dp))
                }
                IconButton(onClick = { page.rotation = (page.rotation + 90) % 360 }, enabled = !page.removed, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Rotate90DegreesCw, stringResource(R.string.rotate), Modifier.size(20.dp))
                }
                IconButton(onClick = { page.removed = !page.removed }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (page.removed) Icons.Outlined.RestoreFromTrash else Icons.Outlined.Delete,
                        stringResource(R.string.delete),
                        Modifier.size(20.dp),
                        tint = if (page.removed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = onRight, enabled = canRight, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.move_down), Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Jedna časť rozdelenia (názov, od, do – strany od 1). */
private class SplitPart(name: String, from: String, to: String) {
    var name by mutableStateOf(name)
    var from by mutableStateOf(from)
    var to by mutableStateOf(to)
}

/**
 * Rozdelenie spevníka na samostatné skladby. [onSplit] dostane časti (názov, od, do – od 0)
 * a či ponechať pôvodnú skladbu.
 */
@Composable
fun SplitDialog(
    piece: PieceEntity,
    onSplit: (List<Triple<String, Int, Int>>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val n = piece.pageCount.coerceAtLeast(1)
    val parts = remember {
        mutableStateListOf(
            SplitPart("${piece.title} 1", "1", "${(n / 2).coerceAtLeast(1)}"),
            SplitPart("${piece.title} 2", "${(n / 2 + 1).coerceAtMost(n)}", "$n"),
        )
    }
    var keep by remember { mutableStateOf(true) }
    fun parsed(): List<Triple<String, Int, Int>>? = parts.map { p ->
        val f = p.from.toIntOrNull() ?: return null
        val t = p.to.toIntOrNull() ?: return null
        if (f < 1 || t > n || f > t) return null
        Triple(p.name.trim(), f - 1, t - 1)
    }
    val valid = parsed()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.split_piece)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.split_desc, n),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(parts) { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = p.name,
                                onValueChange = { p.name = it.take(80) },
                                singleLine = true,
                                label = { Text(stringResource(R.string.field_title)) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            )
                            NumberField(p.from, stringResource(R.string.split_from)) { p.from = it }
                            NumberField(p.to, stringResource(R.string.split_to)) { p.to = it }
                            IconButton(onClick = { if (parts.size > 1) parts.removeAt(i) }, enabled = parts.size > 1) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.delete))
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    val lastTo = parts.lastOrNull()?.to?.toIntOrNull() ?: 0
                    val start = (lastTo + 1).coerceAtMost(n)
                    parts.add(SplitPart("${piece.title} ${parts.size + 1}", "$start", "$n"))
                }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.split_add_part))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text(stringResource(R.string.split_keep_original), style = MaterialTheme.typography.bodyMedium)
                }
                if (valid == null) {
                    Text(stringResource(R.string.split_invalid, n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid != null, onClick = { valid?.let { onSplit(it, keep) } }) { Text(stringResource(R.string.split_do)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NumberField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(76.dp),
    )
}
