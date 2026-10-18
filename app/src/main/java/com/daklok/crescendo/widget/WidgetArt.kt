package com.daklok.crescendo.widget

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Kresba widgetu (Material 3 Expressive). Je nezávislá od Androidu – kreslí cez [ArtCanvas],
 * takže rovnaký kód vykreslí widget v telefóne aj náhľad pri návrhu.
 */

/** Písma widgetu: Google Sans Flex (výrazné / text) a ikony Material Symbols Rounded. */
enum class ArtFont { DISPLAY, TEXT, ICON }

/** Čo vie plátno nakresliť. Farby sú ARGB. */
interface ArtCanvas {
    fun fillRoundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int)
    fun fillCircle(cx: Float, cy: Float, r: Float, color: Int)
    fun fillPolygon(xs: FloatArray, ys: FloatArray, color: Int)
    fun strokePolyline(xs: FloatArray, ys: FloatArray, width: Float, color: Int, closed: Boolean = false)
    fun text(s: String, x: Float, baseline: Float, font: ArtFont, size: Float, color: Int, tracking: Float = 0f)
    fun measure(s: String, font: ArtFont, size: Float, tracking: Float = 0f): Float
    /** Výška veľkých písmen / číslic nad účiarím (na presné zarovnanie). */
    fun capHeight(font: ArtFont, size: Float): Float
}

/** Farby widgetu (Material You alebo farby aplikácie). */
data class ArtPalette(
    val background: Int,
    val onBackground: Int,
    val onBackgroundMuted: Int,
    val heroContainer: Int,
    val onHeroContainer: Int,
    val ring: Int,
    val ringTrack: Int,
    val ringDone: Int,
    val chip: Int,
    val onChip: Int,
    /** Farba série (plamienok) v texte. */
    val streak: Int,
    val cta: Int,
    val onCta: Int,
)

/** Údaje a texty (už preložené). */
data class ArtData(
    val todayMin: Int,
    val goalMin: Int,
    val streak: Int,
    val pieceTitle: String?,
    val piecePage: Int,
    /** „Ešte %d min“ */
    val remainingFmt: String,
    /** „Cieľ splnený“ */
    val goalDone: String,
    /** „Poďme cvičiť“ */
    val letsStart: String,
    /** „Cieľ %d min“ */
    val goalFmt: String,
    /** „min“ */
    val minUnit: String,
    /** „%d dní“ (séria) – už vybraný tvar podľa počtu */
    val streakText: String,
    /** „str. %d“ */
    val pageFmt: String,
    /** „Knižnica“ (keď ešte nie je otvorená žiadna skladba) */
    val openLibrary: String,
    /** Minúty za posledných 7 dní (najstarší → dnes) – stĺpčeky pri väčšom widgete. */
    val week: List<Int> = emptyList(),
    /** Skratky dní k [week] („P“, „U“, …). */
    val weekLabels: List<String> = emptyList(),
)

/**
 * Druh widgetu – používateľ si ho vyberie v zozname widgetov a pri zmene veľkosti sa nemení.
 * WIDE: krúžok, „ešte X min“ a tlačidlo pokračovať; WEEK: navyše týždeň v stĺpčekoch;
 * RING: len krúžok a séria; RING_PIECE: krúžok, séria a pod nimi tlačidlo pokračovať.
 */
enum class ArtStyle { WIDE, WEEK, RING, RING_PIECE }

/** Kde je čo (na klikateľné miesta). Hodnoty v px. */
data class ArtLayout(val ctaL: Float, val ctaT: Float, val ctaR: Float, val ctaB: Float, val hasCta: Boolean)

object WidgetArt {

    private const val ICON_PLAY = ""
    private const val ICON_FIRE = ""
    private const val ICON_CHECK = ""
    private const val ICON_NOTE = ""

    /** Veľkosť podnadpisu (dp). */
    private const val SUB_SIZE = 13.5f

