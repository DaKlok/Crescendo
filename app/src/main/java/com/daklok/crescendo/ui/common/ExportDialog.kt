package com.daklok.crescendo.ui.common

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PieceNotes
import com.daklok.crescendo.pdf.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Čo sa má exportovať. [notes] načíta poznámky (z pamäte čítačky alebo zo súboru). */
class ExportRequest(
    val title: String,
    val pdf: File,
    val notes: suspend () -> PieceNotes,
)

/**
 * Export nôt s poznámkami do PDF: výber „Uložiť do súboru“ / „Zdieľať“,
 * potom priebeh. Práca beží v rozsahu aplikácie, takže dobehne aj po zatvorení obrazovky.
 */
@Composable
fun ExportPdfDialog(request: ExportRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    var progress by remember { mutableStateOf<Float?>(null) }

    fun finish(ok: Boolean, okMsg: Int) {
        Toast.makeText(app, if (ok) okMsg else R.string.export_failed, Toast.LENGTH_SHORT).show()
        progress = null
        onDismiss()
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        progress = 0f
        app.appScope.launch {
            val ok = runCatching {
                val notes = request.notes()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        PdfExporter.export(request.pdf, notes, out) { progress = it }
                    } ?: error("no output")
                }
            }.isSuccess
            finish(ok, R.string.export_saved)
        }
    }

    fun share() {
        progress = 0f
        app.appScope.launch {
            val ok = runCatching {
                val notes = request.notes()
                val file = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "exports").apply {
                        mkdirs()
                        listFiles()?.forEach { it.delete() }
                    }
                    val f = File(dir, PdfExporter.safeFileName(request.title) + ".pdf")
                    FileOutputStream(f).use { out -> PdfExporter.export(request.pdf, notes, out) { progress = it } }
                    f
                }
                startShare(app, file, request.title)
            }.isSuccess
            progress = null
            if (!ok) Toast.makeText(app, R.string.export_failed, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    val p = progress
    AlertDialog(
        onDismissRequest = { if (p == null) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = p == null, dismissOnBackPress = p == null),
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Outlined.IosShare, null) },
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (p == null) stringResource(R.string.export_desc, request.title)
                    else stringResource(R.string.export_working),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (p != null) {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        drawStopIndicator = {},
                    )
                } else {
                    // na mobile tlačidlá pod sebou, na tablete vedľa seba
                    val buttons: @Composable (Modifier) -> Unit = { m ->
                        Button(
                            onClick = { saver.launch(PdfExporter.safeFileName(request.title) + ".pdf") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = m,
                        ) {
                            Icon(Icons.Outlined.SaveAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.export_save))
                        }
                        FilledTonalButton(
                            onClick = { share() },
                            shape = RoundedCornerShape(16.dp),
                            modifier = m,
                        ) {
                            Icon(Icons.Outlined.IosShare, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.export_share))
                        }
                    }
                    if (isCompactWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons(Modifier.fillMaxWidth()) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            buttons(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (p == null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun startShare(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(
        Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
