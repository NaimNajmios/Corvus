package com.najmi.corvus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey
    val id: String,
    val resultId: String,
    val claim: String,
    val resultType: String,
    val verdict: String,
    val confidence: Float,
    val bookmarkedAt: Long = System.currentTimeMillis(),
    val userNotes: String = "",
    val tags: String = "",
    val lastEditedAt: Long = System.currentTimeMillis()
)
