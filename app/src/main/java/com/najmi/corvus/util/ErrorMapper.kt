package com.najmi.corvus.util

object ErrorMapper {
    /**
     * Maps technical error messages from LLM providers or parsing failures
     * to human-readable strings.
     */
    fun map(error: String?): String {
        if (error == null) return "An unknown error occurred"

        return when {
            // Gemini Specifics
            error.contains("Gemini error (429)", ignoreCase = true) -> 
                "The AI service is temporarily overloaded. Please try again in 30 seconds."
            
            error.contains("Gemini error (503)", ignoreCase = true) || 
            error.contains("Gemini error (504)", ignoreCase = true) ->
                "The AI service is temporarily unavailable. Please try again later."

            error.contains("Response blocked by safety filters", ignoreCase = true) ->
                "The AI could not complete the analysis as it triggered safety filters. Try rephrasing your claim."

            error.contains("Response blocked due to recitation", ignoreCase = true) ->
                "The AI could not complete the analysis due to content rights restrictions."

            // Groq/OpenRouter/Cerebras Specifics
            error.contains("error (429)", ignoreCase = true) ->
                "Rate limit exceeded. Please wait a moment before trying again."

            error.contains("error (401)", ignoreCase = true) ||
            error.contains("error (403)", ignoreCase = true) ->
                "Authentication failed or API key restricted. Please check your settings."

            // Parsing failures
            error.contains("Failed to parse LLM response", ignoreCase = true) ||
            error.contains("Invalid response from", ignoreCase = true) ->
                "The AI returned a malformed response. This usually happens with complex claims. Please try again."

            // Generic network/timeout
            error.contains("timeout", ignoreCase = true) ->
                "The analysis took too long to complete. Please check your internet connection and try again."
            
            error.contains("Unable to resolve host", ignoreCase = true) ||
            error.contains("Failed to connect", ignoreCase = true) ->
                "Network connection error. Please check your internet connection."

            // Fallback: keep the original but clean it up if it's too technical
            else -> {
                if (error.startsWith("{") && error.endsWith("}")) {
                    "The AI service returned an unexpected technical error."
                } else if (error.length > 200) {
                    error.take(200) + "..."
                } else {
                    error
                }
            }
        }
    }

    /**
     * Maps an Exception to a human-readable error message.
     */
    fun map(e: Exception): String = map(e.message)
}
