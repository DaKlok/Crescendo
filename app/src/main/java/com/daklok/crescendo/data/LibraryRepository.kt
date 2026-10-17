package com.daklok.crescendo.data

import com.daklok.crescendo.pdf.openPdfRenderer
import com.daklok.crescendo.pdf.closeLocked
import com.daklok.crescendo.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.daklok.crescendo.pdf.withPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument

class LibraryRepository(
    private val context: Context,
    private val dao: PieceDao,
    private val folderDao: FolderDao,
    private val setlistDao: SetlistDao,
    private val practiceDao: PracticeDao,
) {
    private val pdfDir = File(context.filesDir, "pdfs").apply { mkdirs() }
    private val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun observePieces(): Flow<List<PieceEntity>> = dao.observeAll()

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    // ---- priečinky ----

    suspend fun createFolder(name: String, parentId: Long?): Long =
        folderDao.insert(FolderEntity(name = name, parentId = parentId))

    suspend fun renameFolder(id: Long, name: String) = folderDao.rename(id, name)

    suspend fun setFolderColor(id: Long, color: Int?) = folderDao.setColor(id, color)

    suspend fun moveFolder(id: Long, parentId: Long?) = folderDao.setParent(id, parentId)

    /** Zruší priečinok; jeho skladby aj podpriečinky sa presunú o úroveň vyššie (nič sa nestratí). */
    suspend fun deleteFolder(folder: FolderEntity) {
        dao.reparent(folder.id, folder.parentId)
        folderDao.reparentChildren(folder.id, folder.parentId)
        folderDao.delete(folder.id)
    }

    suspend fun movePiece(id: Long, folderId: Long?) = dao.moveToFolder(id, folderId)

    suspend fun getFolder(id: Long): FolderEntity? = folderDao.getById(id)

    suspend fun getPiece(id: Long): PieceEntity? = dao.getById(id)

    fun pdfFile(piece: PieceEntity): File = File(pdfDir, piece.fileName)

    fun thumbFile(piece: PieceEntity): File? = piece.thumbName?.let { File(thumbDir, it) }

    /**
     * Skopíruje PDF do interného úložiska, vytvorí náhľad prvej strany a uloží záznam.
     * Vráti ID novej skladby, alebo chybu (nie je to PDF / je chránené heslom...).
     */
    suspend fun importPdf(uri: Uri, folderId: Long? = null, title: String? = null): Result<Long> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val target = File(pdfDir, "$id.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            } ?: return@withContext Result.failure(IllegalStateException("Cannot open file"))

            var pages = 0
            var thumbName: String? = null
            ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = openPdfRenderer(fd)
                try {
                    pages = renderer.pageCount
                    if (pages > 0) {
                        thumbName = "$id.png"
                        renderThumb(renderer, File(thumbDir, "$id.png"))
                    }
                } finally {
                    renderer.closeLocked()
                }
            }
            if (pages == 0) {
                target.delete()
                return@withContext Result.failure(IllegalStateException("PDF has no pages"))
            }

            val piece = PieceEntity(
                title = title?.takeIf { it.isNotBlank() }
                    ?: displayName(uri)?.removeSuffix(".pdf")?.removeSuffix(".PDF")
                    ?: context.getString(R.string.piece_default_title),
                fileName = target.name,
                thumbName = thumbName,
                pageCount = pages,
                folderId = folderId,
            )
            Result.success(dao.insert(piece))
        } catch (e: Exception) {
            target.delete()
            Result.failure(e)
        }
    }

    /**
     * Doplní strany z ďalšieho PDF do existujúcej skladby (na koniec alebo na začiatok).
     * PDF sa spája bez straty kvality (PdfBox). Pri vložení na začiatok sa poznámky
     * aj posledná strana posunú, aby ostali na správnych stranách. Vráti počet pridaných strán.
     */
    suspend fun appendPdf(piece: PieceEntity, uri: Uri, atStart: Boolean, annotations: AnnotationStore): Result<Int> =
        withContext(Dispatchers.IO) {
            val target = pdfFile(piece)
            val tmp = File(pdfDir, piece.fileName + ".merge")
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                var added = 0
                var total = 0
                PDDocument.load(target).also { it.isAllSecurityToBeRemoved = true }.use { existing ->
                    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open file")
                    input.use { stream ->
                        PDDocument.load(stream).also { it.isAllSecurityToBeRemoved = true }.use { extra ->
                            added = extra.numberOfPages
                            if (added == 0) error("PDF has no pages")
                            val merger = PDFMergerUtility()
                            if (atStart) {
                                merger.appendDocument(extra, existing)
                                extra.save(tmp)
                            } else {
                                merger.appendDocument(existing, extra)
                                existing.save(tmp)
                            }
                        }
                    }
                }
                // nové PDF over (otvorí ho aj Android PdfRenderer) a až potom nahraď pôvodné
                ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val r = openPdfRenderer(fd)
                    try {
                        total = r.pageCount
                        if (atStart) {
                            // nový názov náhľadu, aby sa v knižnici hneď prekreslil
                            val thumbName = "${piece.fileName.removeSuffix(".pdf")}_${System.currentTimeMillis()}.png"
                            renderThumb(r, File(thumbDir, thumbName))
                            piece.thumbName?.let { File(thumbDir, it).delete() }
                            dao.setThumb(piece.id, thumbName)
                        }
                    } finally {
                        r.closeLocked()
                    }
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                dao.setPageCount(piece.id, total)
                if (atStart) {
                    dao.shiftLastPage(piece.id, added)
                    annotations.shiftPages(piece.id, added)
                }
                Result.success(added)
            } catch (e: Throwable) {
                tmp.delete()
                Result.failure(e)
            }
        }

    /** Nahradí PDF skladby novým (overeným) súborom, aktualizuje počet strán a náhľad. */
    private suspend fun replacePdf(piece: PieceEntity, tmp: File): Int {
        var total = 0
        ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val r = openPdfRenderer(fd)
            try {
                total = r.pageCount
                if (total == 0) error("empty")
                val thumbName = "${piece.fileName.removeSuffix(".pdf")}_${System.currentTimeMillis()}.png"
                renderThumb(r, File(thumbDir, thumbName))
                piece.thumbName?.let { File(thumbDir, it).delete() }
                dao.setThumb(piece.id, thumbName)
            } finally {
                r.closeLocked()
            }
        }
        val target = pdfFile(piece)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        dao.setPageCount(piece.id, total)
        return total
    }

    /**
     * Úprava strán: [pages] = nové poradie pôvodných strán (indexy od 0) a otočenie každej o násobok 90°.
     * Vynechané strany sa zmažú. Poznámky idú so stranami; na otočených stranách sa zmažú.
     */
    suspend fun editPages(
        piece: PieceEntity,
        pages: List<Pair<Int, Int>>,
        annotations: AnnotationStore,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tmp = File(pdfDir, piece.fileName + ".edit")
        try {
            if (pages.isEmpty()) error("no pages")
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(pdfFile(piece)).also { it.isAllSecurityToBeRemoved = true }.use { src ->
                PDDocument().use { out ->
                    pages.forEach { (index, rot) ->
                        val page = src.getPage(index)
                        if (rot % 360 != 0) page.rotation = ((page.rotation + rot) % 360 + 360) % 360
                        out.importPage(page)
                    }
                    out.save(tmp)
                }
            }
            replacePdf(piece, tmp)
            // poznámky podľa nového poradia
            val notes = annotations.load(piece.id)
            val newPages = HashMap<Int, List<InkStroke>>()
            val newStamps = HashMap<Int, List<Stamp>>()
            pages.forEachIndexed { newIndex, (old, rot) ->
                if (rot % 360 == 0) {
                    notes.pages[old]?.let { newPages[newIndex] = it }
                    notes.stamps[old]?.let { newStamps[newIndex] = it }
                }
            }
            annotations.save(piece.id, PieceNotes(newPages, newStamps))
            val newLast = pages.indexOfFirst { it.first == piece.lastPage }.coerceAtLeast(0)
            dao.saveProgress(piece.id, newLast, piece.lastOpenedAt)
            Result.success(Unit)
        } catch (e: Throwable) {
            tmp.delete()
            Result.failure(e)
        }
    }

    /**
     * Rozdelí spevník na samostatné skladby. [parts] = (názov, od, do) – strany od 0, vrátane.
     * Poznámky sa skopírujú k novým skladbám. Ak [keepOriginal] = false, pôvodná skladba sa zmaže.
     */
    suspend fun splitPiece(
        piece: PieceEntity,
        parts: List<Triple<String, Int, Int>>,
        keepOriginal: Boolean,
        annotations: AnnotationStore,
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val notes = annotations.load(piece.id)
            var created = 0
            PDDocument.load(pdfFile(piece)).also { it.isAllSecurityToBeRemoved = true }.use { src ->
                for ((name, from, to) in parts) {
                    if (from < 0 || to >= src.numberOfPages || from > to) continue
                    val id = UUID.randomUUID().toString()
                    val target = File(pdfDir, "$id.pdf")
                    PDDocument().use { out ->
                        for (i in from..to) out.importPage(src.getPage(i))
                        out.save(target)
                    }
                    var thumbName: String? = null
                    ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        val r = openPdfRenderer(fd)
                        try {
                            thumbName = "$id.png"
                            renderThumb(r, File(thumbDir, "$id.png"))
                        } finally {
                            r.closeLocked()
                        }
                    }
                    val newId = dao.insert(
                        PieceEntity(
                            title = name.ifBlank { piece.title },
                            fileName = target.name,
                            thumbName = thumbName,
                            pageCount = to - from + 1,
                            folderId = piece.folderId,
                            composer = piece.composer,
                            instrument = piece.instrument,
                            tags = piece.tags,
                        ),
                    )
                    annotations.save(
                        newId,
                        PieceNotes(
                            pages = notes.pages.filterKeys { it in from..to }.mapKeys { it.key - from },
                            stamps = notes.stamps.filterKeys { it in from..to }.mapKeys { it.key - from },
                        ),
                    )
                    created++
                }
            }
            if (created > 0 && !keepOriginal) delete(piece)
            Result.success(created)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    // ───────────── zdieľanie skladby s poznámkami (.crescendo) ─────────────

    /** Zbalí skladbu (PDF, poznámky, údaje, referenčnú nahrávku) do jedného súboru. */
    suspend fun exportPackage(piece: PieceEntity, out: File): Unit = withContext(Dispatchers.IO) {
        java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zip ->
            fun put(name: String, file: File) {
                if (!file.exists()) return
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val meta = org.json.JSONObject()
                .put("format", "crescendo-piece")
                .put("version", 1)
                .put("title", piece.title)
                .put("composer", piece.composer)
                .put("key", piece.keySignature)
                .put("instrument", piece.instrument)
                .put("difficulty", piece.difficulty)
                .put("tags", piece.tags)
            zip.putNextEntry(java.util.zip.ZipEntry("piece.json"))
            zip.write(meta.toString().toByteArray())
            zip.closeEntry()
            put("music.pdf", pdfFile(piece))
            put("notes.json", File(context.filesDir, "annotations/${piece.id}.json"))
            PieceAudio(context).reference(piece.id)?.let { put("audio." + it.extension, it) }
        }
    }

    /** Je súbor balík Crescenda (ZIP) namiesto PDF? */
    fun isPackage(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(2)
            input.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        } ?: false
    }.getOrDefault(false)

    /** Naimportuje balík .crescendo ako novú skladbu (aj s poznámkami a nahrávkou). */
    suspend fun importPackage(uri: Uri, folderId: Long?): Result<Long> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "pkg_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val root = dir.canonicalFile
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.util.zip.ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.isDirectory) continue
                        val f = File(root, e.name).canonicalFile
                        if (!f.path.startsWith(root.path)) continue // ochrana pred „../“
                        f.outputStream().use { zip.copyTo(it) }
                    }
                }
            } ?: error("Cannot open file")
            val meta = org.json.JSONObject(File(root, "piece.json").readText())
            if (meta.optString("format") != "crescendo-piece") error("Not a Crescendo piece")
            val pdf = File(root, "music.pdf")
            val id = importPdf(Uri.fromFile(pdf), folderId, meta.optString("title")).getOrThrow()
            val piece = dao.getById(id) ?: error("missing")
            updateDetails(
                piece.copy(
                    composer = meta.optString("composer"),
                    keySignature = meta.optString("key"),
                    instrument = meta.optString("instrument"),
                    difficulty = meta.optInt("difficulty"),
                    tags = meta.optString("tags"),
                ),
            )
            File(root, "notes.json").takeIf { it.exists() }?.copyTo(File(context.filesDir, "annotations/$id.json"), overwrite = true)
            root.listFiles()?.firstOrNull { it.name.startsWith("audio.") }?.let { a ->
                PieceAudio(context).addFile(id, a, piece.title.take(60))
            }
            Result.success(id)
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            dir.deleteRecursively()
        }
    }

    suspend fun delete(piece: PieceEntity) = withContext(Dispatchers.IO) {
        dao.delete(piece)
        setlistDao.deleteItemsForPiece(piece.id)
        practiceDao.deleteForPiece(piece.id)
        pdfFile(piece).delete()
        thumbFile(piece)?.delete()
        File(context.filesDir, "annotations/${piece.id}.json").delete()
        PieceAudio(context).deleteAll(piece.id)
    }

    suspend fun rename(id: Long, title: String) = dao.rename(id, title)

    suspend fun updateDetails(piece: PieceEntity) = dao.updateDetails(
        piece.id, piece.title, piece.composer, piece.keySignature, piece.instrument, piece.difficulty, piece.tags,
    )

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun saveProgress(id: Long, page: Int) =
        dao.saveProgress(id, page, System.currentTimeMillis())

    private fun renderThumb(renderer: PdfRenderer, out: File) {
        renderer.withPage(0) { page ->
            val width = 480
            val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
