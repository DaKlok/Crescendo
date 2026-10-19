package com.daklok.crescendo.ui.settings

import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.StorageEntry
import com.daklok.crescendo.data.StorageGroup
import com.daklok.crescendo.data.StorageKind
import com.daklok.crescendo.data.StorageManager
import com.daklok.crescendo.ui.common.SettingsCard
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Čitateľná veľkosť („12 MB“). */
@Composable
internal fun formatBytes(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

private fun kindIcon(k: StorageKind): ImageVector = when (k) {
    StorageKind.SCORES -> Icons.Outlined.Description
    StorageKind.REFERENCES -> Icons.Outlined.Headphones
    StorageKind.RECORDINGS -> Icons.Outlined.Mic
    StorageKind.MIDI -> Icons.Outlined.Piano
    StorageKind.RENDERS -> Icons.Outlined.GraphicEq
    StorageKind.INSTRUMENTS -> Icons.Outlined.MusicNote
    StorageKind.CACHE -> Icons.Outlined.CleaningServices
    StorageKind.OTHER -> Icons.Outlined.Settings
}

private fun kindTitle(k: StorageKind): Int = when (k) {
    StorageKind.SCORES -> R.string.storage_scores
    StorageKind.REFERENCES -> R.string.storage_references
    StorageKind.RECORDINGS -> R.string.storage_recordings
    StorageKind.MIDI -> R.string.storage_midi
    StorageKind.RENDERS -> R.string.storage_renders
    StorageKind.INSTRUMENTS -> R.string.storage_instruments
    StorageKind.CACHE -> R.string.storage_cache
    StorageKind.OTHER -> R.string.storage_other
}

private fun kindDesc(k: StorageKind): Int = when (k) {
    StorageKind.SCORES -> R.string.storage_scores_desc
    StorageKind.REFERENCES -> R.string.storage_references_desc
    StorageKind.RECORDINGS -> R.string.storage_recordings_desc
    StorageKind.MIDI -> R.string.storage_midi_desc
    StorageKind.RENDERS -> R.string.storage_renders_desc
    StorageKind.INSTRUMENTS -> R.string.storage_instruments_desc
    StorageKind.CACHE -> R.string.storage_cache_desc
    StorageKind.OTHER -> R.string.storage_other_desc
}

/** Dá sa položka po zmazaní znova získať (vyrobiť / stiahnuť)? */
internal fun restorable(e: StorageEntry): Boolean = when (e.kind) {
    StorageKind.RENDERS, StorageKind.INSTRUMENTS, StorageKind.CACHE -> true
    StorageKind.REFERENCES -> e.label == "official"
    StorageKind.MIDI -> e.label == "catalog"
    else -> false
}

/** Skupiny, ktoré sa dajú bez obáv zmazať celé (vyrobia / stiahnu sa znova). */
private fun regenerable(k: StorageKind) = k == StorageKind.RENDERS || k == StorageKind.CACHE || k == StorageKind.INSTRUMENTS

@Composable
private fun kindColor(k: StorageKind): Color {
    val s = MaterialTheme.colorScheme
    return when (k) {
        StorageKind.SCORES -> s.primary
        StorageKind.REFERENCES -> s.tertiary
        StorageKind.RECORDINGS -> s.error
        StorageKind.MIDI -> s.secondary
        StorageKind.RENDERS -> s.primary.copy(alpha = 0.55f)
        StorageKind.INSTRUMENTS -> s.tertiary.copy(alpha = 0.55f)
        StorageKind.CACHE -> s.outline
        StorageKind.OTHER -> s.outlineVariant
    }
}

/**
 * Správca úložiska: celková veľkosť, rozdelenie podľa typu a pri každom type zoznam súborov
 * (podľa skladby) – dá sa zmazať presne to, čo netreba.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun StorageSettingsContent() {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val manager = remember { StorageManager(app) }
    val scope = rememberCoroutineScope()
    val pieces by remember { app.library.observePieces() }.collectAsStateWithLifecycle(emptyList())
    val titles = remember(pieces) { pieces.associate { it.id to it.title } }
    var groups by remember { mutableStateOf<List<StorageGroup>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) { groups = manager.scan() }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var expanded by remember { mutableStateOf<StorageKind?>(null) }

    val g = groups
    if (g == null) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val total = g.sumOf { it.bytes }.coerceAtLeast(1)
    val scheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── celkom ──
        SettingsCard {
            Text(stringResource(R.string.storage_total), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Text(formatBytes(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val colors = g.map { kindColor(it.kind) }
            val track = scheme.surfaceContainerHighest
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                var x = 0f
                g.forEachIndexed { i, grp ->
                    val w = size.width * grp.bytes / total
                    if (w > 0.5f) {
                        drawRoundRect(colors[i], Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2))
                        x += w
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                g.filter { it.bytes > 0 }.forEach { grp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(kindColor(grp.kind), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(kindTitle(grp.kind)), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            RestoreChip(true)
            Text(stringResource(R.string.storage_legend_restorable), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            RestoreChip(false)
            Text(stringResource(R.string.storage_legend_permanent), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            Text(
                stringResource(R.string.storage_sync_note),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // ── jednotlivé typy ──
        g.filter { it.entries.isNotEmpty() }.forEach { grp ->
            val open = expanded == grp.kind
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = scheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().animateContentSize(),
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = if (open) null else grp.kind }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).background(kindColor(grp.kind).copy(alpha = 0.18f), com.daklok.crescendo.ui.common.ExpressiveShapes.Cookie),
                            contentAlignment = Alignment.Center,
                        ) { Icon(kindIcon(grp.kind), null, Modifier.size(20.dp), tint = kindColor(grp.kind).copy(alpha = 1f)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(kindTitle(grp.kind)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.storage_items, grp.entries.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Text(formatBytes(grp.bytes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.padding(start = 4.dp))
                    }
                    if (open) {
                        Column(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 12.dp)) {
                            Text(
                                stringResource(kindDesc(grp.kind)),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp, bottom = 6.dp),
                            )
                            if (grp.kind.deletable && regenerable(grp.kind)) {
                                val title = stringResource(R.string.storage_delete_all_title, stringResource(kindTitle(grp.kind))) +
                                    "\n\n" + stringResource(R.string.storage_confirm_restorable)
                                FilledTonalButton(
                                    onClick = {
                                        confirm = title to suspend {
                                            manager.deleteAll(grp)
                                            version++
                                            Unit
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 6.dp),
                                ) {
                                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.storage_delete_all, formatBytes(grp.bytes)))
                                }
                            }
                            var shown by remember(grp.kind, version) { mutableIntStateOf(30) }
                            grp.entries.take(shown).forEach { e ->
                                val what = entryTitle(e, titles) + "\n\n" + stringResource(
                                    if (restorable(e)) R.string.storage_confirm_restorable else R.string.storage_confirm_permanent,
                                )
                                EntryRow(
                                    entry = e,
                                    title = what,
                                    subtitle = entrySubtitle(e, titles),
                                    onDelete = if (grp.kind.deletable) {
                                        {
                                            confirm = what to suspend {
                                                manager.delete(e)
                                                version++
                                                Unit
                                            }
                                        }
                                    } else null,
                                )
                            }
                            if (grp.entries.size > shown) {
                                TextButton(onClick = { shown += 50 }) {
                                    Text(stringResource(R.string.storage_show_more, grp.entries.size - shown))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { (what, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            shape = RoundedCornerShape(28.dp),
            icon = { com.daklok.crescendo.ui.common.DialogIcon(Icons.Outlined.Delete, destructive = true) },
            title = { Text(stringResource(R.string.storage_delete_title)) },
            text = { Text(what) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch { action() }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Názov položky: skladba (ak patrí k nejakej), inak popis. */
@Composable
private fun entryTitle(e: StorageEntry, titles: Map<Long, String>): String {
    val piece = e.pieceId?.let { titles[it] }
    return when (e.kind) {
        StorageKind.INSTRUMENTS -> e.label.replace('-', ' ').replaceFirstChar { it.uppercase() }
        StorageKind.OTHER -> when (e.label) {
            "database" -> stringResource(R.string.storage_database)
            else -> stringResource(R.string.storage_settings_files)
        }
        StorageKind.SCORES -> piece ?: when (e.label) {
            "thumbs" -> stringResource(R.string.storage_thumbs)
            "annotations" -> stringResource(R.string.annotations)
            else -> e.label
        }
        StorageKind.CACHE -> e.label
        else -> piece ?: if (e.label == "preview") stringResource(R.string.storage_previews) else stringResource(R.string.storage_unknown_piece)
    }
}

@Composable
private fun entrySubtitle(e: StorageEntry, titles: Map<Long, String>): String {
    val date = remember(e.modified) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.modified)) }
    val what = when (e.kind) {
        StorageKind.REFERENCES -> if (e.label == "official") stringResource(R.string.reference_official) else e.label
        StorageKind.RECORDINGS -> stringResource(if (e.file.extension.equals("mid", true)) R.string.rec_source_piano else R.string.rec_source_mic)
        StorageKind.MIDI -> if (e.label == "catalog") stringResource(R.string.vis_source_catalog) else e.label
        StorageKind.RENDERS -> if (e.label == "preview") stringResource(R.string.storage_previews) else e.label
        else -> ""
    }
    return listOf(what, date).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun EntryRow(entry: StorageEntry, title: String, subtitle: String, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onDelete != null) RestoreChip(restorable(entry), Modifier.padding(vertical = 2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatBytes(entry.bytes), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Štítok: „Dá sa obnoviť“ (zelený) alebo „Natrvalo“ (červený). */
@Composable
private fun RestoreChip(restorable: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (restorable) scheme.primaryContainer else scheme.errorContainer
    val fg = if (restorable) scheme.onPrimaryContainer else scheme.onErrorContainer
    Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg, modifier = modifier) {
        Text(
            stringResource(if (restorable) R.string.storage_chip_restorable else R.string.storage_chip_permanent),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
