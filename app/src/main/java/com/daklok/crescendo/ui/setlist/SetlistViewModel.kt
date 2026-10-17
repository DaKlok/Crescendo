package com.daklok.crescendo.ui.setlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daklok.crescendo.data.LibraryRepository
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.SetlistEntity
import com.daklok.crescendo.data.SetlistItemEntity
import com.daklok.crescendo.data.SetlistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Položka setlistu spolu so skladbou. */
data class SetlistRow(val item: SetlistItemEntity, val piece: PieceEntity)

data class SetlistWithItems(val setlist: SetlistEntity, val rows: List<SetlistRow>)

data class SetlistsUiState(
    val setlists: List<SetlistWithItems> = emptyList(),
    val pieces: List<PieceEntity> = emptyList(),
    val loaded: Boolean = false,
)

class SetlistViewModel(
    private val repo: SetlistRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    val state: StateFlow<SetlistsUiState> = combine(
        repo.observeSetlists(),
        repo.observeItems(),
        library.observePieces(),
    ) { lists, items, pieces ->
        val byId = pieces.associateBy { it.id }
        val grouped = items.groupBy { it.setlistId }
        SetlistsUiState(
            setlists = lists.map { sl ->
                SetlistWithItems(
                    sl,
                    grouped[sl.id].orEmpty()
                        .sortedBy { it.position }
                        .mapNotNull { item -> byId[item.pieceId]?.let { SetlistRow(item, it) } },
                )
            },
            pieces = pieces.sortedBy { it.title.lowercase() },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SetlistsUiState())

    fun create(name: String, then: (Long) -> Unit = {}) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { then(repo.create(clean)) }
    }

    fun rename(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.rename(id, clean) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun addPieces(setlistId: Long, pieceIds: List<Long>) {
        viewModelScope.launch { repo.addPieces(setlistId, pieceIds) }
    }

    fun remove(row: SetlistRow) {
        viewModelScope.launch { repo.removeItem(row.item.id) }
    }

    /** Posunie položku o [delta] (−1 hore, +1 dole). */
    fun move(rows: List<SetlistRow>, index: Int, delta: Int) {
        val target = index + delta
        if (index !in rows.indices || target !in rows.indices) return
        val items = rows.map { it.item }.toMutableList()
        val moved = items.removeAt(index)
        items.add(target, moved)
        viewModelScope.launch { repo.reorder(items) }
    }

    fun thumbFile(piece: PieceEntity) = library.thumbFile(piece)
}
