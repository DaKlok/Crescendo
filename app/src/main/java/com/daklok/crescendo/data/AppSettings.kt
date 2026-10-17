package com.daklok.crescendo.data

import com.daklok.crescendo.R
import androidx.annotation.StringRes
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    PAPER(R.string.theme_paper),
}

enum class LibrarySort(@StringRes val labelRes: Int) {
    RECENT(R.string.sort_recent),
    ADDED(R.string.sort_added),
    TITLE(R.string.sort_title),
}

enum class ToolbarPosition(@StringRes val labelRes: Int) {
    LEFT(R.string.pos_left),
    RIGHT(R.string.pos_right),
    TOP(R.string.pos_top),
    BOTTOM(R.string.pos_bottom),
}

enum class CameraChoice(@StringRes val labelRes: Int) {
    FRONT(R.string.camera_front),
    BACK(R.string.camera_back),
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

    // Gestá: ktoré gesto otočí ďalej / späť (plne nastaviteľné)
    val gestureMap: Map<GestureTrigger, PageAction> = GestureMap.DEFAULT,
    val tiltThresholdDeg: Float = 16f,
    val nodThresholdDeg: Float = 14f,
    val winkHoldSec: Float = 0.25f,
    val swapEyes: Boolean = false,
    /** Krátka vibrácia, keď gesto otočí stranu. */
    val turnHaptic: Boolean = true,
    /** Kalibrácia „rovno“ – odchýlka kamery od stredu (stupne). */
    val yawOffset: Float = 0f,
    val rollOffset: Float = 0f,
    val pitchOffset: Float = 0f,
    val showCameraPreview: Boolean = true,

    // Čítačka
    /** Dvojstranové zobrazenie na šírke (landscape). */
    val twoPageSpread: Boolean = true,
    val keepScreenOn: Boolean = true,
    val tapToTurn: Boolean = true,
    /** Tlačidlá hlasitosti otáčajú strany (inak menia hlasitosť ako zvyčajne). */
    val volumeKeysTurn: Boolean = false,

    // Poznámky
    /** Kreslenie prstom (ak nemáš stylus). So stylusom sa kreslí vždy. */
    val fingerDrawing: Boolean = false,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.RIGHT,

    /** Jemné vibrácie pri ťuknutiach a prepínačoch. */
    val haptics: Boolean = true,

    // Záloha na Google Drive
    /** E-mail pripojeného Google účtu ("" = nepripojený). */
    val backupEmail: String = "",
    val autoBackup: Boolean = false,
    /** Čas poslednej úspešnej zálohy (ms), 0 = ešte žiadna. */
    val lastBackupAt: Long = 0L,
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
        val gestureMap = stringPreferencesKey("gesture_map")
        val tiltThresholdDeg = floatPreferencesKey("tilt_thr")
        val nodThresholdDeg = floatPreferencesKey("nod_thr")
        val winkHoldSec = floatPreferencesKey("wink_hold")
        val swapEyes = booleanPreferencesKey("swap_eyes")
        val turnHaptic = booleanPreferencesKey("turn_haptic")
        val yawOffset = floatPreferencesKey("yaw_off")
        val rollOffset = floatPreferencesKey("roll_off")
        val pitchOffset = floatPreferencesKey("pitch_off")
        val preview = booleanPreferencesKey("preview")
        val twoPage = booleanPreferencesKey("two_page")
        val keepOn = booleanPreferencesKey("keep_on")
        val tap = booleanPreferencesKey("tap")
        val volumeKeys = booleanPreferencesKey("volume_keys")
        val finger = booleanPreferencesKey("finger")
        val toolbarPos = stringPreferencesKey("toolbar_pos")
        val haptics = booleanPreferencesKey("haptics")
        val backupEmail = stringPreferencesKey("backup_email")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val lastBackupAt = longPreferencesKey("last_backup_at")
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
            prefs[K.gestureMap] = GestureMap.serialize(updated.gestureMap)
            prefs[K.tiltThresholdDeg] = updated.tiltThresholdDeg
            prefs[K.nodThresholdDeg] = updated.nodThresholdDeg
            prefs[K.winkHoldSec] = updated.winkHoldSec
            prefs[K.swapEyes] = updated.swapEyes
            prefs[K.turnHaptic] = updated.turnHaptic
            prefs[K.yawOffset] = updated.yawOffset
            prefs[K.rollOffset] = updated.rollOffset
            prefs[K.pitchOffset] = updated.pitchOffset
            prefs[K.preview] = updated.showCameraPreview
            prefs[K.twoPage] = updated.twoPageSpread
            prefs[K.keepOn] = updated.keepScreenOn
            prefs[K.tap] = updated.tapToTurn
            prefs[K.volumeKeys] = updated.volumeKeysTurn
            prefs[K.finger] = updated.fingerDrawing
            prefs[K.toolbarPos] = updated.toolbarPosition.name
            prefs[K.haptics] = updated.haptics
            prefs[K.backupEmail] = updated.backupEmail
            prefs[K.autoBackup] = updated.autoBackup
            prefs[K.lastBackupAt] = updated.lastBackupAt
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
            gestureMap = GestureMap.parse(this[K.gestureMap]),
            tiltThresholdDeg = this[K.tiltThresholdDeg] ?: d.tiltThresholdDeg,
            nodThresholdDeg = this[K.nodThresholdDeg] ?: d.nodThresholdDeg,
            winkHoldSec = this[K.winkHoldSec] ?: d.winkHoldSec,
            swapEyes = this[K.swapEyes] ?: d.swapEyes,
            turnHaptic = this[K.turnHaptic] ?: d.turnHaptic,
            yawOffset = this[K.yawOffset] ?: d.yawOffset,
            rollOffset = this[K.rollOffset] ?: d.rollOffset,
            pitchOffset = this[K.pitchOffset] ?: d.pitchOffset,
            showCameraPreview = this[K.preview] ?: d.showCameraPreview,
            twoPageSpread = this[K.twoPage] ?: d.twoPageSpread,
            keepScreenOn = this[K.keepOn] ?: d.keepScreenOn,
            tapToTurn = this[K.tap] ?: d.tapToTurn,
            volumeKeysTurn = this[K.volumeKeys] ?: d.volumeKeysTurn,
            fingerDrawing = this[K.finger] ?: d.fingerDrawing,
            toolbarPosition = enumOr(this[K.toolbarPos], d.toolbarPosition),
            haptics = this[K.haptics] ?: d.haptics,
            backupEmail = this[K.backupEmail] ?: d.backupEmail,
            autoBackup = this[K.autoBackup] ?: d.autoBackup,
            lastBackupAt = this[K.lastBackupAt] ?: d.lastBackupAt,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
