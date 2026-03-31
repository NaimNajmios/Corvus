package com.najmi.corvus.data.repository

import android.util.Log
import com.najmi.corvus.data.local.BookmarkDao
import com.najmi.corvus.data.local.BookmarkEntity
import com.najmi.corvus.domain.model.CorvusCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val historyRepository: HistoryRepository
) {
    companion object {
        private const val TAG = "BookmarkRepository"
    }

    suspend fun addBookmark(result: CorvusCheckResult, notes: String = ""): String {
        val bookmarkId = UUID.randomUUID().toString()
        val bookmark = BookmarkEntity(
            id = bookmarkId,
            resultId = result.id,
            claim = result.claim,
            resultType = getResultType(result),
            verdict = getVerdictString(result),
            confidence = result.confidence,
            userNotes = notes
        )
        bookmarkDao.insert(bookmark)
        Log.d(TAG, "Added bookmark with id: $bookmarkId for result: ${result.id}")
        return bookmarkId
    }

    suspend fun updateNotes(bookmarkId: String, notes: String) {
        bookmarkDao.getById(bookmarkId)?.let { bookmark ->
            bookmarkDao.update(
                bookmark.copy(
                    userNotes = notes,
                    lastEditedAt = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Updated notes for bookmark: $bookmarkId")
        }
    }

    fun getAllBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    fun searchBookmarks(query: String): Flow<List<BookmarkEntity>> = bookmarkDao.searchBookmarks(query)

    suspend fun getBookmarkById(id: String): BookmarkEntity? = bookmarkDao.getById(id)

    suspend fun getBookmarkWithResult(bookmarkId: String): Pair<BookmarkEntity, CorvusCheckResult>? {
        val bookmark = bookmarkDao.getById(bookmarkId) ?: return null
        val result = historyRepository.getResultById(bookmark.resultId) ?: return null
        return bookmark to result
    }

    suspend fun deleteBookmark(bookmarkId: String) {
        bookmarkDao.deleteById(bookmarkId)
        Log.d(TAG, "Deleted bookmark: $bookmarkId")
    }

    suspend fun clearAll() {
        bookmarkDao.clearAll()
        Log.d(TAG, "Cleared all bookmarks")
    }

    suspend fun isBookmarked(resultId: String): Boolean = bookmarkDao.isBookmarked(resultId)

    suspend fun getCount(): Int = bookmarkDao.getCount()

    private fun getResultType(result: CorvusCheckResult): String {
        return when (result) {
            is CorvusCheckResult.GeneralResult -> "GENERAL"
            is CorvusCheckResult.QuoteResult -> "QUOTE"
            is CorvusCheckResult.CompositeResult -> "COMPOSITE"
            is CorvusCheckResult.ViralHoaxResult -> "VIRAL"
        }
    }

    private fun getVerdictString(result: CorvusCheckResult): String {
        return when (result) {
            is CorvusCheckResult.GeneralResult -> result.verdict.name
            is CorvusCheckResult.QuoteResult -> result.quoteVerdict.name
            is CorvusCheckResult.CompositeResult -> result.compositeVerdict.name
            is CorvusCheckResult.ViralHoaxResult -> "FALSE"
        }
    }
}
