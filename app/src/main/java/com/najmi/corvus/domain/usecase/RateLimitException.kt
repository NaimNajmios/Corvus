package com.najmi.corvus.domain.usecase

class RateLimitException(
    message: String = "Rate limit exceeded (429)",
    val provider: String? = null
) : Exception(message)
