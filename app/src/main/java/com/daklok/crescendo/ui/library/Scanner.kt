package com.daklok.crescendo.ui.library

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Skenovanie nôt fotoaparátom cez Google skener dokumentov (ML Kit):
 * sám nájde okraje papiera, vyrovná perspektívu, oreže, umožní viac strán a vráti hotové PDF.
 * Všetko beží v zariadení. Vráti funkciu, ktorá skener spustí.
 */
@Composable
fun rememberDocumentScanner(
    onScanned: (pdf: Uri, pages: Int) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val pdf = scan?.pdf
        if (pdf != null) onScanned(pdf.uri, pdf.pageCount) else onError()
    }
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(60)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }
    return remember(context) {
        {
            val activity = context.findActivity()
            if (activity == null) {
                onError()
            } else {
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                    .addOnFailureListener { onError() }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