    /**
     * Nakreslí widget na plátno [w]×[h] px. [dp] = počet px na 1 dp, [radius] = zaoblenie rohov (px).
     */
    fun draw(
        c: ArtCanvas, w: Float, h: Float, dp: Float, radius: Float, d: ArtData, p: ArtPalette,
        style: ArtStyle = ArtStyle.WIDE,
        /** false = pozadie kreslí samotný widget (obrázok je priehľadný a pri inom pomere strán sa len vycentruje). */
        fillBackground: Boolean = true,
    ): ArtLayout {
        if (fillBackground) c.fillRoundRect(0f, 0f, w, h, radius, p.background)
        return when (style) {
            // vyšší než široký pomer (napr. tablet na výšku) → krúžok s nadpisom hore, pod nimi zvyšok
            ArtStyle.WIDE, ArtStyle.WEEK -> if (h >= w * 0.62f && w / dp >= 200f) {
                drawStacked(c, w, h, dp, d, p, week = style == ArtStyle.WEEK)
            } else {
                drawWide(c, w, h, dp, d, p, week = style == ArtStyle.WEEK)
            }
            ArtStyle.RING -> drawCompact(c, w, h, dp, d, p, cta = false)
            ArtStyle.RING_PIECE -> drawCompact(c, w, h, dp, d, p, cta = true)
        }
    }

    // ───────────────────────── široký (4×2) ─────────────────────────

    private fun drawWide(c: ArtCanvas, w: Float, h: Float, dp: Float, d: ArtData, p: ArtPalette, week: Boolean): ArtLayout {
        val pad = 16f * dp
        val hero = min(h - 2 * pad, w * 0.36f)
        val hx = pad + 2f * dp
        val hy = (h - hero) / 2f
        drawHero(c, hx, hy, hero, dp, d, p)

        // pravý stĺpec
        val x0 = hx + hero + 20f * dp
        val colW = w - x0 - pad
        val top = pad + 4f * dp

        // nadpis: „Ešte 12 min“ / „Cieľ splnený“ / „Poďme cvičiť“
        val headline = headline(d)
        val hSize = fitSize(c, headline, ArtFont.DISPLAY, min(32f * dp, h * 0.17f), colW - 4f * dp, 16f * dp, -0.01f)
        val capH = c.capHeight(ArtFont.DISPLAY, hSize)
        val capS = c.capHeight(ArtFont.TEXT, SUB_SIZE * dp)
        val subGap = 10f * dp

        // tlačidlo „pokračovať“ dole v stĺpci; pri nízkom widgete bez podnadpisu, inak bez tlačidla
        var ctaH = min(56f * dp, max(46f * dp, h * 0.32f))
        val ctaMin = 44f * dp
        fun fits(sub: Boolean, ch: Float) = (h - pad - ch) - (top + capH + if (sub) subGap + capS else 0f) >= 8f * dp
        var showSub = true
        val hasCta = when {
            fits(true, ctaH) -> true
            fits(true, ctaMin) -> { ctaH = ctaMin; true }
            fits(false, ctaMin) -> { ctaH = ctaMin; showSub = false; true }
            else -> false
        }
        // bez tlačidla je text zvislo v strede
        val textH = capH + subGap + capS
        val hBase = (if (hasCta) top else (h - textH) / 2f) + capH
        drawHeadline(c, headline, x0, hBase, hSize, d, p)
        val sBase = hBase + subGap + capS
        if (showSub) subline(c, x0, sBase, colW, dp, d, p)
        val ctaB = h - pad
        val ctaT = ctaB - ctaH
        // widget „týždeň“: keď sa nezmestia stĺpčeky aj tlačidlo, prednosť majú stĺpčeky
        if (week && d.week.size == 7 && (!hasCta || ctaT - sBase < 56f * dp)) {
            val subBase = if (showSub) sBase else hBase
            if (h - pad - subBase >= 48f * dp) {
                if (!showSub) subline(c, x0, sBase, colW, dp, d, p)
                weekBars(c, x0, sBase + 14f * dp, colW, h - pad - sBase - 14f * dp, dp, d, p)
                return ArtLayout(0f, 0f, 0f, 0f, false)
            }
        }
        if (!hasCta) return ArtLayout(0f, 0f, 0f, 0f, false)

        // väčší widget (tablet): týždeň v stĺpčekoch medzi textom a tlačidlom
        val free = ctaT - sBase
        if (week && showSub && free >= 56f * dp && d.week.size == 7) {
            weekBars(c, x0, sBase + 16f * dp, colW, free - 30f * dp, dp, d, p)
        }
        drawCta(c, x0, ctaT, x0 + colW, ctaB, dp, d, p)
        return ArtLayout(x0, ctaT, x0 + colW, ctaB, true)
    }

