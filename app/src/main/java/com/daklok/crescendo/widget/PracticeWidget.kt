package com.daklok.crescendo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.MainActivity
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PracticeGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Widget na domovskej obrazovke (Material 3 Expressive): vlnitý krúžok dnešného cvičenia s veľkým číslom,
 * koľko ešte zostáva do cieľa, séria dní, pri väčšom widgete týždeň v stĺpčekoch a tlačidlo
 * „pokračovať“ s poslednou skladbou.
 *
 * Kreslí sa celý ([WidgetArt]) v presnej veľkosti widgetu – písmom Google Sans Flex, ktoré systémový
 * widget inak použiť nevie. Na Androide 12+ sa pripraví svetlá aj tmavá verzia (prepína systém sám)
 * vo farbách tapety.
 */
open class PracticeWidget : AppWidgetProvider() {

    /** Druh widgetu (každý druh je samostatný widget v zozname, veľkosťou sa nemení). */
    open val style: ArtStyle get() = ArtStyle.WIDE

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext as CrescendoApp
        app.appScope.launch(Dispatchers.IO) {
            try {
                render(context, manager, ids, style)
            } finally {
                pending.finish()
            }
        }
    }

    /** Zmena veľkosti – widget sa prekreslí presne na novú veľkosť. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    companion object {
        const val ACTION_OPEN_PIECE = "com.daklok.crescendo.OPEN_PIECE"
        const val ACTION_OPEN_PRACTICE = "com.daklok.crescendo.OPEN_PRACTICE"
        const val EXTRA_PIECE_ID = "pieceId"

        /** Všetky druhy widgetu. */
        private val kinds = listOf(
            PracticeWidget::class.java to ArtStyle.WIDE,
            WeekWidget::class.java to ArtStyle.WEEK,
            RingWidget::class.java to ArtStyle.RING,
            RingPieceWidget::class.java to ArtStyle.RING_PIECE,
        )

        /** Obnoví všetky widgety (po cvičení, po otvorení skladby). */
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val app = context.applicationContext as CrescendoApp
            for ((cls, style) in kinds) {
                val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, cls)) }.getOrNull()
                if (ids == null || ids.isEmpty()) continue
                app.appScope.launch(Dispatchers.IO) { runCatching { render(context, manager, ids, style) } }
            }
        }

        fun openPieceIntent(context: Context, pieceId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_PIECE)
                .putExtra(EXTRA_PIECE_ID, pieceId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun openPracticeIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_PRACTICE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** Údaje a texty pre kresbu. */
        private suspend fun loadData(context: Context): Pair<ArtData, Long?> {
            val app = context.applicationContext as CrescendoApp
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val since = today.minusDays(400).atStartOfDay(zone).toInstant().toEpochMilli()
            val sessions = runCatching { app.practice.since(since) }.getOrDefault(emptyList())
            val perDay = sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
                .mapValues { (_, l) -> l.sumOf { it.durationSec.toLong() } }
            val todayMin = ((perDay[today] ?: 0L) / 60).toInt()
            val goal = PracticeGoal.get(context).coerceAtLeast(1)
            var streak = 0
            var d = if ((perDay[today] ?: 0L) > 0) today else today.minusDays(1)
            while ((perDay[d] ?: 0L) > 0) {
                streak++
                d = d.minusDays(1)
            }
            val last = runCatching { app.database.pieceDao().recent(1).firstOrNull() }.getOrNull()
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
            val data = ArtData(
                todayMin = todayMin,
                goalMin = goal,
                streak = streak,
                pieceTitle = last?.title?.replace('_', ' ')?.trim(),
                piecePage = (last?.lastPage ?: 0) + 1,
                remainingFmt = context.getString(R.string.widget_remaining),
                goalDone = context.getString(R.string.widget_goal_done),
                letsStart = context.getString(R.string.widget_lets_start),
                goalFmt = context.getString(R.string.widget_goal_fmt),
                minUnit = context.getString(R.string.widget_min),
                streakText = context.resources.getQuantityString(R.plurals.days, streak, streak),
                pageFmt = context.getString(R.string.widget_page_fmt),
                openLibrary = context.getString(R.string.widget_library),
                week = days.map { ((perDay[it] ?: 0L) / 60).toInt() },
                weekLabels = days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale) },
            )
            return data to last?.id
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray, style: ArtStyle) {
            val app = context.applicationContext as CrescendoApp
            val (data, pieceId) = loadData(context)
            val useDynamic = runCatching { app.settings.settings.first().useDynamicColor }.getOrDefault(true)
            val fonts = AndroidArtCanvas.Fonts.get(context)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val practiceIntent = PendingIntent.getActivity(context, 1, openPracticeIntent(context), flags)
            val pieceIntent = pieceId?.let { PendingIntent.getActivity(context, 2, openPieceIntent(context, it), flags) }
                ?: practiceIntent
            val description = context.getString(R.string.widget_a11y, data.todayMin, data.goalMin) +
                (if (data.streak > 0) ", " + data.streakText else "")

            val dm = context.resources.displayMetrics
            val density = dm.density
            // rovnaké zaoblenie ako pozadie widgetu (Android 12+: systémové)
            val radius = runCatching { context.resources.getDimension(R.dimen.widget_radius) }.getOrDefault(24f * density)
            val nightNow = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            // systém dovolí widgetu bitmapy najviac za ~1,5 obrazovky – na všetky obrázky berieme polovicu
            val screenPx = max(1_000_000L, dm.widthPixels.toLong() * dm.heightPixels)
            val variants = if (Build.VERSION.SDK_INT >= 31) 2 else 1

            // Android 12+: pozadie kreslí systém (rovnaká farba ako widget_bg), obrázok je priehľadný –
            // keď launcher po otočení tabletu použije obrázok inej veľkosti, obsah sa len vycentruje, nič sa nenaťahuje
            val transparent = Build.VERSION.SDK_INT >= 31

            @android.annotation.SuppressLint("NewApi")
            fun palette(dark: Boolean): ArtPalette {
                val base = (if (useDynamic) WidgetPalettes.dynamic(context, dark) else null) ?: WidgetPalettes.app(dark)
                if (!transparent) return base
                val bg = runCatching {
                    context.getColor(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50)
                }.getOrNull() ?: return base
                return base.copy(background = bg)
            }

            /** Jedno rozloženie pre veľkosť [wDp]×[hDp]; obrázok má najviac [maxPx] pixelov. */
            fun build(wDp: Float, hDp: Float, maxPx: Float): RemoteViews {
                // kreslí sa v rozlíšení displeja; pri veľkom widgete (alebo veľa veľkostiach) o niečo menej
                var scale = density
                if (wDp * hDp * scale * scale > maxPx) scale = sqrt(maxPx / (wDp * hDp))
                val wPx = max(1, (wDp * scale).toInt())
                val hPx = max(1, (hDp * scale).toInt())
                val rPx = radius / density * scale

                fun draw(p: ArtPalette): Pair<Bitmap, ArtLayout> {
                    val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
                    val layout = WidgetArt.draw(AndroidArtCanvas(Canvas(bmp), fonts), wPx.toFloat(), hPx.toFloat(), scale, rPx, data, p, style, !transparent)
                    return bmp to layout
                }

                val v = RemoteViews(context.packageName, R.layout.widget_practice)
                val layout: ArtLayout
                if (Build.VERSION.SDK_INT >= 31) {
                    val (light, lay) = draw(palette(false))
                    val (dark, _) = draw(palette(true))
                    layout = lay
                    v.setIcon(R.id.widget_canvas, "setImageIcon", Icon.createWithBitmap(light), Icon.createWithBitmap(dark))
                } else {
                    val (bmp, lay) = draw(palette(nightNow))
                    layout = lay
                    v.setImageViewBitmap(R.id.widget_canvas, bmp)
                }
                v.setContentDescription(R.id.widget_canvas, description)
                v.setOnClickPendingIntent(android.R.id.background, practiceIntent)
                if (layout.hasCta) {
                    // klikateľné tlačidlo „pokračovať“ presne tam, kde je nakreslené
                    val k = density / scale
                    v.setViewVisibility(R.id.widget_cta_frame, View.VISIBLE)
                    v.setViewPadding(
                        R.id.widget_cta_frame,
                        (layout.ctaL * k).toInt(),
                        (layout.ctaT * k).toInt(),
                        ((wPx - layout.ctaR) * k).toInt(),
                        ((hPx - layout.ctaB) * k).toInt(),
                    )
                    v.setOnClickPendingIntent(R.id.widget_cta, pieceIntent)
                    v.setContentDescription(R.id.widget_cta, data.pieceTitle ?: data.openLibrary)
                } else {
                    v.setViewVisibility(R.id.widget_cta_frame, View.GONE)
                }
                return v
            }

            /** Widget pre všetky [sizes] (Android 12+: launcher si vyberie; staršie: na výšku / na šírku). */
            fun views(sizes: List<SizeF>, maxPx: Float): RemoteViews = when {
                sizes.size == 1 -> build(sizes[0].width, sizes[0].height, maxPx)
                Build.VERSION.SDK_INT >= 31 -> RemoteViews(sizes.associateWith { build(it.width, it.height, maxPx) })
                else -> RemoteViews(build(sizes[1].width, sizes[1].height, maxPx), build(sizes[0].width, sizes[0].height, maxPx))
            }

            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                val sizes = widgetSizes(options)
                // keď systém obrázky odmietne (priveľa pamäte), skúsi sa menšie rozlíšenie, potom jedna veľkosť
                for (attempt in 0 until 3) {
                    val list = if (attempt < 2) sizes else sizes.take(1)
                    val maxPx = min(650_000f, 0.75f * screenPx / (list.size * variants)) / (if (attempt == 0) 1f else 2f)
                    val ok = runCatching { manager.updateAppWidget(id, views(list, maxPx)) }.isSuccess
                    if (ok) break
                }
            }
        }

        /**
         * Veľkosti widgetu (dp). Android 12+: všetky, ktoré launcher hlási (takmer rovnaké sa zlúčia);
         * staršie: [na výšku, na šírku] z min/max údajov.
         */
        private fun widgetSizes(options: Bundle): List<SizeF> {
            if (Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                val reported = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    ?.filter { it.width > 0 && it.height > 0 }.orEmpty()
                val out = ArrayList<SizeF>()
                for (s in reported) {
                    if (out.none { abs(it.width - s.width) < 4f && abs(it.height - s.height) < 4f }) out += s
                    if (out.size == 4) break
                }
                if (out.isNotEmpty()) return out
            }
            val (portrait, landscape) = fallbackSizes(options)
            return if (portrait == landscape) listOf(portrait) else listOf(portrait, landscape)
        }

        /** Veľkosti na výšku a na šírku z údajov launchera (dp). */
        private fun fallbackSizes(options: Bundle): Pair<SizeF, SizeF> {
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val pw = (if (minW > 0) minW else 300).toFloat()
            val ph = (if (maxH > 0) maxH else 150).toFloat()
            val lw = (if (maxW > 0) maxW else 340).toFloat()
            val lh = (if (minH > 0) minH else 140).toFloat()
            return SizeF(min(pw, 900f), min(ph, 900f)) to SizeF(min(lw, 900f), min(lh, 900f))
        }
    }
}
