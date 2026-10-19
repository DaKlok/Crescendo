package com.daklok.crescendo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.daklok.crescendo.data.backup.await
import com.daklok.crescendo.pdf.closeLocked
import com.daklok.crescendo.pdf.openPdfRenderer
import com.daklok.crescendo.pdf.withPage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import kotlin.math.abs

/**
 * Rozpoznanie názvu a skladateľa z prvej strany nôt (ML Kit, všetko v zariadení).
 * Vykreslí hornú časť prvej strany, prečíta riadky textu a odhadne: názov = najväčší text hore
 * (aj s „Op. 9 No. 2“ pod ním), skladateľ = známe meno, „Music by …“, roky života alebo meno vpravo nad notami.
 */
object TitleRecognizer {
    data class Result(val title: String?, val composer: String?)

    /** Riadok textu s polohou (v pixeloch vykresleného obrázka). */
    data class Line(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val height: Int get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
    }

    suspend fun recognize(context: Context, pdf: File): Result? = runCatching {
        val bmp = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { renderTop(it) }
        } ?: return null
        read(bmp)
    }.getOrNull()

    suspend fun recognize(context: Context, uri: Uri): Result? = runCatching {
        val bmp = withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { renderTop(it) }
        } ?: return null
        read(bmp)
    }.getOrNull()

    private suspend fun read(bmp: Bitmap): Result? {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text = try {
                recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
            } finally {
                recognizer.close()
            }
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                val r = l.boundingBox ?: return@mapNotNull null
                Line(l.text, r.left, r.top, r.right, r.bottom)
            }
            return withContext(Dispatchers.Default) { analyze(lines, bmp.width, bmp.height) }
        } finally {
            bmp.recycle()
        }
    }

    /** Horných 45 % prvej strany na šírku 1600 px (nadpis a skladateľ bývajú hore). */
    private fun renderTop(fd: ParcelFileDescriptor): Bitmap? {
        val renderer = openPdfRenderer(fd)
        try {
            if (renderer.pageCount == 0) return null
            return renderer.withPage(0) { page ->
                val w = 1600
                val scale = w.toFloat() / page.width
                val h = (page.height * scale * 0.45f).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } finally {
            renderer.closeLocked()
        }
    }

    /** Odhad názvu a skladateľa z riadkov textu (oddelené kvôli testom). */
    fun analyze(raw: List<Line>, width: Int, height: Int): Result? {
        val lines = raw.map { it.copy(text = clean(it.text)) }.filter { it.text.isNotEmpty() && usable(it.text) }
        if (lines.isEmpty()) return null

        // ── názov: najväčší text v hornej časti, radšej v strede; riadok, kde je len meno skladateľa, nie ──
        val top = lines.filter { it.top < height * 0.75f }
        val title0 = top.sortedWith(compareByDescending<Line> { it.height }.thenBy { abs(it.centerX - width / 2f) })
            .firstOrNull { knownComposer(it.text)?.second != true && it.text.count { c -> c.isLetter() } >= 2 }
        val titleParts = ArrayList<Line>()
        var title: String? = null
        if (title0 != null) {
            titleParts.add(title0)
            // druhý riadok nadpisu v rovnakej veľkosti tesne pod
            top.filter { it !== title0 && it.top > title0.top && it.top - title0.bottom < title0.height * 0.9f && it.height >= title0.height * 0.8f }
                .minByOrNull { it.top }
                ?.takeIf { knownComposer(it.text) == null }
                ?.let { titleParts.add(it) }
            var t = titleParts.joinToString(" ") { titleCase(it.text) }
            // „Op. 9 No. 2“ / „BWV 846“ pod nadpisom
            val below = titleParts.last()
            top.filter { it !in titleParts && it.top >= below.bottom - 4 && it.top - below.bottom < below.height * 1.6f }
                .minByOrNull { it.top }
                ?.takeIf { OPUS.containsMatchIn(it.text) && it.text.length <= 40 }
                ?.let {
                    titleParts.add(it)
                    t += " " + titleCase(it.text)
                }
            title = t.trim().trim(',', '.', ';', ':', '-')
        }
        val rest = lines.filter { it !in titleParts }

        // ── skladateľ ──
        // 1) známe meno
        var composer: String? = rest.sortedBy { it.top }
            .firstNotNullOfOrNull { l -> if (l.text.split(' ').size > 7) null else knownComposer(l.text)?.first }
        // 2) „Music by …“, „Hudba: …“
        if (composer == null) {
            composer = rest.firstNotNullOfOrNull { l ->
                val m = BY.find(l.text) ?: return@firstNotNullOfOrNull null
                cleanName(l.text.substring(m.range.last + 1)).takeIf { it.length >= 3 }
            }
        }
        // 3) roky života „(1810-1849)“
        if (composer == null) {
            composer = rest.firstNotNullOfOrNull { l ->
                if (!YEARS.containsMatchIn(l.text)) return@firstNotNullOfOrNull null
                cleanName(YEARS.replace(l.text, " ")).takeIf { it.length >= 3 && it.any { c -> c.isLetter() } }
            }
        }
        // 4) meno vpravo nad notami (bežné rozloženie)
        if (composer == null && title0 != null) {
            composer = rest
                .filter { it.left > width * 0.55f && it.top > title0.top && it.height < title0.height }
                .filter { l ->
                    val w = l.text.split(' ').filter { it.isNotBlank() }
                    w.size in 1..5 && l.text.none { it.isDigit() } && w.all { it.first().isUpperCase() }
                }
                .minByOrNull { it.top }
                ?.let { cleanName(it.text) }
        }

        val c = composer?.takeIf { it.length in 3..60 }?.let { nameCase(it) }
        val tt = title?.takeIf { it.length in 2..90 }
        if (tt == null && c == null) return null
        return Result(tt, c)
    }

    /**
     * Či prepísať doterajší názov rozpoznaným: áno, ak je doterajší len technický názov súboru
     * (sken, IMSLP, čísla, dátum, podčiarkovníky) a rozpoznaný sa od neho líši.
     */
    fun shouldReplaceTitle(current: String, recognized: String, scanDefault: String? = null): Boolean {
        val c = current.trim()
        if (c.isEmpty()) return true
        if (c.equals(recognized, ignoreCase = true)) return false
        if (scanDefault != null && c.startsWith(scanDefault.substringBefore("%").trim(), ignoreCase = true)) return true
        return GENERIC.any { it.containsMatchIn(c) }
    }

    // ───────── pomocné ─────────

    private val BY = Regex(
        "(?i)\\b(music\\s+by|composed\\s+by|words\\s+and\\s+music\\s+by|music:|composer:|comp\\.|hudba:|hudba\\s+a\\s+text:|text\\s+a\\s+hudba:|musik:|musique:|melodie:|melódia:|zložil:|skomponoval:)\\s*",
    )
    private val YEARS = Regex("\\(?\\s*\\b1[4-9]\\d{2}\\s*[-–—/]\\s*(1[5-9]|20)\\d{2}\\b\\s*\\)?")
    private val OPUS = Regex("(?i)^(op\\.?|opus|no\\.|nr\\.|č\\.|bwv|kv|k\\.|hob|woo|d\\.\\s*\\d|rv|hwv|s\\.\\s*\\d)|\\bop\\.\\s*\\d")
    private val GENERIC = listOf(
        Regex("(?i)^(scan|sken|img|image|dokument|document|doc|untitled|bez názvu|noty|sheet|score|file|pdf|camscanner|adobe scan|scanned|nová skladba|new piece)\\b"),
        Regex("(?i)imslp"),
        Regex("^[\\d\\s_\\-.()]+$"),
        Regex("\\d{4}[-_.]\\d{2}[-_.]\\d{2}"),
        Regex("^[^\\s]*_[^\\s]*$"),
        Regex("^[^\\s]+-[^\\s]+-[^\\s]+$"),
    )

    /** Slová, ktoré nie sú názov ani skladateľ (tempo, nástroj, vydavateľ, dynamika…). */
    private val SKIP = Regex(
        "(?i)^(allegro|allegretto|andante|andantino|adagio|adagietto|largo|larghetto|lento|grave|moderato|presto|prestissimo|vivace|vivo|maestoso|" +
            "tempo|rubato|cantabile|dolce|espressivo|con moto|a tempo|rit\\.|rall\\.|accel|moderately|moderate|slowly|slow|fast|lively|brightly|gently|" +
            "swing|ballad|freely|rock|langsam|mäßig|ruhig|lebhaft|schnell|bewegt|sehr|mierne|pomaly|rýchlo|zvolna|" +
            "piano|pianoforte|klavier|klavír|violin|violino|viola|cello|violoncello|flute|flauto|flauta|oboe|clarinet|klarinet|bassoon|horn|trumpet|trombone|" +
            "guitar|gitara|gitarre|voice|soprano|alto|tenor|bass|baritone|organ|orgel|organo|harp|vocal|choir|satb|" +
            "page|strana|seite|cresc\\.?|dim\\.?)\\b",
    )
    private val DYNAMICS = Regex("(?i)^(p|pp|ppp|f|ff|fff|mf|mp|sf|sfz|fp)$")
    private val PUBLISHER = Regex(
        "(?i)(©|copyright|all rights reserved|public domain|imslp|mutopia|www\\.|https?://|\\.com\\b|\\.org\\b|\\.net\\b|edition|verlag|publishing|publisher|" +
            "printed|engraved|typeset|lilypond|musescore|sibelius|finale|creative commons|licen[cs]e|reproduced|urtext|henle|peters|schott|breitkopf|bärenreiter|durand|ricordi|schirmer)",
    )

    private fun usable(t: String): Boolean {
        val letters = t.count { it.isLetter() }
        // „Op. 9 No. 2“ má málo písmen, ale k názvu patrí
        if (OPUS.containsMatchIn(t) && letters >= 2 && t.length <= 40) return !PUBLISHER.containsMatchIn(t)
        if (letters < 2 || letters < t.length * 0.45f) return false
        if (DYNAMICS.matches(t)) return false
        if (PUBLISHER.containsMatchIn(t)) return false
        if (SKIP.containsMatchIn(t) && t.split(' ').size <= 4) return false
        if (t.contains('=') || t.contains('♩')) return false
        return true
    }

    private fun clean(t: String): String =
        t.replace('\n', ' ').replace(Regex("[|_~*#]+"), " ").replace(Regex("\\s+"), " ").trim().trim('-', '.', ',', ':', ';')

    private fun cleanName(t: String): String =
        t.replace(Regex("\\(.*?\\)"), " ").replace(Regex("[\\[\\]]"), " ")
            .replace(Regex("(?i)\\b(arr\\.?|arranged by|úprava|upravil).*$"), " ")
            .replace(Regex("\\s+"), " ").trim().trim(',', '.', ';', ':', '-', '–', '—')

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    /**
     * Známe meno v riadku: (meno, či je v riadku len meno). Iniciály alebo krstné meno, ktoré sedia
     * so zoznamom („F. Chopin“, „J. S. Bach“), dajú celé meno zo zoznamu; inak meno, ako je napísané („C. P. E. Bach“).
     */
    private fun knownComposer(t: String): Pair<String, Boolean>? {
        val text = cleanName(BY.replace(YEARS.replace(t, " "), " "))
        val words = text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
        val folded = words.map { fold(it.trim('.', ',', ';', ':', '(', ')')) }
        for ((i, w) in folded.withIndex()) {
            val full = COMPOSERS[w] ?: continue
            // „Field“, „Sor“ a pod. len ako meno s veľkým písmenom
            if (!words[i].first().isUpperCase()) continue
            val others = folded.filterIndexed { j, _ -> j != i }.filter { it.isNotEmpty() }
            val canon = full.split(' ').dropLast(1).map { fold(it) }
            val fits = others.size <= canon.size && others.withIndex().all { (k, o) ->
                val c = canon.getOrNull(k) ?: return@all false
                if (o.length == 1) c.startsWith(o) else c == o
            }
            return if (fits) full to true else text to (words.size <= 4)
        }
        return null
    }

    private val SMALL = setOf(
        "a", "an", "and", "the", "of", "in", "on", "for", "to", "de", "la", "le", "les", "des", "du", "et", "und", "der", "die", "das",
        "von", "van", "zu", "im", "am", "i", "z", "zo", "v", "vo", "na", "pre", "do", "od", "za", "s", "so", "di", "da", "del", "e",
    )
    private val ROMAN = Regex("^(?=[IVXLC]+$)C{0,3}(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$")
    private val KEEP_UPPER = setOf("BWV", "KV", "HWV", "RV", "TWV", "WOO", "HOB", "SZ", "BB", "D", "K", "S", "WTC")

    /** NADPIS VEĽKÝMI → „Nadpis Veľkými“ (rímske čísla a označenia diel ostanú). */
    fun titleCase(t: String): String {
        val letters = t.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return t
        return t.split(' ').mapIndexed { i, w ->
            val core = w.trim('.', ',', '(', ')', ':', ';', '"', '\'', '„', '“')
            when {
                core.isEmpty() -> w
                ROMAN.matches(core) -> w
                core in KEEP_UPPER -> w
                core.equals("OP", true) -> w.replace(core, "Op")
                core.equals("NO", true) -> w.replace(core, "No")
                i > 0 && core.lowercase() in SMALL -> w.lowercase()
                else -> w.lowercase().replaceFirstChar { it.titlecase() }.let { cap ->
                    // „SAINT-SAËNS“ → „Saint-Saëns“
                    cap.split('-').joinToString("-") { p -> p.replaceFirstChar { c -> c.titlecase() } }
                }
            }
        }.joinToString(" ")
    }

    /** MENO VEĽKÝMI → „Meno Veľkými“ (van, von, de ostanú malé). */
    private fun nameCase(t: String): String {
        val letters = t.filter { it.isLetter() }
        if (letters.any { it.isLowerCase() }) return t
        return t.split(' ').mapIndexed { i, w ->
            val low = w.lowercase()
            if (i > 0 && low in setOf("van", "von", "de", "da", "di", "del", "la", "le")) low
            else low.split('-').joinToString("-") { p -> p.replaceFirstChar { it.titlecase() } }
        }.joinToString(" ")
    }

    /** Priezviská známych skladateľov (bez diakritiky, malými) → celé meno. */
    private val COMPOSERS: Map<String, String> = buildMap {
        fun add(full: String, vararg keys: String) = keys.forEach { put(it, full) }
        add("Johann Sebastian Bach", "bach")
        add("Ludwig van Beethoven", "beethoven")
        add("Wolfgang Amadeus Mozart", "mozart")
        add("Frédéric Chopin", "chopin")
        add("Franz Liszt", "liszt")
        add("Franz Schubert", "schubert")
        add("Robert Schumann", "schumann")
        add("Johannes Brahms", "brahms")
        add("Joseph Haydn", "haydn")
        add("Antonio Vivaldi", "vivaldi")
        add("Claude Debussy", "debussy")
        add("Maurice Ravel", "ravel")
        add("Erik Satie", "satie")
        add("Edvard Grieg", "grieg")
        add("Bedřich Smetana", "smetana")
        add("Antonín Dvořák", "dvorak")
        add("Leoš Janáček", "janacek")
        add("Bohuslav Martinů", "martinu")
        add("Felix Mendelssohn", "mendelssohn")
        add("Muzio Clementi", "clementi")
        add("Carl Czerny", "czerny")
        add("Friedrich Burgmüller", "burgmuller", "burgmueller")
        add("Domenico Scarlatti", "scarlatti")
        add("Georg Philipp Telemann", "telemann")
        add("Henry Purcell", "purcell")
        add("Béla Bartók", "bartok")
        add("Isaac Albéniz", "albeniz")
        add("Enrique Granados", "granados")
        add("Francisco Tárrega", "tarrega")
        add("Mauro Giuliani", "giuliani")
        add("Matteo Carcassi", "carcassi")
        add("Ferdinando Carulli", "carulli")
        add("François Couperin", "couperin")
        add("Jean-Philippe Rameau", "rameau")
        add("Johann Pachelbel", "pachelbel")
        add("Arcangelo Corelli", "corelli")
        add("Tomaso Albinoni", "albinoni")
        add("Luigi Boccherini", "boccherini")
        add("Anton Diabelli", "diabelli")
        add("Friedrich Kuhlau", "kuhlau")
        add("Johann Nepomuk Hummel", "hummel")
        add("Carl Maria von Weber", "weber")
        add("Richard Wagner", "wagner")
        add("Anton Bruckner", "bruckner")
        add("Gustav Mahler", "mahler")
        add("César Franck", "franck")
        add("Charles Gounod", "gounod")
        add("Jules Massenet", "massenet")
        add("Léo Delibes", "delibes")
        add("Alexander Borodin", "borodin")
        add("Mikhail Glinka", "glinka")
        add("Nikolai Rimsky-Korsakov", "rimsky-korsakov", "rimskij-korsakov")
        add("Pyotr Ilyich Tchaikovsky", "tchaikovsky", "tschaikowsky", "tchaikowsky", "cajkovskij", "czajkowski")
        add("Sergei Rachmaninoff", "rachmaninoff", "rachmaninov", "rachmaninow")
        add("Modest Mussorgsky", "mussorgsky", "moussorgsky", "musorgskij")
        add("Sergei Prokofiev", "prokofiev", "prokofieff", "prokofiew")
        add("Dmitri Shostakovich", "shostakovich", "schostakowitsch", "sostakovic")
        add("Igor Stravinsky", "stravinsky", "strawinsky")
        add("Alexander Scriabin", "scriabin", "skrjabin")
        add("Dmitry Kabalevsky", "kabalevsky", "kabalewski")
        add("Aram Khachaturian", "khachaturian", "chacaturjan")
        add("Cornelius Gurlitt", "gurlitt")
        add("Stephen Heller", "heller")
        add("Jean-Baptiste Duvernoy", "duvernoy")
        add("Scott Joplin", "joplin")
        add("George Gershwin", "gershwin")
        add("Edward Elgar", "elgar")
        add("Gustav Holst", "holst")
        add("Giuseppe Verdi", "verdi")
        add("Giacomo Puccini", "puccini")
        add("Gioachino Rossini", "rossini")
        add("Georges Bizet", "bizet")
        add("Jacques Offenbach", "offenbach")
        add("Niccolò Paganini", "paganini")
        add("Jean Sibelius", "sibelius")
        add("John Dowland", "dowland")
        add("William Byrd", "byrd")
        add("Girolamo Frescobaldi", "frescobaldi")
        add("Dieterich Buxtehude", "buxtehude")
        add("Claudio Monteverdi", "monteverdi")
        add("Giovanni Pierluigi da Palestrina", "palestrina")
        add("Thomas Tallis", "tallis")
        add("Georg Friedrich Händel", "handel", "haendel")
        add("Gabriel Fauré", "faure")
        add("Camille Saint-Saëns", "saint-saens")
        add("Astor Piazzolla", "piazzolla")
        add("Heitor Villa-Lobos", "villa-lobos")
        add("Agustín Barrios", "barrios")
        add("Leo Brouwer", "brouwer")
        add("Francis Poulenc", "poulenc")
        add("Olivier Messiaen", "messiaen")
        add("Paul Hindemith", "hindemith")
        add("Max Reger", "reger")
        add("Ennio Morricone", "morricone")
        add("Ludovico Einaudi", "einaudi")
        add("Yann Tiersen", "tiersen")
        add("Yiruma", "yiruma")
        add("Joe Hisaishi", "hisaishi")
        add("Hans Zimmer", "zimmer")
        add("Eugen Suchoň", "suchon")
        add("Ján Cikker", "cikker")
        add("John Field", "field")
        add("Fernando Sor", "sor")
        add("Johann Strauss", "strauss")
        add("Christoph Willibald Gluck", "gluck")
    }
}
