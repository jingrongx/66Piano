package com.pianokids.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.data.db.PieceEntity
import com.pianokids.data.repo.PiecesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 乐谱库 UI 状态。
 */
data class LibraryUiState(
    val items: List<PieceEntity> = emptyList(),
    val loading: Boolean = true,
    val confirmDeleteId: Long? = null,
)

/**
 * 乐谱库 ViewModel。
 *
 * 列出所有自定义/导入的乐谱，提供删除与跳转编辑入口。
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val piecesRepository: PiecesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            piecesRepository.observeAll().collect { items ->
                _uiState.value = _uiState.value.copy(items = items, loading = false)
            }
        }
    }

    /**
     * 请求确认删除一条乐谱。
     */
    fun requestDelete(id: Long) {
        _uiState.value = _uiState.value.copy(confirmDeleteId = id)
    }

    /**
     * 取消删除确认。
     */
    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(confirmDeleteId = null)
    }

    /**
     * 确认删除。
     */
    fun confirmDelete() {
        val id = _uiState.value.confirmDeleteId ?: return
        viewModelScope.launch {
            piecesRepository.delete(id)
            _uiState.value = _uiState.value.copy(confirmDeleteId = null)
        }
    }
}
