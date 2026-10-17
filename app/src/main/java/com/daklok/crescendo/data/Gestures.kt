package com.daklok.crescendo.data

import androidx.annotation.StringRes
import com.daklok.crescendo.R

/** Druh pohybu (podľa neho sa zobrazujú nastavenia citlivosti). */
enum class GestureKind { TURN, TILT, NOD, WINK, SMILE }

/** Jedno konkrétne gesto, ktoré sa dá priradiť k otočeniu strany. */
enum class GestureTrigger(@StringRes val labelRes: Int, val kind: GestureKind) {
    TURN_RIGHT(R.string.trig_turn_right, GestureKind.TURN),
    TURN_LEFT(R.string.trig_turn_left, GestureKind.TURN),
    TILT_RIGHT(R.string.trig_tilt_right, GestureKind.TILT),
    TILT_LEFT(R.string.trig_tilt_left, GestureKind.TILT),
    NOD_DOWN(R.string.trig_nod_down, GestureKind.NOD),
    NOD_UP(R.string.trig_nod_up, GestureKind.NOD),
    WINK_RIGHT(R.string.trig_wink_right, GestureKind.WINK),
    WINK_LEFT(R.string.trig_wink_left, GestureKind.WINK),
    SMILE(R.string.trig_smile, GestureKind.SMILE),
}

/** Čo gesto urobí. */
enum class PageAction { NONE, NEXT, PREVIOUS }

/** Rýchle predvoľby – jedným ťuknutím nastavia rozumnú kombináciu. */
enum class GesturePreset(@StringRes val labelRes: Int, val map: Map<GestureTrigger, PageAction>) {
    TURN(
        R.string.preset_turn,
        mapOf(GestureTrigger.TURN_RIGHT to PageAction.NEXT, GestureTrigger.TURN_LEFT to PageAction.PREVIOUS),
    ),
    TILT(
        R.string.preset_tilt,
        mapOf(GestureTrigger.TILT_RIGHT to PageAction.NEXT, GestureTrigger.TILT_LEFT to PageAction.PREVIOUS),
    ),
    WINK(
        R.string.preset_wink,
        mapOf(GestureTrigger.WINK_RIGHT to PageAction.NEXT, GestureTrigger.WINK_LEFT to PageAction.PREVIOUS),
    ),
    ONE_EYE(
        R.string.preset_one_eye,
        mapOf(GestureTrigger.WINK_LEFT to PageAction.NEXT),
    ),
}

object GestureMap {
    val DEFAULT: Map<GestureTrigger, PageAction> = GesturePreset.TURN.map

    /** "TURN_RIGHT=NEXT,TURN_LEFT=PREVIOUS" -> mapa (neznáme položky sa preskočia). */
    fun parse(text: String?): Map<GestureTrigger, PageAction> {
        if (text == null) return DEFAULT
        if (text.isBlank()) return emptyMap()
        return text.split(',').mapNotNull { part ->
            val (k, v) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val trig = GestureTrigger.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
            val act = PageAction.entries.firstOrNull { it.name == v } ?: return@mapNotNull null
            if (act == PageAction.NONE) null else trig to act
        }.toMap()
    }

    fun serialize(map: Map<GestureTrigger, PageAction>): String =
        map.filterValues { it != PageAction.NONE }.entries.joinToString(",") { "${it.key.name}=${it.value.name}" }
}

/** Či je nejaké gesto daného druhu priradené. */
fun Map<GestureTrigger, PageAction>.usesKind(kind: GestureKind): Boolean =
    any { it.key.kind == kind && it.value != PageAction.NONE }
