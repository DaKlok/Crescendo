package com.daklok.crescendo.ui.library

import com.daklok.crescendo.R
import android.app.Application
import com.daklok.crescendo.CrescendoApp
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daklok.crescendo.data.AppSettings
import com.daklok.crescendo.data.FolderEntity
import com.daklok.crescendo.data.LibraryRepository
import com.daklok.crescendo.data.LibrarySort
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val pieces: List<PieceEntity> = emptyList(),
    /** Priečinky v aktuálnom priečinku. */
    val folders: List<FolderEntity> = emptyList(),
    /** Všetky priečinky (pre dialóg „Presunúť do…“). */
    val allFolders: List<FolderEntity> = emptyList(),
    /** Počet položiek (skladby + podpriečinky) priamo v každom priečinku. */
    val folderCounts: Map<Long, Int> = emptyMap(),
    val currentFolder: FolderEntity? = null,
    /** Cesta od knižnice po aktuálny priečinok. */
    val path: List<FolderEntity> = emptyList(),
    val totalCount: Int = 0,
    val continueReading: PieceEntity? = null,
    /** Ďalšie naposledy otvorené skladby (pás pod „Pokračovať“). */
    val recent: List<PieceEntity> = emptyList(),
    val query: String = "",
    val favoritesOnly: Boolean = false,
    /** Všetky štítky v knižnici a vybraný štítok (filter). */
    val allTags: List<String> = emptyList(),
    val tagFilter: String? = null,
    /** Pri hľadaní / filtri obľúbených sa prehľadáva celá knižnica a priečinky sa skryjú. */
    val globalView: Boolean = false,
    val sort: LibrarySort = LibrarySort.RECENT,
    val importing: Boolean = false,
    val loaded: Boolean = false,
)

/** Vybrané položky v režime výberu (dlhé podržanie). */
data class Selection(val pieces: Set<Long> = emptySet(), val folders: Set<Long> = emptySet()) {
    val active: Boolean get() = pieces.isNotEmpty() || folders.isNotEmpty()
    val count: Int get() = pieces.size + folders.size
}

/** Hlásenie pre plávajúcu notifikáciu. */
data class UiMessage(val text: String, val error: Boolean = false)

private data class Filters(
    val query: String,
    val favoritesOnly: Boolean,
    val importing: Int,
    val folderId: Long?,
    val tag: String?,
)