    /** Vyšší widget: hore krúžok a vedľa nadpis, pod nimi týždeň (ak treba) a tlačidlo na celú šírku. */
    private fun drawStacked(c: ArtCanvas, w: Float, h: Float, dp: Float, d: ArtData, p: ArtPalette, week: Boolean): ArtLayout {
        val pad = 18f * dp
        val gap = 18f * dp
        val ctaH = min(56f * dp, max(46f * dp, h * 0.16f))
        val showWeek = week && d.week.size == 7
        val barsMin = 72f * dp
        val hero = min(min(w * 0.42f, 180f * dp), h - 2 * pad - ctaH - gap - (if (showWeek) barsMin + gap else 0f))
        // na výšku je málo miesta → radšej rozloženie vedľa seba
        if (hero < 96f * dp) return drawWide(c, w, h, dp, d, p, week)
        val barsBlock = if (showWeek) min(h - 2 * pad - ctaH - hero - 2 * gap, 140f * dp) else 0f
        val total = hero + gap + ctaH + (if (showWeek) barsBlock + gap else 0f)
        val top = max(pad, (h - total) / 2f)

        drawHero(c, pad, top, hero, dp, d, p)
        // nadpis a podnadpis vedľa krúžku, zvislo v jeho strede
        val x0 = pad + hero + 18f * dp
        val colW = w - x0 - pad
        if (colW > 70f * dp) {
            val headline = headline(d)
            val hSize = fitSize(c, headline, ArtFont.DISPLAY, min(32f * dp, hero * 0.26f), colW - 2f * dp, 15f * dp, -0.01f)
            val capH = c.capHeight(ArtFont.DISPLAY, hSize)
            val capS = c.capHeight(ArtFont.TEXT, SUB_SIZE * dp)
            val textH = capH + 10f * dp + capS
            val hBase = top + (hero - textH) / 2f + capH
            drawHeadline(c, headline, x0, hBase, hSize, d, p)
            subline(c, x0, hBase + 10f * dp + capS, colW, dp, d, p)
        }
        var y = top + hero + gap
        if (showWeek && barsBlock >= 48f * dp) {
            weekBars(c, pad, y, w - 2 * pad, barsBlock, dp, d, p, 112f * dp)
            y += barsBlock + gap
        }
        val ctaT = min(y, h - pad - ctaH)
        drawCta(c, pad, ctaT, w - pad, ctaT + ctaH, dp, d, p)
        return ArtLayout(pad, ctaT, w - pad, ctaT + ctaH, true)
    }

    /** Posledných 7 dní: zaoblené stĺpčeky (dnes zvýraznený), pod nimi skratky dní. */
    private fun weekBars(
        c: ArtCanvas, x: Float, y: Float, w: Float, h: Float, dp: Float, d: ArtData, p: ArtPalette,
        maxBarsH: Float = 72f * dp,
    ) {
        val labelSize = 11f * dp
        val labelH = c.capHeight(ArtFont.TEXT, labelSize) + 8f * dp
        val barsH = (h - labelH).coerceAtMost(maxBarsH)
        if (barsH < 20f * dp) return
        val top = y + (h - labelH - barsH) / 2f
        val maxV = max(d.goalMin, d.week.maxOrNull() ?: 0).coerceAtLeast(1)
        val slot = w / 7f
        val bw = min(slot * 0.56f, 18f * dp)
        for (i in 0 until 7) {
            val v = d.week[i]
            val bx = x + slot * i + (slot - bw) / 2f
            val today = i == 6
            // dráha (celá výška) a hodnota
            c.fillRoundRect(bx, top, bx + bw, top + barsH, bw / 2f, p.ringTrack and 0x00FFFFFF or (0x66 shl 24))
            if (v > 0) {
                val bh = max(bw, barsH * v / maxV)
                val color = when {
                    v >= d.goalMin -> p.ringDone
                    today -> p.ring
                    else -> p.ring and 0x00FFFFFF or (0x99 shl 24)
                }
                c.fillRoundRect(bx, top + barsH - bh, bx + bw, top + barsH, bw / 2f, color)
            }
            val lab = d.weekLabels.getOrNull(i) ?: ""
            val lw = c.measure(lab, ArtFont.TEXT, labelSize)
            c.text(lab, bx + bw / 2f - lw / 2f, top + barsH + labelH - 2f * dp, ArtFont.TEXT, labelSize,
                if (today) p.onBackground else p.onBackgroundMuted)
        }
    }

