package com.najmi.corvus.di

import com.najmi.corvus.domain.model.LlmProvider
import dagger.MapKey

/**
 * Custom key for LlmProvider enum in Hilt multibinding.
 */
@MapKey
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LlmProviderKey(val value: LlmProvider)
