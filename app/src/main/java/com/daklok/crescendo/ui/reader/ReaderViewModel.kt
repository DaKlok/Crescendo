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

class ReaderViewModel(
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
    annotationStore: AnnotationStore,
    private val pieceId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val annotations = AnnotationController(annotationStore, pieceId, viewModelScope)

    init {
        viewModelScope.launch { annotations.load() }
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