    /** „Cieľ 30 min · 🔥 5 dní“ – séria je zvýraznená farbou a plamienkom. */
    private fun subline(c: ArtCanvas, x: Float, base: Float, maxW: Float, dp: Float, d: ArtData, p: ArtPalette) {
        val size = SUB_SIZE * dp
        val goal = d.goalFmt.format(d.goalMin)
        var cx = x
        c.text(goal, cx, base, ArtFont.TEXT, size, p.onBackgroundMuted)
        cx += c.measure(goal, ArtFont.TEXT, size)
        if (d.streak <= 0) return
        val dot = "  ·  "
        val iconSize = size * 1.15f
        val streakW = c.measure(dot, ArtFont.TEXT, size) + iconSize + 2f * dp + c.measure(d.streakText, ArtFont.TEXT, size)
        if (cx - x + streakW > maxW) return
        c.text(dot, cx, base, ArtFont.TEXT, size, p.onBackgroundMuted)
        cx += c.measure(dot, ArtFont.TEXT, size)
        c.text(ICON_FIRE, cx, base + c.capHeight(ArtFont.ICON, iconSize) * 0.08f, ArtFont.ICON, iconSize, p.streak)
        cx += iconSize + 2f * dp
        c.text(d.streakText, cx, base, ArtFont.TEXT, size, p.streak)
    }

    // ───────────────────────── malý (2×2) ─────────────────────────

    private fun drawCompact(c: ArtCanvas, w: Float, h: Float, dp: Float, d: ArtData, p: ArtPalette, cta: Boolean): ArtLayout {
        val pad = 12f * dp
        // s tlačidlom pokračovať pod krúžkom
        val ctaH = min(48f * dp, max(40f * dp, h * 0.24f))
        val tall = cta
        val chipRoom = if (d.streak > 0) 12f * dp else 0f
        val availH = (if (tall) h - ctaH - 3 * pad else h - 2 * pad) - chipRoom
        val hero = min(w - 2 * pad, availH)
        val hx = (w - hero) / 2f
        // krúžok (a tlačidlo) spolu zvislo v strede – bez prázdneho miesta medzi nimi
        val group = hero + chipRoom + (if (tall) pad + ctaH else 0f)
        val hy = (h - group) / 2f
        drawHero(c, hx, hy, hero, dp, d, p)
        if (d.streak > 0 && hero > 96f * dp) {
            // čip série prekrýva spodný okraj krúžku (výrazný „expressive“ detail)
            val chipW = chipWidth(c, d, dp)
            drawStreakChip(c, hx + (hero - chipW) / 2f, hy + hero - 14f * dp, dp, d, p)
        }
        if (tall) {
            val t = hy + hero + chipRoom + pad
            drawCta(c, pad, t, w - pad, t + ctaH, dp, d, p)
            return ArtLayout(pad, t, w - pad, t + ctaH, true)
        }
        return ArtLayout(0f, 0f, 0f, 0f, false)
    }

    // ───────────────────────── časti ─────────────────────────

    /** Nadpis; číslo s jednotkou („12 min“) je zvýraznené farbou priebehu. */
    private fun drawHeadline(c: ArtCanvas, text: String, x: Float, base: Float, size: Float, d: ArtData, p: ArtPalette) {
        val m = Regex("\\d+(\\s+\\S+)?").find(text)
        if (m == null || d.todayMin >= d.goalMin || d.todayMin == 0) {
            c.text(text, x, base, ArtFont.DISPLAY, size, p.onBackground, -0.01f)
            return
        }
        // „Ešte “ + „12 min“ / „12 min“ + „ left“
        var cx = x
        for ((part, color) in listOf(
            text.substring(0, m.range.first) to p.onBackground,
            m.value to p.ring,
            text.substring(m.range.last + 1) to p.onBackground,
        )) {
            if (part.isEmpty()) continue
            c.text(part, cx, base, ArtFont.DISPLAY, size, color, -0.01f)
            cx += c.measure(part, ArtFont.DISPLAY, size, -0.01f)
        }
    }

