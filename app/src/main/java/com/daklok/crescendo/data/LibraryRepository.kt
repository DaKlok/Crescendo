package com.daklok.crescendo.data

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

class LibraryRepository(
    private val context: Context,
    private val dao: PieceDao,
    private val folderDao: FolderDao,
) {
    private val pdfDir = File(context.filesDir, "pdfs").apply { mkdirs() }
    private val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun observePieces(): Flow<List<PieceEntity>> = dao.observeAll()

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    // ---- priečinky ----

    suspend fun createFolder(name: String, parentId: Long?): Long =
        folderDao.insert(FolderEntity(name = name, parentId = parentId))

    suspend fun renameFolder(id: Long, name: String) = folderDao.rename(id, name)

    suspend fun moveFolder(id: Long, parentId: Long?) = folderDao.setParent(id, parentId)

    /** Zruší priečinok; jeho skladby aj podpriečinky sa presunú o úroveň vyššie (nič sa nestratí). */
    suspend fun deleteFolder(folder: FolderEntity) {
        dao.reparent(folder.id, folder.parentId)
        folderDao.reparentChildren(folder.id, folder.parentId)
        folderDao.delete(folder.id)
    }

    suspend fun movePiece(id: Long, folderId: Long?) = dao.moveToFolder(id, folderId)

    suspend fun getPiece(id: Long): PieceEntity? = dao.getById(id)

    fun pdfFile(piece: PieceEntity): File = File(pdfDir, piece.fileName)

    fun thumbFile(piece: PieceEntity): File? = piece.thumbName?.let { File(thumbDir, it) }

    /**
     * Skopíruje PDF do interného úložiska, vytvorí náhľad prvej strany a uloží záznam.
     * Vráti ID novej skladby, alebo chybu (nie je to PDF / je chránené heslom...).
     */
    suspend fun importPdf(uri: Uri, folderId: Long? = null): Result<Long> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val target = File(pdfDir, "$id.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            } ?: return@withContext Result.failure(IllegalStateException("Cannot open file"))

            var pages = 0
            var thumbName: String? = null
            ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = PdfRenderer(fd)
                try {
                    pages = renderer.pageCount
                    if (pages > 0) {
                        thumbName = "$id.png"
                        renderThumb(renderer, File(thumbDir, "$id.png"))
                    }
                } finally {
                    renderer.close()
                }
            }
            if (pages == 0) {
                target.delete()
                return@withContext Result.failure(IllegalStateException("PDF has no pages"))
            }

            val piece = PieceEntity(
                title = displayName(uri)?.removeSuffix(".pdf")?.removeSuffix(".PDF")
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

    suspend fun delete(piece: PieceEntity) = withContext(Dispatchers.IO) {
        dao.delete(piece)
        pdfFile(piece).delete()
        thumbFile(piece)?.delete()
        File(context.filesDir, "annotations/${piece.id}.json").delete()
    }

    suspend fun rename(id: Long, title: String) = dao.rename(id, title)

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
