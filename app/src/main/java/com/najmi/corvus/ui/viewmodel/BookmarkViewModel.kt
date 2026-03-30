package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.local.BookmarkEntity
import com.najmi.corvus.data.repository.BookmarkRepository
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.CorvusCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkWithResult(
    val bookmark: BookmarkEntity,
    val result: CorvusCheckResult
)

data class BookmarkUiState(
    val bookmarks: List<BookmarkWithResult> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null
)

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    
    private val _uiState = MutableStateFlow(BookmarkUiState())
    val uiState: StateFlow<BookmarkUiState> = _uiState.asStateFlow()

    init {
        observeBookmarks()
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            _searchQuery.flatMapLatest { query ->
                _uiState.update { it.copy(isLoading = true, searchQuery = query) }
                val flow = if (query.isNotBlank()) {
                    bookmarkRepository.searchBookmarks(query)
                } else {
                    bookmarkRepository.getAllBookmarks()
                }
                flow.map { bookmarks ->
                    bookmarks.mapNotNull { bookmark ->
                        historyRepository.getResultById(bookmark.resultId)?.let { result ->
                            BookmarkWithResult(bookmark, result)
                        }
                    }
                }
            }.collect { bookmarks ->
                _uiState.update { it.copy(
                    bookmarks = bookmarks,
                    isLoading = false
                ) }
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun addBookmark(result: CorvusCheckResult, notes: String = "", tags: String = "") {
        viewModelScope.launch {
            try {
                bookmarkRepository.addBookmark(result, notes, tags)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateNotes(bookmarkId: String, notes: String) {
        viewModelScope.launch {
            try {
                bookmarkRepository.updateNotes(bookmarkId, notes)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun addTag(bookmarkId: String, tag: String) {
        viewModelScope.launch {
            try {
                bookmarkRepository.addTag(bookmarkId, tag)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun removeTag(bookmarkId: String, tag: String) {
        viewModelScope.launch {
            try {
                bookmarkRepository.removeTag(bookmarkId, tag)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            try {
                bookmarkRepository.deleteBookmark(bookmarkId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                bookmarkRepository.clearAll()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    suspend fun isBookmarked(resultId: String): Boolean {
        return bookmarkRepository.isBookmarked(resultId)
    }

    suspend fun getBookmarkWithResult(bookmarkId: String): Pair<BookmarkEntity, CorvusCheckResult>? {
        return bookmarkRepository.getBookmarkWithResult(bookmarkId)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
