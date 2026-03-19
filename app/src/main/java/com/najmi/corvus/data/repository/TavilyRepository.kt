package com.najmi.corvus.data.repository

import android.util.Log
import com.najmi.corvus.data.remote.TavilyClient
import com.najmi.corvus.domain.model.Source
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TavilyRepository @Inject constructor(
    private val client: TavilyClient
) {
    companion object {
        private const val TAG = "TavilyRepository"
    }

    suspend fun search(query: String, maxResults: Int = 5): List<Source> {
        return try {
            Log.d(TAG, "Searching Tavily for: $query")
            val response = client.search(query, maxResults)
            val sources = response.results.map { result ->
                Source(
                    title = result.title,
                    url = result.url,
                    publisher = result.publisher,
                    snippet = result.content?.take(200)
                )
            }
            Log.d(TAG, "Found ${sources.size} sources")
            sources
        } catch (e: Exception) {
            Log.e(TAG, "Tavily search failed: ${e.message}", e)
            emptyList()
        }
    }
}
