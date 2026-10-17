package com.daklok.crescendo.ui.common

import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.outlined.LibraryMusic
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.R
import com.daklok.crescendo.data.backup.BackupState
import com.daklok.crescendo.data.backup.backupManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OverlayKind { LANGUAGE, RESTORE, RESTORED, STARTUP }

/**
 * Celoobrazovkové prechodové obrazovky (zmena jazyka, obnovenie zálohy).
 * Zobrazia sa ešte pred chvíľou, keď sa aplikácia prestavuje, a po novom štarte plynulo zmiznú.
 */
object AppOverlay {
    var kind by mutableStateOf<OverlayKind?>(null)
        private set

    /** Knižnica je načítaná (aj s náhľadmi) – úvodná / prechodová obrazovka môže zmiznúť. */
    var contentReady by mutableStateOf(false)

    private const val PREFS = "crescendo_overlay"
    private const val KEY = "pending"

    /** Identita aktivity, v ktorej sa prechod začal (0 = neznáma / nový proces). */
    private var ownerId = 0

    fun show(k: OverlayKind, owner: Any? = null) {
        ownerId = owner?.let { System.identityHashCode(it) } ?: 0
        kind = k
    }

    internal fun startedIn(activityId: Int) = ownerId != 0 && ownerId == activityId

    fun hide() {
        kind = null
    }

    private const val KEY_BG = "background"

    fun rememberBackground(context: Context, argb: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_BG, argb).apply()
    }

    /** Farba pozadia prechodovej obrazovky (na nastavenie pozadia okna ešte pred prvým snímkom). */
    fun savedBackground(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_BG)) prefs.getInt(KEY_BG, 0) else null
    }

    /** Zapamätá si, že po reštarte / prestavaní aktivity sa má prechod dokončiť. */
    fun markPending(context: Context, k: OverlayKind) {
        // commit (nie apply) – zápis musí prebehnúť ešte pred ukončením procesu
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, k.name).commit()
    }

    /** Ak prechod čaká na dokončenie, vráti ho (a vymaže). */
    fun consumePending(context: Context): OverlayKind? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY, null) ?: return null
        prefs.edit().remove(KEY).apply()
        return OverlayKind.entries.firstOrNull { it.name == name }
    }
}

/**
 * Vykreslí prechodovú obrazovku nad celým obsahom.
 *
 * Kreslí sa priamo v hlavnom okne (nie v samostatnom dialógu) – vďaka tomu je viditeľná už
 * v úplne prvom snímku po prestavaní aktivity a nič nepreblikne.
 */
@Composable
fun AppOverlayHost() {
    val kind = AppOverlay.kind
    // ak je prechod aktívny už pri vzniku obrazovky (po zmene jazyka / reštarte), je hneď viditeľný bez animácie
    val visibleState = remember { MutableTransitionState(kind != null) }
    visibleState.targetState = kind != null
    var lastKind by remember { mutableStateOf(kind) }
    if (kind != null) lastKind = kind

    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surface
    LaunchedEffect(kind, surface) {
        // farbu si zapamätáme, aby aj pozadie okna pri štarte malo rovnakú farbu
        if (kind != null) AppOverlay.rememberBackground(context, surface.toArgb())
    }

    BackHandler(enabled = kind != null) { /* počas prechodu sa nedá odísť */ }

    // Automatické zatvorenie – nezávisí od toho, koľkokrát sa aktivita medzitým prestaví.
    // RESTORE (prebiehajúca obnova) sa zatvára sama po dokončení / chybe.
    val activityId = remember(context) { System.identityHashCode(context.findActivity() ?: context) }
    LaunchedEffect(kind, activityId) {
        when (kind) {
            OverlayKind.LANGUAGE -> {
                // v novej (prestavanej) aktivite rýchlo zmizne; ak sa aktivita neprestavala
                // (napr. jazyk sa v skutočnosti nezmenil), zavrie sa po krátkej poistke
                delay(if (AppOverlay.startedIn(activityId)) 2500 else 650)
                awaitContent(3000)
                AppOverlay.consumePending(context)
                AppOverlay.hide()
            }
            OverlayKind.RESTORED -> {
                delay(1400)
                awaitContent(4000)
                AppOverlay.hide()
            }
            OverlayKind.STARTUP -> {
                // úvodná obrazovka pri spustení: zmizne, keď je knižnica pripravená (poistka 5 s).
                // Čas sa počíta od prvého skutočne vykresleného snímku – pri štarte býva prvých pár snímok
                // pomalých – a animácia ostane viditeľná aspoň chvíľu, nech len neblikne.
                androidx.compose.runtime.withFrameNanos { }
                val shownAt = android.os.SystemClock.uptimeMillis()
                awaitContent(5000)
                val left = STARTUP_MIN_MS - (android.os.SystemClock.uptimeMillis() - shownAt)
                if (left > 0) delay(left)
                AppOverlay.hide()
            }
            else -> Unit
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(420, delayMillis = 80)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // pohltí všetky dotyky – pod prechodovou obrazovkou sa nedá nič stlačiť
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
        ) {
            lastKind?.let { OverlayContent(it) }
        }
    }
}

