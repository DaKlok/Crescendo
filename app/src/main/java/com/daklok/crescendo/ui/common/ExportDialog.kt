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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.PieceNotes
import com.daklok.crescendo.pdf.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Čo sa má exportovať. [notes] načíta poznámky (z pamäte čítačky alebo zo súboru). */
class ExportRequest(
    val piece: PieceEntity,
    val pdf: File,
    val notes: suspend () -> PieceNotes,
) {
    val title: String get() = piece.title
}

/** Formát exportu. */
enum class ExportFormat(val ext: String, val mime: String) {
    /** PDF s poznámkami vpísanými do nôt – otvorí sa kdekoľvek, poznámky sa už nedajú upravovať. */
    PDF("pdf", "application/pdf"),
    /** Balík Crescenda – všetko: noty, upraviteľné poznámky, záložky, nahrávky, MIDI. */
    PACKAGE("crescendo", "application/zip"),
}

/**
 * Zdieľanie / export skladby: výber formátu (PDF alebo balík .crescendo), potom
 * „Uložiť do súboru“ / „Zdieľať“ a priebeh. Práca beží v rozsahu aplikácie, takže dobehne aj po zatvorení obrazovky.
 */
@Composable
fun ExportPdfDialog(request: ExportRequest, onDismiss: () -> Unit, initialFormat: ExportFormat = ExportFormat.PDF) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    var progress by remember { mutableStateOf<Float?>(null) }
    var format by remember { mutableStateOf(initialFormat) }

    fun finish(ok: Boolean, okMsg: Int) {
        Toast.makeText(app, if (ok) okMsg else R.string.export_failed, Toast.LENGTH_SHORT).show()
        progress = null
        onDismiss()
    }

    /** Zapíše vybraný formát do [out]. */
    suspend fun write(fmt: ExportFormat, out: java.io.OutputStream) {
        val notes = request.notes()
        when (fmt) {
            ExportFormat.PDF -> withContext(Dispatchers.IO) { PdfExporter.export(request.pdf, notes, out) { progress = it } }
            ExportFormat.PACKAGE -> {
                // poznámky z čítačky najprv uložiť, aby bol v balíku aktuálny stav
                app.annotations.save(request.piece.id, notes)
                withContext(Dispatchers.IO) {
                    val tmp = File(File(app.cacheDir, "exports").apply { mkdirs() }, "pkg_${System.nanoTime()}.tmp")
                    try {
                        app.library.exportPackage(request.piece, tmp)
                        tmp.inputStream().use { it.copyTo(out) }
                    } finally {
                        tmp.delete()
                    }
                }
            }
        }
    }

    fun saveTo(fmt: ExportFormat, uri: android.net.Uri?) {
        if (uri == null) {
            onDismiss()
            return
        }
        progress = 0f
        app.appScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri) }?.use { write(fmt, it) }
                    ?: error("no output")
            }.isSuccess
            finish(ok, if (fmt == ExportFormat.PDF) R.string.export_saved else R.string.export_saved_pkg)
        }
    }

    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        saveTo(ExportFormat.PDF, it)
    }
    val pkgSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        saveTo(ExportFormat.PACKAGE, it)
    }

    fun share() {
        val fmt = format
        progress = 0f
        app.appScope.launch {
            val ok = runCatching {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "exports").apply {
                        mkdirs()
                        listFiles()?.forEach { it.delete() }
                    }
                    File(dir, PdfExporter.safeFileName(request.title) + "." + fmt.ext)
                }
                FileOutputStream(file).use { write(fmt, it) }
                startShare(app, file, request.title, fmt)
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
                if (p == null) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ExportFormat.entries.forEachIndexed { i, f ->
                            SegmentedButton(
                                selected = format == f,
                                onClick = { format = f },
                                shape = SegmentedButtonDefaults.itemShape(i, ExportFormat.entries.size),
                            ) {
                                Text(stringResource(if (f == ExportFormat.PDF) R.string.export_fmt_pdf else R.string.export_fmt_pkg))
                            }
                        }
                    }
                }
                Text(
                    when {
                        p != null -> stringResource(if (format == ExportFormat.PDF) R.string.export_working else R.string.export_working_pkg)
                        format == ExportFormat.PDF -> stringResource(R.string.export_desc, request.title)
                        else -> stringResource(R.string.export_desc_pkg, request.title)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (p != null) {
                    if (format == ExportFormat.PACKAGE) {
                        // balenie nemá priebeh po stranách
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp))
                    } else {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            drawStopIndicator = {},
                        )
                    }
                } else {
                    // na mobile tlačidlá pod sebou, na tablete vedľa seba
                    val buttons: @Composable (Modifier) -> Unit = { m ->
                        Button(
                            onClick = {
                                val name = PdfExporter.safeFileName(request.title) + "." + format.ext
                                if (format == ExportFormat.PDF) pdfSaver.launch(name) else pkgSaver.launch(name)
                            },
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

private fun startShare(context: Context, file: File, title: String, format: ExportFormat) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(format.mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, title)
    if (format == ExportFormat.PACKAGE) send.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_piece_text, title))
    send
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(
        Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
