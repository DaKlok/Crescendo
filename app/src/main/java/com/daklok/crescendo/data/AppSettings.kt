package com.daklok.crescendo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("Podľa systému"),
    LIGHT("Svetlý"),
    DARK("Tmavý"),
    PAPER("Papier (NXTPAPER)"),
}

enum class LibrarySort(val label: String) {
    RECENT("Naposledy otvorené"),
    ADDED("Naposledy pridané"),
    TITLE("Názov A–Z"),
}

enum class ToolbarPosition(val label: String) {
    LEFT("Vľavo"),
    RIGHT("Vpravo"),
    TOP("Hore"),
    BOTTOM("Dole"),
}

enum class CameraChoice(val label: String) {
    FRONT("Predná"),
    BACK("Zadná"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.PAPER,
    val useDynamicColor: Boolean = false,
    val sort: LibrarySort = LibrarySort.RECENT,

    // Ovládanie otáčania strán hlavou
    val headTrackingEnabled: Boolean = false,
    val camera: CameraChoice = CameraChoice.FRONT,
    /** Uhol natočenia hlavy (v stupňoch), pri ktorom sa strana otočí. */
    val yawThresholdDeg: Float = 22f,
    /** Ako dlho (s) treba držať hlavu natočenú. */
    val holdTimeSec: Float = 0.4f,
    /** Pauza (s) po otočení strany. */
    val cooldownSec: Float = 1.2f,
    val reverseDirection: Boolean = false,
    val showCameraPreview: Boolean = true,

    // Čítačka
    /** Dvojstranové zobrazenie na šírke (landscape). */
    val twoPageSpread: Boolean = true,
    val keepScreenOn: Boolean = true,
    val tapToTurn: Boolean = true,

    // Poznámky
    /** Kreslenie prstom (ak nemáš stylus). So stylusom sa kreslí vždy. */
    val fingerDrawing: Boolean = false,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.RIGHT,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val sort = stringPreferencesKey("sort")
        val headEnabled = booleanPreferencesKey("head_enabled")
        val camera = stringPreferencesKey("camera")
        val yaw = floatPreferencesKey("yaw")
        val hold = floatPreferencesKey("hold")
        val cooldown = floatPreferencesKey("cooldown")
        val reverse = booleanPreferencesKey("reverse")
        val preview = booleanPreferencesKey("preview")
        val twoPage = booleanPreferencesKey("two_page")
        val keepOn = booleanPreferencesKey("keep_on")
        val tap = booleanPreferencesKey("tap")
        val finger = booleanPreferencesKey("finger")
        val toolbarPos = stringPreferencesKey("toolbar_pos")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[K.theme] = updated.themeMode.name
            prefs[K.dynamic] = updated.useDynamicColor
            prefs[K.sort] = updated.sort.name
            prefs[K.headEnabled] = updated.headTrackingEnabled
            prefs[K.camera] = updated.camera.name
            prefs[K.yaw] = updated.yawThresholdDeg
            prefs[K.hold] = updated.holdTimeSec
            prefs[K.cooldown] = updated.cooldownSec
            prefs[K.reverse] = updated.reverseDirection
            prefs[K.preview] = updated.showCameraPreview
            prefs[K.twoPage] = updated.twoPageSpread
            prefs[K.keepOn] = updated.keepScreenOn
            prefs[K.tap] = updated.tapToTurn
            prefs[K.finger] = updated.fingerDrawing
            prefs[K.toolbarPos] = updated.toolbarPosition.name
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOr(this[K.theme], d.themeMode),
            useDynamicColor = this[K.dynamic] ?: d.useDynamicColor,
            sort = enumOr(this[K.sort], d.sort),
            headTrackingEnabled = this[K.headEnabled] ?: d.headTrackingEnabled,
            camera = enumOr(this[K.camera], d.camera),
            yawThresholdDeg = this[K.yaw] ?: d.yawThresholdDeg,
            holdTimeSec = this[K.hold] ?: d.holdTimeSec,
            cooldownSec = this[K.cooldown] ?: d.cooldownSec,
            reverseDirection = this[K.reverse] ?: d.reverseDirection,
            showCameraPreview = this[K.preview] ?: d.showCameraPreview,
            twoPageSpread = this[K.twoPage] ?: d.twoPageSpread,
            keepScreenOn = this[K.keepOn] ?: d.keepScreenOn,
            tapToTurn = this[K.tap] ?: d.tapToTurn,
            fingerDrawing = this[K.finger] ?: d.fingerDrawing,
            toolbarPosition = enumOr(this[K.toolbarPos], d.toolbarPosition),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