/** Ako dlho najmenej ostane úvodná obrazovka viditeľná (od prvého snímku). */
private const val STARTUP_MIN_MS = 1100L

/** Počká, kým je obsah pripravený (najviac [timeoutMs]). */
private suspend fun awaitContent(timeoutMs: Long) {
    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
        androidx.compose.runtime.snapshotFlow { AppOverlay.contentReady }.first { it }
    }
}

@Composable
private fun OverlayContent(kind: OverlayKind) {
    val backupState by LocalContext.current.backupManager.state.collectAsStateWithLifecycle()

    val (icon, title, subtitle) = when (kind) {
        OverlayKind.LANGUAGE -> Triple(
            Icons.Outlined.Language,
            stringResource(R.string.ov_language_title),
            stringResource(R.string.ov_language_sub),
        )
        OverlayKind.RESTORE -> Triple(
            Icons.Outlined.Restore,
            stringResource(R.string.ov_restore_title),
            (backupState as? BackupState.Working)?.label ?: stringResource(R.string.ov_restore_sub),
        )
        OverlayKind.STARTUP -> Triple(
            Icons.Outlined.LibraryMusic,
            "Crescendo",
            stringResource(R.string.ov_startup_sub),
        )
        OverlayKind.RESTORED -> Triple(
            Icons.Outlined.CloudDone,
            stringResource(R.string.ov_restored_title),
            stringResource(R.string.ov_restored_sub),
        )
    }
    val progress = (backupState as? BackupState.Working)?.progress.takeIf { kind == OverlayKind.RESTORE }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            // morfujúci „expressive“ tvar s ikonou – pri zobrazení pružne „vyskočí“
            // pri štarte aplikácie je tvar viditeľný hneď (bez „vyskočenia“), aby sa neobjavil až neskoro
            val appear = remember { MutableTransitionState(kind == OverlayKind.STARTUP).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = appear,
                enter = scaleIn(initialScale = 0.6f, animationSpec = Motion.bouncy()) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ExpressiveLoader(size = 132.dp)
                    AnimatedContent(
                        targetState = icon,
                        transitionSpec = {
                            (scaleIn(initialScale = 0.5f, animationSpec = Motion.bouncy()) + fadeIn()) togetherWith
                                (scaleOut(targetScale = 0.5f) + fadeOut())
                        },
                        label = "overlayIcon",
                    ) { ic ->
                        Icon(
                            ic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(Motion.bouncy()) { it / 3 }) togetherWith fadeOut(tween(140))
                    },
                    label = "overlayTitle",
                ) { t ->
                    Text(
                        t,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "overlaySubtitle",
                ) { s ->
                    Text(
                        s,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            AnimatedVisibility(visible = progress != null, enter = fadeIn(), exit = fadeOut()) {
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier
                        .width(240.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

/**
 * Animovaný indikátor v duchu Material 3 Expressive: zaoblený „kvetinový“ tvar,
 * ktorý sa pomaly otáča, dýcha a plynule mení počet lístkov.
 */
@Composable
fun ExpressiveLoader(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "rotation",
    )
    val morph by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "morph",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val container = MaterialTheme.colorScheme.primaryContainer
    val ring = MaterialTheme.colorScheme.primary

    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f * breathe
        rotate(rotation, c) {
            // vonkajší jemný tvar
            drawPath(flowerPath(c, r, morph, lobesA = 9, lobesB = 6, depth = 0.07f), ring.copy(alpha = 0.18f))
            // hlavný tvar
            drawPath(flowerPath(c, r * 0.86f, 1f - morph, lobesA = 8, lobesB = 5, depth = 0.09f), container)
        }
    }
}

/** Uzavretý tvar s vlnitým okrajom; [mix] plynule prelína počet lístkov A ↔ B. */
private fun flowerPath(c: Offset, r: Float, mix: Float, lobesA: Int, lobesB: Int, depth: Float): Path {
    val path = Path()
    val steps = 180
    for (i in 0..steps) {
        val t = (2.0 * PI * i / steps)
        val wave = (1f - mix) * cos(lobesA * t).toFloat() + mix * cos(lobesB * t).toFloat()
        val rr = r * (1f - depth + depth * wave)
        val x = c.x + rr * cos(t).toFloat()
        val y = c.y + rr * sin(t).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