class LibraryViewModel(
    private val app: Application,
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val favoritesOnly = MutableStateFlow(false)
    private val importing = MutableStateFlow(0)
    private val currentFolderId = MutableStateFlow<Long?>(null)
    private val tagFilter = MutableStateFlow<String?>(null)

    private val _selection = MutableStateFlow(Selection())
    val selection: StateFlow<Selection> = _selection

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private fun say(text: String, error: Boolean = false) {
        _messages.tryEmit(UiMessage(text, error))
    }

    private val setlistRepo = (app as CrescendoApp).setlists

    /** Setlisty (pre dialóg „Pridať do setlistu“). */
    val setlists = setlistRepo.observeSetlists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Pridá skladby do setlistu; ak [newName] nie je null, najprv vytvorí nový setlist. */
    fun addToSetlist(pieceIds: List<Long>, setlistId: Long?, newName: String? = null) {
        if (pieceIds.isEmpty()) return
        viewModelScope.launch {
            val target = setlistId ?: newName?.trim()?.takeIf { it.isNotEmpty() }?.let { setlistRepo.create(it) } ?: return@launch
            setlistRepo.addPieces(target, pieceIds)
            val name = setlistRepo.get(target)?.name ?: ""
            say(app.getString(R.string.msg_added_to_setlist, pieceIds.size, name))
        }
    }

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val filters = combine(query, favoritesOnly, importing, currentFolderId, tagFilter) { q, f, i, folder, tag ->
        Filters(q, f, i, folder, tag)
    }

    /** Kedy sa ktorá skladba naposledy cvičila (z denníka) – na zoradenie. */
    private val lastPracticed = (app as CrescendoApp).practice.observeAll()
        .map { list -> list.groupBy { it.pieceId }.mapValues { (_, l) -> l.maxOf { it.startedAt + it.durationSec * 1000L } } }

    val state: StateFlow<LibraryUiState> = combine(
        library.observePieces(),
        library.observeFolders(),
        settingsRepo.settings,
        filters,
        lastPracticed,
    ) { all, folders, settings, f, practiced ->
        val byId = folders.associateBy { it.id }
        // ak bol aktuálny priečinok zmazaný, vrátime sa do knižnice
        val current = f.folderId?.let { byId[it] }
        val currentId = current?.id

        val global = f.query.isNotBlank() || f.favoritesOnly || f.tag != null
        val q = f.query.trim()
        val filtered = all
            .asSequence()
            .filter { global || it.folderId == currentId }
            .filter { !f.favoritesOnly || it.favorite }
            .filter { f.tag == null || f.tag in it.tagList }
            .filter {
                // hľadá sa v názve, skladateľovi, nástroji, tónine aj štítkoch
                q.isEmpty() || it.title.contains(q, true) || it.composer.contains(q, true) ||
                    it.instrument.contains(q, true) || it.keySignature.contains(q, true) || it.tags.contains(q, true)
            }
            .toList()
        val sorted = when (settings.sort) {
            LibrarySort.RECENT -> filtered.sortedByDescending { maxOf(it.lastOpenedAt, it.addedAt) }
            LibrarySort.ADDED -> filtered.sortedByDescending { it.addedAt }
            LibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            // skladateľ (bez skladateľa na koniec), v rámci neho názov
            LibrarySort.COMPOSER -> filtered.sortedWith(
                compareBy<PieceEntity>({ it.composer.isBlank() }, { it.composer.lowercase() }, { it.title.lowercase() }),
            )
            // od najľahšej (neuvedená obtiažnosť na koniec)
            LibrarySort.DIFFICULTY -> filtered.sortedWith(
                compareBy<PieceEntity>({ it.difficulty == 0 }, { it.difficulty }, { it.title.lowercase() }),
            )
            // naposledy cvičené (podľa denníka)
            LibrarySort.PRACTICED -> filtered.sortedByDescending { practiced[it.id] ?: 0L }
            // dlho necvičené: najprv tie, ktoré sa cvičili najdávnejšie; nikdy necvičené na koniec
            LibrarySort.NEGLECTED -> filtered.sortedWith(
                compareBy<PieceEntity>({ practiced[it.id] == null }, { practiced[it.id] ?: Long.MAX_VALUE }),
            )
        }

        val counts = HashMap<Long, Int>()
        all.forEach { p -> p.folderId?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        folders.forEach { fo -> fo.parentId?.let { counts[it] = (counts[it] ?: 0) + 1 } }

        val path = ArrayList<FolderEntity>()
        var cursor = current
        var guard = 0
        while (cursor != null && guard++ < 64) {
            path.add(0, cursor)
            cursor = cursor.parentId?.let { byId[it] }
        }

        LibraryUiState(
            pieces = sorted,
            folders = if (global) emptyList()
            else folders.filter { it.parentId == currentId }.sortedBy { it.name.lowercase() },
            allFolders = folders,
            folderCounts = counts,
            currentFolder = current,
            path = path,
            totalCount = all.size,
            continueReading = if (current == null) {
                all.filter { it.lastOpenedAt > 0 }.maxByOrNull { it.lastOpenedAt }
            } else null,
            recent = if (current == null) {
                all.filter { it.lastOpenedAt > 0 }.sortedByDescending { it.lastOpenedAt }.drop(1).take(8)
            } else emptyList(),
            query = f.query,
            favoritesOnly = f.favoritesOnly,
            allTags = all.flatMap { it.tagList }.distinct().sortedBy { it.lowercase() },
            tagFilter = f.tag,
            globalView = global,
            sort = settings.sort,
            importing = f.importing > 0,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    // ---- navigácia ----

    fun openFolder(id: Long?) {
        clearSelection()
        currentFolderId.update { id }
    }

    /** O úroveň vyššie; vráti false, ak už sme v knižnici. */
    fun goUp(): Boolean {
        val current = state.value.currentFolder ?: return false
        clearSelection()
        currentFolderId.update { current.parentId }
        return true
    }

    fun setQuery(q: String) {
        clearSelection()
        query.update { q }
    }

    fun setTagFilter(tag: String?) {
        clearSelection()
        tagFilter.update { tag }
    }

    fun updateDetails(piece: PieceEntity) {
        if (piece.title.isBlank()) return
        viewModelScope.launch { library.updateDetails(piece) }
    }

    fun setFavoritesOnly(value: Boolean) {
        clearSelection()
        favoritesOnly.update { value }
    }

    // ---- výber viacerých položiek ----

    fun togglePiece(id: Long) = _selection.update {
        it.copy(pieces = if (id in it.pieces) it.pieces - id else it.pieces + id)
    }

    fun toggleFolder(id: Long) = _selection.update {
        it.copy(folders = if (id in it.folders) it.folders - id else it.folders + id)
    }

    fun clearSelection() = _selection.update { Selection() }

    /** Vyberie všetko, čo je práve zobrazené; ak už je všetko vybrané, výber zruší. */
    fun selectAll() {
        val s = state.value
        val all = Selection(s.pieces.map { it.id }.toSet(), s.folders.map { it.id }.toSet())
        _selection.update { if (it == all) Selection() else all }
    }

    /** Priečinky, kam sa výber nesmie presunúť (vybrané priečinky a ich podpriečinky). */
    fun selectionExcluded(): Set<Long> =
        _selection.value.folders.flatMap { descendantsOf(it) }.toSet()

    fun moveSelected(target: Long?) {
        val sel = _selection.value
        val excluded = selectionExcluded()
        if (target != null && target in excluded) return
        viewModelScope.launch {
            sel.pieces.forEach { library.movePiece(it, target) }
            sel.folders.forEach { library.moveFolder(it, target) }
            clearSelection()
            say(app.getString(R.string.msg_moved_n, sel.count, folderName(target)))
        }
    }

    fun deleteSelected() {
        val sel = _selection.value
        val pieces = state.value.pieces.filter { it.id in sel.pieces }
        val folders = state.value.allFolders.filter { it.id in sel.folders }
        viewModelScope.launch {
            pieces.forEach { library.delete(it) }
            // priečinky od najhlbšieho, aby sa obsah presúval správne o úroveň vyššie
            folders.sortedByDescending { depthOf(it) }.forEach { f ->
                val fresh = library.getFolder(f.id) ?: return@forEach
                library.deleteFolder(fresh)
            }
            clearSelection()
            say(app.getString(R.string.msg_deleted_n, sel.count))
        }
    }

    private fun depthOf(folder: FolderEntity): Int {
        val byId = state.value.allFolders.associateBy { it.id }
        var d = 0
        var p = folder.parentId
        while (p != null && d < 64) {
            d++
            p = byId[p]?.parentId
        }
        return d
    }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { settingsRepo.update { it.copy(sort = sort) } }
    }

    // ---- import ----

    /**
     * Importuje jeden alebo viac PDF do aktuálneho priečinka (alebo do knižnice, ak [intoRoot]).
     * [onFirstImported] sa zavolá s ID prvého úspešného importu.
     */
    fun importPdfs(uris: List<Uri>, intoRoot: Boolean = false, onFirstImported: ((Long) -> Unit)? = null) {
        if (uris.isEmpty()) return
        val target = if (intoRoot) null else state.value.currentFolder?.id
        viewModelScope.launch {
            importing.update { it + 1 }
            var ok = 0
            var failed = 0
            var firstId: Long? = null
            for (uri in uris) {
                // balík .crescendo (skladba aj s poznámkami) alebo obyčajné PDF
                (if (library.isPackage(uri)) library.importPackage(uri, target) else library.importPdf(uri, target))
                    .onSuccess { id ->
                        ok++
                        if (firstId == null) firstId = id
                    }
                    .onFailure { failed++ }
            }
            importing.update { it - 1 }
            when {
                ok > 0 && failed == 0 ->
                    say(if (ok == 1) app.getString(R.string.msg_piece_added) else app.getString(R.string.msg_pieces_added, ok))
                ok > 0 -> say(app.getString(R.string.msg_import_partial, ok, failed))
                else -> say(app.getString(R.string.msg_import_failed), error = true)
            }
            firstId?.let { onFirstImported?.invoke(it) }
        }
    }

    /** Import naskenovaného PDF (zo skenera) s vlastným názvom do aktuálneho priečinka. */
    fun importScan(uri: Uri, title: String, onImported: (Long) -> Unit = {}) {
        val target = state.value.currentFolder?.id
        viewModelScope.launch {
            importing.update { it + 1 }
            library.importPdf(uri, target, title.trim())
                .onSuccess { id ->
                    say(app.getString(R.string.msg_piece_added))
                    onImported(id)
                }
                .onFailure { say(app.getString(R.string.msg_import_failed), error = true) }
            importing.update { it - 1 }
        }
    }

    /** Doplní strany z PDF (súbor alebo sken) do existujúcej skladby. */
    fun appendToPiece(piece: PieceEntity, uri: Uri, atStart: Boolean) {
        viewModelScope.launch {
            importing.update { it + 1 }
            library.appendPdf(piece, uri, atStart, (app as CrescendoApp).annotations)
                .onSuccess { n -> say(app.getString(R.string.msg_pages_added, n, piece.title)) }
                .onFailure { say(app.getString(R.string.msg_append_failed), error = true) }
            importing.update { it - 1 }
        }
    }

    fun editPages(piece: PieceEntity, pages: List<Pair<Int, Int>>) {
        viewModelScope.launch {
            importing.update { it + 1 }
            library.editPages(piece, pages, (app as CrescendoApp).annotations)
                .onSuccess { say(app.getString(R.string.msg_pages_saved)) }
                .onFailure { say(app.getString(R.string.msg_pages_failed), error = true) }
            importing.update { it - 1 }
        }
    }

    fun splitPiece(piece: PieceEntity, parts: List<Triple<String, Int, Int>>, keepOriginal: Boolean) {
        viewModelScope.launch {
            importing.update { it + 1 }
            library.splitPiece(piece, parts, keepOriginal, (app as CrescendoApp).annotations)
                .onSuccess { n -> say(app.getString(R.string.msg_split_done, n)) }
                .onFailure { say(app.getString(R.string.msg_pages_failed), error = true) }
            importing.update { it - 1 }
        }
    }

    // ---- skladby ----

    fun rename(piece: PieceEntity, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { library.rename(piece.id, clean) }
    }

    fun toggleFavorite(piece: PieceEntity) {
        viewModelScope.launch { library.setFavorite(piece.id, !piece.favorite) }
    }

    fun delete(piece: PieceEntity) {
        viewModelScope.launch {
            library.delete(piece)
            say(app.getString(R.string.msg_piece_deleted))
        }
    }

    fun movePiece(piece: PieceEntity, folderId: Long?) {
        viewModelScope.launch {
            library.movePiece(piece.id, folderId)
            say(app.getString(R.string.msg_moved_to, folderName(folderId)))
        }
    }

    // ---- priečinky ----

    fun createFolder(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val parent = state.value.currentFolder?.id
        viewModelScope.launch { library.createFolder(clean, parent) }
    }

    fun renameFolder(folder: FolderEntity, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { library.renameFolder(folder.id, clean) }
    }

    fun moveFolder(folder: FolderEntity, parentId: Long?) {
        if (parentId != null && parentId in descendantsOf(folder.id)) return
        viewModelScope.launch {
            library.moveFolder(folder.id, parentId)
            say(app.getString(R.string.msg_moved_to, folderName(parentId)))
        }
    }

    fun setFolderColor(folder: FolderEntity, color: Int?) {
        viewModelScope.launch { library.setFolderColor(folder.id, color) }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            library.deleteFolder(folder)
            say(app.getString(R.string.msg_folder_removed, folder.name))
        }
    }

    /** ID priečinka a všetkých jeho podpriečinkov (kam sa priečinok nesmie presunúť). */
    fun descendantsOf(folderId: Long): Set<Long> {
        val all = state.value.allFolders
        val result = mutableSetOf(folderId)
        var changed = true
        while (changed) {
            changed = false
            for (f in all) {
                val p = f.parentId
                if (p != null && p in result && f.id !in result) {
                    result.add(f.id)
                    changed = true
                }
            }
        }
        return result
    }

    private fun folderName(id: Long?): String =
        id?.let { fid -> state.value.allFolders.firstOrNull { it.id == fid }?.name } ?: app.getString(R.string.library)

    fun thumbFile(piece: PieceEntity) = library.thumbFile(piece)

    fun pdfFile(piece: PieceEntity) = library.pdfFile(piece)

    suspend fun loadAnnotations(pieceId: Long) = (app as CrescendoApp).annotations.load(pieceId)
}
