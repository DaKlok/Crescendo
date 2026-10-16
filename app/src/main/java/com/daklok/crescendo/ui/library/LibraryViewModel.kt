package com.daklok.crescendo.ui.library

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
    val query: String = "",
    val favoritesOnly: Boolean = false,
    /** Pri hľadaní / filtri obľúbených sa prehľadáva celá knižnica a priečinky sa skryjú. */
    val globalView: Boolean = false,
    val sort: LibrarySort = LibrarySort.RECENT,
    val importing: Boolean = false,
    val loaded: Boolean = false,
)

private data class Filters(
    val query: String,
    val favoritesOnly: Boolean,
    val importing: Int,
    val folderId: Long?,
)

class LibraryViewModel(
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val favoritesOnly = MutableStateFlow(false)
    private val importing = MutableStateFlow(0)
    private val currentFolderId = MutableStateFlow<Long?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val filters = combine(query, favoritesOnly, importing, currentFolderId) { q, f, i, folder ->
        Filters(q, f, i, folder)
    }

    val state: StateFlow<LibraryUiState> = combine(
        library.observePieces(),
        library.observeFolders(),
        settingsRepo.settings,
        filters,
    ) { all, folders, settings, f ->
        val byId = folders.associateBy { it.id }
        // ak bol aktuálny priečinok zmazaný, vrátime sa do knižnice
        val current = f.folderId?.let { byId[it] }
        val currentId = current?.id

        val global = f.query.isNotBlank() || f.favoritesOnly
        val filtered = all
            .asSequence()
            .filter { global || it.folderId == currentId }
            .filter { !f.favoritesOnly || it.favorite }
            .filter { f.query.isBlank() || it.title.contains(f.query.trim(), ignoreCase = true) }
            .toList()
        val sorted = when (settings.sort) {
            LibrarySort.RECENT -> filtered.sortedByDescending { maxOf(it.lastOpenedAt, it.addedAt) }
            LibrarySort.ADDED -> filtered.sortedByDescending { it.addedAt }
            LibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase() }
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
            query = f.query,
            favoritesOnly = f.favoritesOnly,
            globalView = global,
            sort = settings.sort,
            importing = f.importing > 0,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    // ---- navigácia ----

    fun openFolder(id: Long?) = currentFolderId.update { id }

    /** O úroveň vyššie; vráti false, ak už sme v knižnici. */
    fun goUp(): Boolean {
        val current = state.value.currentFolder ?: return false
        currentFolderId.update { current.parentId }
        return true
    }

    fun setQuery(q: String) = query.update { q }

    fun setFavoritesOnly(value: Boolean) = favoritesOnly.update { value }

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
                library.importPdf(uri, target)
                    .onSuccess { id ->
                        ok++
                        if (firstId == null) firstId = id
                    }
                    .onFailure { failed++ }
            }
            importing.update { it - 1 }
            when {
                ok > 0 && failed == 0 ->
                    _messages.tryEmit(if (ok == 1) "Skladba pridaná" else "Pridaných skladieb: $ok")
                ok > 0 -> _messages.tryEmit("Pridaných: $ok, zlyhalo: $failed")
                else -> _messages.tryEmit("Import zlyhal – súbor nie je platné PDF alebo je chránený heslom")
            }
            firstId?.let { onFirstImported?.invoke(it) }
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
            _messages.tryEmit("Skladba odstránená")
        }
    }

    fun movePiece(piece: PieceEntity, folderId: Long?) {
        viewModelScope.launch {
            library.movePiece(piece.id, folderId)
            _messages.tryEmit("Presunuté do: ${folderName(folderId)}")
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
            _messages.tryEmit("Presunuté do: ${folderName(parentId)}")
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            library.deleteFolder(folder)
            _messages.tryEmit("Priečinok „${folder.name}“ zrušený, obsah sa presunul o úroveň vyššie")
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
        id?.let { fid -> state.value.allFolders.firstOrNull { it.id == fid }?.name } ?: "Knižnica"

    fun thumbFile(piece: PieceEntity) = library.thumbFile(piece)
}
