package com.najmi.corvus.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY bookmarkedAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getById(id: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE resultId = :resultId")
    suspend fun getByResultId(resultId: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE claim LIKE '%' || :query || '%' OR userNotes LIKE '%' || :query || '%' ORDER BY bookmarkedAt DESC")
    fun searchBookmarks(query: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun getCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE resultId = :resultId)")
    suspend fun isBookmarked(resultId: String): Boolean
}