    private fun headline(d: ArtData): String = when {
        d.todayMin >= d.goalMin -> d.goalDone
        d.todayMin == 0 -> d.letsStart
        else -> d.remainingFmt.format(d.goalMin - d.todayMin)
    }

    /**
     * Hlavný prvok: kruh s vlnitým krúžkom priebehu (Material 3 Expressive) a veľkým číslom minút.
     */
    private fun drawHero(c: ArtCanvas, x: Float, y: Float, size: Float, dp: Float, d: ArtData, p: ArtPalette) {
        val cx = x + size / 2f
        val cy = y + size / 2f
        val done = d.todayMin >= d.goalMin
        // podklad hlavného prvku – kruh v tóne kontajnera
        c.fillCircle(cx, cy, size / 2f, p.heroContainer)

        // vlnitý krúžok priebehu (ako CircularWavyProgressIndicator)
        val stroke = (size * 0.055f).coerceIn(5f * dp, 8f * dp)
        val amp = stroke * 0.36f
        val inset = size * 0.075f
        val ringR = size / 2f - stroke / 2f - amp - inset
        val progress = (d.todayMin.toFloat() / d.goalMin.coerceAtLeast(1)).coerceIn(0f, 1f)
        wavyRing(c, cx, cy, ringR, stroke, amp, progress, if (done) p.ringDone else p.ring, p.ringTrack, done, dp)

        // číslo minút
        val num = d.todayMin.toString()
        val maxNumW = ringR * 1.25f
        var numSize = ringR * 0.92f
        while (numSize > 10f && c.measure(num, ArtFont.DISPLAY, numSize, -0.03f) > maxNumW) numSize *= 0.92f
        val capN = c.capHeight(ArtFont.DISPLAY, numSize)
        val unitSize = max(11f * dp, ringR * 0.2f)
        val capU = c.capHeight(ArtFont.TEXT, unitSize)
        val gap = ringR * 0.1f
        val blockH = capN + gap + capU
        val numBase = cy - blockH / 2f + capN
        val nw = c.measure(num, ArtFont.DISPLAY, numSize, -0.03f)
        c.text(num, cx - nw / 2f, numBase, ArtFont.DISPLAY, numSize, p.onHeroContainer, -0.03f)
        val unit = if (done) "" else d.minUnit
        if (done) {
            val isz = unitSize * 1.9f
            val iw = c.measure(ICON_CHECK, ArtFont.ICON, isz)
            c.text(ICON_CHECK, cx - iw / 2f, numBase + gap * 0.6f + c.capHeight(ArtFont.ICON, isz), ArtFont.ICON, isz, p.ringDone)
        } else {
            val uw = c.measure(unit, ArtFont.TEXT, unitSize, 0.02f)
            c.text(unit, cx - uw / 2f, numBase + gap + capU, ArtFont.TEXT, unitSize, p.onHeroContainer, 0.02f)
        }
    }

    private fun chipWidth(c: ArtCanvas, d: ArtData, dp: Float): Float {
        val ts = 13f * dp
        return 10f * dp + 16f * dp + 4f * dp + c.measure(d.streakText, ArtFont.TEXT, ts) + 12f * dp
    }

    /** Čip série dní: tercárny kontajner, plamienok a „5 dní“. */
    private fun drawStreakChip(c: ArtCanvas, x: Float, y: Float, dp: Float, d: ArtData, p: ArtPalette) {
        val h = 26f * dp
        val w = chipWidth(c, d, dp)
        c.fillRoundRect(x, y, x + w, y + h, h / 2f, p.chip)
        val iconSize = 16f * dp
        val iconBase = y + h / 2f + c.capHeight(ArtFont.ICON, iconSize) / 2f
        c.text(ICON_FIRE, x + 10f * dp, iconBase, ArtFont.ICON, iconSize, p.onChip)
        val ts = 13f * dp
        val tb = y + h / 2f + c.capHeight(ArtFont.TEXT, ts) / 2f
        c.text(d.streakText, x + 10f * dp + iconSize + 4f * dp, tb, ArtFont.TEXT, ts, p.onChip)
    }

