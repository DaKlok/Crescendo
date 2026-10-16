package com.daklok.crescendo.tracking

import kotlin.math.abs

enum class TurnDirection { NEXT, PREVIOUS }

/** Výsledok jedného kroku detektora – slúži aj na vykreslenie indikátora v UI. */
data class DetectorOutput(
    val fired: TurnDirection? = null,
    /** 0..1 – ako dlho už držíš hlavu natočenú vzhľadom na „hold time“. */
    val holdProgress: Float = 0f,
    /** 0..1 – 1 = práve otočené, 0 = pauza (cooldown) uplynula. */
    val cooldownRemaining: Float = 0f,
)

/**
 * Čistá logika rozhodovania, kedy otočiť stranu (bez závislosti na Androide).
 *
 * Natočenie doprava (z pohľadu hráča) => ďalšia strana, doľava => predchádzajúca.
 * Hysteréza: po otočení sa musí hlava vrátiť do stredu, kým sa detektor znova „odistí“.
 */
class HeadTurnDetector {
    var thresholdDeg: Float = 22f
    var holdMs: Long = 400
    var cooldownMs: Long = 1200
    var reverse: Boolean = false

    private var candidate: TurnDirection? = null
    private var candidateSince = 0L
    private var armed = true
    private var lastFire = Long.MIN_VALUE / 2

    fun reset() {
        candidate = null
        armed = true
    }

    /**
     * @param yawDeg uhol natočenia hlavy (ML Kit headEulerAngleY), alebo null ak nie je vidno tvár.
     */
    fun update(yawDeg: Float?, nowMs: Long): DetectorOutput {
        val cooldownLeft = (1f - (nowMs - lastFire).toFloat() / cooldownMs).coerceIn(0f, 1f)

        if (yawDeg == null) {
            candidate = null
            return DetectorOutput(cooldownRemaining = cooldownLeft)
        }

        if (abs(yawDeg) < thresholdDeg * 0.6f) armed = true

        // ML Kit: záporný Euler Y = hráč otočil hlavu doprava (z jeho pohľadu).
        var dir: TurnDirection? = when {
            yawDeg <= -thresholdDeg -> TurnDirection.NEXT
            yawDeg >= thresholdDeg -> TurnDirection.PREVIOUS
            else -> null
        }
        if (reverse && dir != null) {
            dir = if (dir == TurnDirection.NEXT) TurnDirection.PREVIOUS else TurnDirection.NEXT
        }

        if (dir == null || !armed || cooldownLeft > 0f) {
            candidate = null
            return DetectorOutput(cooldownRemaining = cooldownLeft)
        }

        if (candidate != dir) {
            candidate = dir
            candidateSince = nowMs
        }
        val held = nowMs - candidateSince
        if (held >= holdMs) {
            candidate = null
            armed = false
            lastFire = nowMs
            return DetectorOutput(fired = dir, holdProgress = 1f, cooldownRemaining = 1f)
        }
        val progress = if (holdMs <= 0) 1f else (held.toFloat() / holdMs).coerceIn(0f, 1f)
        return DetectorOutput(holdProgress = progress, cooldownRemaining = cooldownLeft)
    }
}
