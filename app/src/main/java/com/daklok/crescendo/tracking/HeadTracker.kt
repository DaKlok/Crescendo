package com.daklok.crescendo.tracking

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.daklok.crescendo.data.AppSettings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class TrackingState(
    val faceDetected: Boolean = false,
    val yawDeg: Float = 0f,
    val holdProgress: Float = 0f,
    val cooldownRemaining: Float = 0f,
)

/**
 * Analyzátor pre CameraX: z každého snímku získa natočenie hlavy (ML Kit Face Detection,
 * beží celé lokálne na zariadení) a pošle ho do [HeadTurnDetector].
 */
class HeadTracker : ImageAnalysis.Analyzer {

    private val detector = HeadTurnDetector()

    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _turns = MutableSharedFlow<TurnDirection>(extraBufferCapacity = 4)
    val turns: SharedFlow<TurnDirection> = _turns.asSharedFlow()

    fun applySettings(s: AppSettings) {
        detector.thresholdDeg = s.yawThresholdDeg
        detector.holdMs = (s.holdTimeSec * 1000).toLong()
        detector.cooldownMs = (s.cooldownSec * 1000).toLong()
        detector.reverse = s.reverseDirection
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        faceDetector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val yaw = face?.headEulerAngleY
                val out = detector.update(yaw, SystemClock.elapsedRealtime())
                _state.value = TrackingState(
                    faceDetected = face != null,
                    yawDeg = yaw ?: 0f,
                    holdProgress = out.holdProgress,
                    cooldownRemaining = out.cooldownRemaining,
                )
                out.fired?.let { _turns.tryEmit(it) }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun reset() {
        detector.reset()
        _state.value = TrackingState()
    }

    fun close() {
        faceDetector.close()
    }
}
