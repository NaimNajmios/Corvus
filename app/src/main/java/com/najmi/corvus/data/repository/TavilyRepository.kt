package com.najmi.corvus.data.repository

import com.najmi.corvus.data.remote.TavilyClient
import com.najmi.corvus.domain.model.Source
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TavilyRepository @Inject constructor(
    private val client: TavilyClient
) {
    suspend fun search(query: String, maxResults: Int = 5): List<Source> {
        return try {
            val response = client.search(query, maxResults)
            response.results.map { result ->
                Source(
                    title = result.title,
                    url = result.url,
                    publisher = result.publisher,
                    snippet = result.content?.take(200)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
