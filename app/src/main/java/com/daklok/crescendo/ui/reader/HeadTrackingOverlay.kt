package com.daklok.crescendo.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.Icons
import com.daklok.crescendo.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.data.AppSettings
import com.daklok.crescendo.data.CameraChoice
import com.daklok.crescendo.tracking.HeadTracker
import com.daklok.crescendo.tracking.TurnDirection
import com.daklok.crescendo.data.GestureKind
import com.daklok.crescendo.data.usesKind
import java.util.concurrent.Executors

fun hasCameraPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Spustí kameru + sledovanie hlavy. Keď sa rozpozná otočenie, zavolá [onTurn].
 * Náhľad kamery sa zobrazuje v malom okne s ukazovateľom natočenia.
 */
@Composable
fun HeadTrackingOverlay(
    settings: AppSettings,
    onTurn: (TurnDirection) -> Unit,
    /** Uloží aktuálnu polohu hlavy ako „rovno“ (yaw, roll, pitch v stupňoch). */
    onCalibrate: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    /** false = sledovanie beží ďalej, ale nič sa nezobrazuje (napr. v režime celej obrazovky). */
    visible: Boolean = true,
) {
    val showPreview = visible && settings.showCameraPreview
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tracker = remember { HeadTracker() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }

    LaunchedEffect(settings) { tracker.applySettings(settings) }

    LaunchedEffect(tracker) {
        tracker.turns.collect { onTurn(it) }
    }

    DisposableEffect(settings.camera, showPreview, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, tracker) }
            val selector = if (settings.camera == CameraChoice.FRONT)
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            runCatching {
                p.unbindAll()
                if (showPreview) {
                    // Preview sa viaže len keď je PreviewView naozaj zobrazený; inak by kamera nedodávala snímky.
                    val preview = Preview.Builder().build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                } else {
                    p.bindToLifecycle(lifecycleOwner, selector, analysis)
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            tracker.reset()
            runCatching { provider?.unbindAll() }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tracker.close()
            executor.shutdown()
        }
    }

    val state by tracker.state.collectAsStateWithLifecycle()

    if (!visible) return

    Surface(
        modifier = modifier.width(232.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (settings.showCameraPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            // zrkadlový náhľad – ako pri selfie
                            .scale(scaleX = if (settings.camera == CameraChoice.FRONT) -1f else 1f, scaleY = 1f),
                    )
                }
            }
            // ukazovateľ hlavného zapnutého gesta (po kalibrácii)
            when {
                settings.gestureMap.usesKind(GestureKind.TURN) -> AngleBar(state.yawDeg - settings.yawOffset, settings.yawThresholdDeg, state.faceDetected)
                settings.gestureMap.usesKind(GestureKind.TILT) -> AngleBar(-(state.rollDeg - settings.rollOffset), settings.tiltThresholdDeg, state.faceDetected)
                settings.gestureMap.usesKind(GestureKind.NOD) -> AngleBar(state.pitchDeg - settings.pitchOffset, settings.nodThresholdDeg, state.faceDetected)
            }
            if (settings.gestureMap.usesKind(GestureKind.WINK)) {
                EyeIndicator(
                    left = if (settings.swapEyes) state.rightEyeOpen else state.leftEyeOpen,
                    right = if (settings.swapEyes) state.leftEyeOpen else state.rightEyeOpen,
                    faceDetected = state.faceDetected,
                )
            }
            LinearProgressIndicator(
                progress = { state.holdProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
            )
            Text(
                when {
                    !state.faceDetected -> stringResource(R.string.tracking_no_face)
                    state.cooldownRemaining > 0f -> stringResource(R.string.tracking_pause)
                    else -> stringResource(R.string.tracking_ready)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (settings.gestureMap.usesKind(GestureKind.TURN) || settings.gestureMap.usesKind(GestureKind.TILT) || settings.gestureMap.usesKind(GestureKind.NOD)) {
                val haptic = com.daklok.crescendo.ui.common.rememberHaptic()
                TextButton(
                    onClick = {
                        haptic(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onCalibrate(state.yawDeg, state.rollDeg, state.pitchDeg)
                    },
                    enabled = state.faceDetected,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Outlined.CenterFocusStrong, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.calibrate), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Vodorovný pás: stred = rovno, značky = prah spustenia, bodka = aktuálne natočenie. */
@Composable
private fun AngleBar(yaw: Float, threshold: Float, faceDetected: Boolean) {
    val range = (threshold * 1.8f).coerceAtLeast(10f)
    val pos = ((yaw / range).coerceIn(-1f, 1f) + 1f) / 2f
    val thrPos = (threshold / range).coerceIn(0f, 1f) / 2f
    val dot = MaterialTheme.colorScheme.primary
    val mark = MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val h = size.height
        drawRoundRect(
            color = track,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2),
        )
        listOf(0.5f - thrPos, 0.5f + thrPos).forEach { f ->
            drawRect(
                color = mark,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * f - 1.5f, 2f),
                size = androidx.compose.ui.geometry.Size(3f, h - 4f),
            )
        }
        if (faceDetected) {
            drawCircle(
                color = dot,
                radius = h / 2 - 1f,
                center = androidx.compose.ui.geometry.Offset(size.width * pos, h / 2),
            )
        }
    }
}

/** Dve bodky = oči; plná = otvorené, prázdna = zavreté. Ľavá bodka = ľavé oko človeka. */
@Composable
private fun EyeIndicator(left: Float?, right: Float?, faceDetected: Boolean) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(left, right).forEach { p ->
            val open = faceDetected && (p ?: 1f) > 0.5f
            androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                if (open) drawCircle(on)
                else drawCircle(off, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            }
        }
    }
}
