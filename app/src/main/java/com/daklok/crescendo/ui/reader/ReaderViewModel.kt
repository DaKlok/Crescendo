package com.daklok.crescendo.ui.reader

import com.daklok.crescendo.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daklok.crescendo.data.AnnotationStore
import com.daklok.crescendo.data.AppSettings
import com.daklok.crescendo.data.LibraryRepository
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.SettingsRepository
import com.daklok.crescendo.data.SetlistRepository
import com.daklok.crescendo.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val loading: Boolean = true,
    val piece: PieceEntity? = null,
    val document: PdfDocument? = null,
    @StringRes val errorRes: Int? = null,
)

/** Položka setlistu v čítačke. */
data class SetlistEntry(val pieceId: Long, val title: String)

/** Kde v setliste práve sme. */
data class SetlistNav(val name: String, val index: Int, val entries: List<SetlistEntry>) {
    val previous: SetlistEntry? get() = entries.getOrNull(index - 1)
    val next: SetlistEntry? get() = entries.getOrNull(index + 1)
}

class ReaderViewModel(
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
    annotationStore: AnnotationStore,
    private val pieceId: Long,
    private val setlists: SetlistRepository? = null,
    private val setlistId: Long? = null,
    private val setlistIndex: Int = 0,
    /** V setliste začíname každú skladbu od prvej strany. */
    val fromStart: Boolean = false,
) : ViewModel() {

    private val _setlist = MutableStateFlow<SetlistNav?>(null)
    val setlist: StateFlow<SetlistNav?> = _setlist.asStateFlow()

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val annotations = AnnotationController(annotationStore, pieceId, viewModelScope)

    init {
        viewModelScope.launch { annotations.load() }
        if (setlists != null && setlistId != null) {
            viewModelScope.launch {
                val list = setlists.get(setlistId) ?: return@launch
                // zmazané skladby vynecháme; index prepočítame na zostávajúce položky
                val entries = ArrayList<SetlistEntry>()
                var index = 0
                setlists.itemsOf(setlistId).forEachIndexed { i, item ->
                    val p = library.getPiece(item.pieceId) ?: return@forEachIndexed
                    if (i == setlistIndex) index = entries.size
                    entries.add(SetlistEntry(p.id, p.title))
                }
                if (entries.isNotEmpty()) {
                    _setlist.value = SetlistNav(list.name, index.coerceIn(0, entries.lastIndex), entries)
                }
            }
        }
        viewModelScope.launch {
            val piece = library.getPiece(pieceId)
            if (piece == null) {
                _state.value = ReaderUiState(loading = false, errorRes = R.string.error_piece_not_found)
                return@launch
            }
            try {
                val doc = withContext(Dispatchers.IO) { PdfDocument(library.pdfFile(piece)) }
                _state.value = ReaderUiState(loading = false, piece = piece, document = doc)
                library.saveProgress(piece.id, piece.lastPage.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0)))
            } catch (e: Exception) {
                _state.value = ReaderUiState(
                    loading = false,
                    piece = piece,
                    errorRes = R.string.error_pdf_open,
                )
            }
        }
    }

    fun pdfFile(piece: PieceEntity) = library.pdfFile(piece)

    fun saveProgress(page: Int) {
        viewModelScope.launch { library.saveProgress(pieceId, page) }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    /** Zatvorí PDF (volá sa pri opustení čítačky). */
    fun release() {
        annotations.flush()
        _state.value.document?.close()
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }
}
