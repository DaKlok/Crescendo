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
    /** Surové uhly (bez kalibrácie) – na kalibráciu a indikátor. */
    val yawDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val leftEyeOpen: Float? = null,
    val rightEyeOpen: Float? = null,
    val holdProgress: Float = 0f,
    val cooldownRemaining: Float = 0f,
    val activeGesture: com.daklok.crescendo.data.GestureTrigger? = null,
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
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // oči + úsmev
            .build()
    )

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _turns = MutableSharedFlow<TurnDirection>(extraBufferCapacity = 4)
    val turns: SharedFlow<TurnDirection> = _turns.asSharedFlow()

    fun applySettings(s: AppSettings) {
        detector.actions = s.gestureMap
        detector.turnThresholdDeg = s.yawThresholdDeg
        detector.tiltThresholdDeg = s.tiltThresholdDeg
        detector.nodThresholdDeg = s.nodThresholdDeg
        detector.winkHoldMs = (s.winkHoldSec * 1000).toLong()
        detector.swapEyes = s.swapEyes
        detector.yawOffset = s.yawOffset
        detector.rollOffset = s.rollOffset
        detector.pitchOffset = s.pitchOffset
        detector.holdMs = (s.holdTimeSec * 1000).toLong()
        detector.cooldownMs = (s.cooldownSec * 1000).toLong()
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
                val sample = face?.let {
                    FaceSample(
                        yaw = it.headEulerAngleY,
                        roll = it.headEulerAngleZ,
                        pitch = it.headEulerAngleX,
                        leftEyeOpen = it.leftEyeOpenProbability,
                        rightEyeOpen = it.rightEyeOpenProbability,
                        smile = it.smilingProbability,
                    )
                }
                val out = detector.update(sample, SystemClock.elapsedRealtime())
                _state.value = TrackingState(
                    faceDetected = face != null,
                    yawDeg = sample?.yaw ?: 0f,
                    rollDeg = sample?.roll ?: 0f,
                    pitchDeg = sample?.pitch ?: 0f,
                    leftEyeOpen = sample?.leftEyeOpen,
                    rightEyeOpen = sample?.rightEyeOpen,
                    holdProgress = out.holdProgress,
                    cooldownRemaining = out.cooldownRemaining,
                    activeGesture = out.activeGesture,
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
