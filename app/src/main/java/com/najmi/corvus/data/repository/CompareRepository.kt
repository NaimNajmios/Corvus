package com.najmi.corvus.data.repository

import com.najmi.corvus.domain.model.CorvusResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompareRepository @Inject constructor() {
    
    private val _selectedClaims = MutableStateFlow<List<CorvusResult>>(emptyList())
    val selectedClaims: StateFlow<List<CorvusResult>> = _selectedClaims.asStateFlow()
    
    companion object {
        const val MAX_COMPARE_ITEMS = 4
    }
    
    fun isSelected(claimId: String): Boolean {
        return _selectedClaims.value.any { it.id == claimId }
    }
    
    fun toggleSelection(claim: CorvusResult) {
        _selectedClaims.update { current ->
            if (current.any { it.id == claim.id }) {
                current.filter { it.id != claim.id }
            } else if (current.size < MAX_COMPARE_ITEMS) {
                current + claim
            } else {
                current
            }
        }
    }
    
    fun addClaim(claim: CorvusResult) {
        if (_selectedClaims.value.size < MAX_COMPARE_ITEMS) {
            _selectedClaims.update { current ->
                if (current.none { it.id == claim.id }) {
                    current + claim
                } else {
                    current
                }
            }
        }
    }
    
    fun removeClaim(claimId: String) {
        _selectedClaims.update { current ->
            current.filter { it.id != claimId }
        }
    }
    
    fun replaceClaim(oldClaimId: String, newClaim: CorvusResult) {
        _selectedClaims.update { current ->
            current.map { if (it.id == oldClaimId) newClaim else it }
        }
    }
    
    fun clearSelection() {
        _selectedClaims.value = emptyList()
    }
    
    fun getSelectionCount(): Int = _selectedClaims.value.size
}