    /** Tlačidlo „pokračovať“: plná pilulka s ikonou ▶ v kruhu, názov skladby a strana. */
    private fun drawCta(c: ArtCanvas, l: Float, t: Float, r: Float, b: Float, dp: Float, d: ArtData, p: ArtPalette) {
        val h = b - t
        val narrow = r - l < 160f * dp
        c.fillRoundRect(l, t, r, b, h / 2f, p.cta)
        // ikona v kruhu – sústredná so zaoblením pilulky
        val circleR = min(h / 2f - 6f * dp, (if (narrow) 15f else 18f) * dp)
        // na text nie je miesto → len ikona v strede tlačidla
        val iconOnly = (r - l) - h - circleR - 18f * dp < 48f * dp
        val ccx = if (iconOnly) (l + r) / 2f else l + h / 2f
        val ccy = t + h / 2f
        c.fillCircle(ccx, ccy, circleR, p.onCta)
        val hasPiece = d.pieceTitle != null
        val icon = if (hasPiece) ICON_PLAY else ICON_NOTE
        val iconSize = circleR * 1.25f
        val iw = c.measure(icon, ArtFont.ICON, iconSize)
        c.text(
            icon, ccx - iw / 2f + if (hasPiece) iconSize * 0.03f else 0f,
            ccy + c.capHeight(ArtFont.ICON, iconSize) / 2f, ArtFont.ICON, iconSize, p.cta,
        )

        if (iconOnly) return
        // texty: názov skladby a pod ním strana (dlhý názov na dva riadky)
        val tx = ccx + circleR + (if (narrow) 8f else 10f) * dp
        val maxW = r - (if (narrow) 10f else 14f) * dp - tx
        val ts = min(if (r - l < 200f * dp) 13.5f * dp else 14.5f * dp, h * 0.29f)
        if (!hasPiece) {
            val s = ellipsize(c, d.openLibrary, ArtFont.TEXT, ts, maxW)
            c.text(s, tx, ccy + c.capHeight(ArtFont.TEXT, ts) / 2f, ArtFont.TEXT, ts, p.onCta)
            return
        }
        val fit = ctaTitle(c, d.pieceTitle!!, ts, maxW)
        val capT = c.capHeight(ArtFont.TEXT, fit.size)
        if (fit.lines.size == 2) {
            val lh = fit.size * 1.22f
            val tb = ccy - (lh - capT) / 2f
            c.text(fit.lines[0], tx, tb, ArtFont.TEXT, fit.size, p.onCta)
            c.text(fit.lines[1], tx, tb + lh, ArtFont.TEXT, fit.size, p.onCta)
        } else {
            val ps = 12f * dp
            val capP = c.capHeight(ArtFont.TEXT, ps)
            val gap = 6f * dp
            val tb = ccy - (capT + gap + capP) / 2f + capT
            c.text(fit.lines[0], tx, tb, ArtFont.TEXT, fit.size, p.onCta)
            val page = d.pageFmt.format(d.piecePage)
            if (c.measure(page, ArtFont.TEXT, ps) <= maxW) {
                c.text(page, tx, tb + gap + capP, ArtFont.TEXT, ps, (p.onCta and 0x00FFFFFF) or (0xB8 shl 24))
            }
        }
    }

    private class Fit(val lines: List<String>, val size: Float)

