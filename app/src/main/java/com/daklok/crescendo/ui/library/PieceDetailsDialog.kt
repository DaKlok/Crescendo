package com.daklok.crescendo.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PieceEntity

/** Úprava údajov skladby: názov, skladateľ, tónina, nástroj, obtiažnosť a štítky. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PieceDetailsDialog(
    piece: PieceEntity,
    allTags: List<String>,
    onSave: (PieceEntity) -> Unit,
    onDismiss: () -> Unit,
    /** Prečíta prvú stranu a vráti rozpoznaný názov a skladateľa. */
    onRecognize: (suspend () -> com.daklok.crescendo.data.TitleRecognizer.Result?)? = null,
) {
    var title by remember { mutableStateOf(piece.title) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    /** 0 = nič, 1 = číta sa, 2 = nič sa nenašlo. */
    var recognizing by remember { mutableIntStateOf(0) }
    var composer by remember { mutableStateOf(piece.composer) }
    var key by remember { mutableStateOf(piece.keySignature) }
    var instrument by remember { mutableStateOf(piece.instrument) }
    var difficulty by remember { mutableIntStateOf(piece.difficulty) }
    var tags by remember { mutableStateOf(piece.tagList) }
    var newTag by remember { mutableStateOf("") }

    fun addTag() {
        val t = newTag.trim().replace(",", " ")
        if (t.isNotEmpty() && t !in tags) tags = tags + t
        newTag = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.edit_details)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Field(stringResource(R.string.field_title), title) { title = it }
                Field(stringResource(R.string.field_composer), composer) { composer = it }
                if (onRecognize != null) {
                    // názov a skladateľ z prvej strany (ML Kit, v zariadení)
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            if (recognizing == 1) return@AssistChip
                            recognizing = 1
                            scope.launch {
                                val r = onRecognize()
                                if (r == null) {
                                    recognizing = 2
                                } else {
                                    r.title?.let { title = it }
                                    r.composer?.let { composer = it }
                                    recognizing = 0
                                }
                            }
                        },
                        label = {
                            Text(
                                when (recognizing) {
                                    1 -> stringResource(R.string.recognize_running)
                                    2 -> stringResource(R.string.recognize_nothing)
                                    else -> stringResource(R.string.recognize_from_page)
                                },
                            )
                        },
                        leadingIcon = {
                            if (recognizing == 1) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                            }
                        },
                        shape = RoundedCornerShape(50),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(stringResource(R.string.field_key), key, Modifier.weight(1f)) { key = it }
                    Field(stringResource(R.string.field_instrument), instrument, Modifier.weight(1f)) { instrument = it }
                }
                // obtiažnosť – hviezdičky (ťuk na tú istú hviezdičku ju zruší)
                Text(stringResource(R.string.field_difficulty), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { n ->
                        IconButton(onClick = { difficulty = if (difficulty == n) 0 else n }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                if (n <= difficulty) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                                contentDescription = null,
                                tint = if (n <= difficulty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                // štítky
                Text(stringResource(R.string.field_tags), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    (allTags + tags).distinct().forEach { t ->
                        FilterChip(
                            selected = t in tags,
                            onClick = { tags = if (t in tags) tags - t else tags + t },
                            label = { Text(t) },
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = {
                        if (it.endsWith(",")) {
                            newTag = it.dropLast(1)
                            addTag()
                        } else newTag = it.take(30)
                    },
                    placeholder = { Text(stringResource(R.string.tag_add_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { addTag() }, enabled = newTag.isNotBlank()) { Text(stringResource(R.string.tag_add)) }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    if (newTag.isNotBlank()) addTag()
                    onSave(
                        piece.copy(
                            title = title.trim(),
                            composer = composer.trim(),
                            keySignature = key.trim(),
                            instrument = instrument.trim(),
                            difficulty = difficulty,
                            tags = tags.joinToString(","),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(80)) },
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    )
}
