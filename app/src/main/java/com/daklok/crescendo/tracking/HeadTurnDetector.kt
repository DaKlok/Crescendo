package com.daklok.crescendo.tracking

import com.daklok.crescendo.data.GestureKind
import com.daklok.crescendo.data.GestureMap
import com.daklok.crescendo.data.GestureTrigger
import com.daklok.crescendo.data.PageAction
import com.daklok.crescendo.data.usesKind
import kotlin.math.abs

enum class TurnDirection { NEXT, PREVIOUS }

/** Jeden snímok z ML Kit – uhly hlavy (stupne) a pravdepodobnosti očí / úsmevu (0..1, null = neznáme). */
data class FaceSample(
    val yaw: Float,
    val roll: Float,
    val pitch: Float,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    val smile: Float?,
)

/** Výsledok jedného kroku detektora – slúži aj na vykreslenie indikátora v UI. */
data class DetectorOutput(
    val fired: TurnDirection? = null,
    /** 0..1 – ako dlho už gesto trvá vzhľadom na potrebný čas. */
    val holdProgress: Float = 0f,
    /** 0..1 – 1 = práve otočené, 0 = pauza (cooldown) uplynula. */
    val cooldownRemaining: Float = 0f,
    val activeGesture: GestureTrigger? = null,
)

/**
 * Rozhoduje, kedy otočiť stranu. Používateľ si sám priradí, ktoré gesto otočí ďalej a ktoré späť
 * ([actions]); gestá bez priradenia sa ignorujú.
 *
 * Hysteréza: po otočení sa musí tvár vrátiť do pokoja, kým sa detektor znova „odistí“.
 */
class HeadTurnDetector {
    var actions: Map<GestureTrigger, PageAction> = GestureMap.DEFAULT

    var turnThresholdDeg = 22f
    var tiltThresholdDeg = 16f
    var nodThresholdDeg = 14f
    var winkHoldMs = 250L
    var swapEyes = false

    var holdMs: Long = 400
    var cooldownMs: Long = 1200

    var yawOffset = 0f
    var rollOffset = 0f
    var pitchOffset = 0f

    private var candidate: GestureTrigger? = null
    private var candidateSince = 0L
    private var armed = true
    private var lastFire = Long.MIN_VALUE / 2

    fun reset() {
        candidate = null
        armed = true
    }

    fun update(sample: FaceSample?, nowMs: Long): DetectorOutput {
        val cooldownLeft = (1f - (nowMs - lastFire).toFloat() / cooldownMs.coerceAtLeast(1)).coerceIn(0f, 1f)

        if (sample == null) {
            candidate = null
            return DetectorOutput(cooldownRemaining = cooldownLeft)
        }

        val yaw = sample.yaw - yawOffset
        val roll = sample.roll - rollOffset
        val pitch = sample.pitch - pitchOffset
        // oči z pohľadu človeka; [swapEyes] pre zariadenia, ktoré ich vracajú naopak
        val right = if (swapEyes) sample.leftEyeOpen else sample.rightEyeOpen
        val left = if (swapEyes) sample.rightEyeOpen else sample.leftEyeOpen
        val smile = sample.smile ?: 0f

        // ---- pokoj = všetky používané druhy gest sú v neutrále ----
        val calm =
            (!actions.usesKind(GestureKind.TURN) || abs(yaw) < turnThresholdDeg * 0.6f) &&
                (!actions.usesKind(GestureKind.TILT) || abs(roll) < tiltThresholdDeg * 0.6f) &&
                (!actions.usesKind(GestureKind.NOD) || abs(pitch) < nodThresholdDeg * 0.6f) &&
                (!actions.usesKind(GestureKind.WINK) || winkSide(left, right) == null) &&
                (!actions.usesKind(GestureKind.SMILE) || smile < 0.4f)
        if (calm) armed = true

        // ---- všetky gestá, ktoré tvár práve robí (v poradí priority) ----
        val wink = winkSide(left, right)
        val present = buildList {
            if (wink != null) add(wink)
            // ML Kit: kladný Euler Z = hlava naklonená k pravému ramenu človeka
            if (roll >= tiltThresholdDeg) add(GestureTrigger.TILT_RIGHT)
            if (roll <= -tiltThresholdDeg) add(GestureTrigger.TILT_LEFT)
            // ML Kit: záporný Euler Y = človek otočil hlavu doprava
            if (yaw <= -turnThresholdDeg) add(GestureTrigger.TURN_RIGHT)
            if (yaw >= turnThresholdDeg) add(GestureTrigger.TURN_LEFT)
            // ML Kit: záporný Euler X = pohľad dole
            if (pitch <= -nodThresholdDeg) add(GestureTrigger.NOD_DOWN)
            if (pitch >= nodThresholdDeg) add(GestureTrigger.NOD_UP)
            if (smile > 0.8f) add(GestureTrigger.SMILE)
        }
        // prvé gesto, ktoré má priradenú akciu
        val trigger = present.firstOrNull { (actions[it] ?: PageAction.NONE) != PageAction.NONE }

        if (trigger == null || !armed || cooldownLeft > 0f) {
            candidate = null
            return DetectorOutput(cooldownRemaining = cooldownLeft, activeGesture = trigger)
        }

        if (candidate != trigger) {
            candidate = trigger
            candidateSince = nowMs
        }
        val need = when (trigger.kind) {
            GestureKind.WINK -> winkHoldMs
            GestureKind.SMILE -> maxOf(holdMs, 500L)
            else -> holdMs
        }
        val held = nowMs - candidateSince
        if (held >= need) {
            candidate = null
            armed = false
            lastFire = nowMs
            val dir = if (actions[trigger] == PageAction.PREVIOUS) TurnDirection.PREVIOUS else TurnDirection.NEXT
            return DetectorOutput(fired = dir, holdProgress = 1f, cooldownRemaining = 1f, activeGesture = trigger)
        }
        val progress = if (need <= 0) 1f else (held.toFloat() / need).coerceIn(0f, 1f)
        return DetectorOutput(holdProgress = progress, cooldownRemaining = cooldownLeft, activeGesture = trigger)
    }

    /** Ktoré oko žmurklo (jedno zavreté, druhé otvorené); obyčajné žmurknutie oboma sa nepočíta. */
    private fun winkSide(left: Float?, right: Float?): GestureTrigger? {
        if (left == null || right == null) return null
        return when {
            right < 0.25f && left > 0.6f -> GestureTrigger.WINK_RIGHT
            left < 0.25f && right > 0.6f -> GestureTrigger.WINK_LEFT
            else -> null
        }
    }
}