    /**
     * Názov skladby do tlačidla tak, aby sa dal prečítať: celý → bez dovetku („, op. 27 č. 2“) →
     * o niečo menším písmom → na dva riadky (vtedy bez strany) → skrátený „…“.
     */
    private fun ctaTitle(c: ArtCanvas, title: String, size: Float, maxW: Float): Fit {
        val short = shortTitle(title)
        val small = size * 0.92f
        for (s in floatArrayOf(size, small)) {
            if (c.measure(title, ArtFont.TEXT, s) <= maxW) return Fit(listOf(title), s)
            if (short != title && c.measure(short, ArtFont.TEXT, s) <= maxW) return Fit(listOf(short), s)
        }
        val words = short.split(' ').filter { it.isNotEmpty() }
        var n = 0
        while (n < words.size && c.measure(words.take(n + 1).joinToString(" "), ArtFont.TEXT, small) <= maxW) n++
        if (n in 1 until words.size) {
            val second = ellipsize(c, words.drop(n).joinToString(" "), ArtFont.TEXT, small, maxW)
            return Fit(listOf(words.take(n).joinToString(" "), second), small)
        }
        return Fit(listOf(ellipsize(c, short, ArtFont.TEXT, size, maxW)), size)
    }

    /** „Mesačná sonáta, op. 27 č. 2“ → „Mesačná sonáta“ (bez dovetku za čiarkou, zátvorkou, pomlčkou). */
    private fun shortTitle(t: String): String {
        val cut = listOf(", ", " (", " – ", " - ", ": ").map { t.indexOf(it) }.filter { it >= 3 }.minOrNull()
        return if (cut != null) t.substring(0, cut).trim() else t
    }

    // ───────────────────────── tvary ─────────────────────────

    /**
     * Vlnitý kruhový indikátor (M3 Expressive): vlnitý aktívny oblúk so zaoblenými koncami,
     * rovná dráha s medzerou na oboch koncoch. Splnené = hladký celý kruh.
     */
    private fun wavyRing(
        c: ArtCanvas, cx: Float, cy: Float, r: Float, stroke: Float, amp: Float,
        progress: Float, color: Int, track: Int, done: Boolean, dp: Float,
    ) {
        val waves = max(6, ((2 * PI * r) / (stroke * 4.2f)).roundToInt())
        if (done) {
            // splnené: celý vlnitý kruh
            arc(c, cx, cy, r, -90f, 360f, stroke, color, amp, waves, true)
            return
        }
        val gapDeg = Math.toDegrees(((stroke + 5f * dp) / r).toDouble()).toFloat()
        val sweep = 360f * progress
        if (progress <= 0.001f) {
            arc(c, cx, cy, r, 0f, 360f, stroke, track, 0f, 0, true)
            return
        }
        val trackSweep = 360f - sweep - 2 * gapDeg
        if (trackSweep > 1f) arc(c, cx, cy, r, -90f + sweep + gapDeg, trackSweep, stroke, track, 0f, 0, false)
        arc(c, cx, cy, r, -90f, max(sweep, 0.5f), stroke, color, amp, waves, false)
    }

    private fun arc(
        c: ArtCanvas, cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float,
        stroke: Float, color: Int, amp: Float, waves: Int, closed: Boolean,
    ) {
        val n = max(8, (sweepDeg * 3).roundToInt())
        val cnt = if (closed) n else n + 1
        val xs = FloatArray(cnt)
        val ys = FloatArray(cnt)
        for (i in 0 until cnt) {
            val deg = startDeg + sweepDeg * i / n
            val th = Math.toRadians(deg.toDouble())
            val rr = r + amp * sin(waves * (th + PI / 2)).toFloat()
            xs[i] = cx + rr * cos(th).toFloat()
            ys[i] = cy + rr * sin(th).toFloat()
        }
        c.strokePolyline(xs, ys, stroke, color, closed)
    }

    // ───────────────────────── text ─────────────────────────

    private fun fitSize(c: ArtCanvas, s: String, font: ArtFont, start: Float, maxW: Float, minSize: Float, tracking: Float): Float {
        var size = start
        while (size > minSize && c.measure(s, font, size, tracking) > maxW) size *= 0.94f
        return size
    }

    fun ellipsize(c: ArtCanvas, s: String, font: ArtFont, size: Float, maxW: Float): String {
        if (c.measure(s, font, size) <= maxW) return s
        var lo = 0
        var hi = s.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (c.measure(s.take(mid).trimEnd() + "…", font, size) <= maxW) lo = mid else hi = mid - 1
        }
        return s.take(lo).trimEnd() + "…"
    }
}
