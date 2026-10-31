package com.echoshield.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echoshield.core.di.AppContainer
import com.echoshield.domain.model.FamilyInvitation
import com.echoshield.domain.model.FamilyMember
import com.echoshield.domain.model.UserSearchInfo
import com.echoshield.domain.repository.FamilyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State representing family members, pending invitations, and asynchronous actions.
 */
data class FamilyUiState(
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val members: List<FamilyMember> = emptyList(),
    val incomingInvitations: List<FamilyInvitation> = emptyList(),
    val outgoingInvitations: List<FamilyInvitation> = emptyList(),
    val selectedMember: FamilyMember? = null,
    val searchResult: UserSearchInfo? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * ViewModel managing Family Management System interactions and business state.
 */
class FamilyViewModel(
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilyUiState())
    val uiState: StateFlow<FamilyUiState> = _uiState.asStateFlow()

    init {
        // Collect real-time reactive streams from repository
        viewModelScope.launch {
            familyRepository.familyMembers.collect { members ->
                _uiState.update { it.copy(members = members) }
            }
        }
        viewModelScope.launch {
            familyRepository.incomingInvitations.collect { incoming ->
                _uiState.update { it.copy(incomingInvitations = incoming) }
            }
        }
        viewModelScope.launch {
            familyRepository.outgoingInvitations.collect { outgoing ->
                _uiState.update { it.copy(outgoingInvitations = outgoing) }
            }
        }

        // Trigger initial data refresh
        loadFamilyData()
    }

    fun loadFamilyData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val membersResult = familyRepository.refreshFamilyMembers()
            val invitesResult = familyRepository.refreshInvitations()

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    errorMessage = membersResult.exceptionOrNull()?.localizedMessage
                        ?: invitesResult.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun searchUser(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address to search.") }
            return
        }

        _uiState.update { it.copy(isSearching = true, errorMessage = null, searchResult = null) }
        viewModelScope.launch {
            familyRepository.searchUser(cleanEmail)
                .onSuccess { info ->
                    _uiState.update { it.copy(isSearching = false, searchResult = info) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            errorMessage = err.localizedMessage ?: "Failed to query EchoShield directory."
                        )
                    }
                }
        }
    }

    fun sendInvitation(
        recipientEmail: String,
        relationship: String,
        onSuccess: () -> Unit
    ) {
        val cleanEmail = recipientEmail.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid recipient email address.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            familyRepository.sendInvitation(cleanEmail, relationship)
                .onSuccess { inv ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Family invitation dispatched to ${inv.recipientEmail}."
                        )
                    }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to dispatch family invitation."
                        )
                    }
                }
        }
    }

    fun acceptInvitation(invitationId: String, onSuccess: (() -> Unit)? = null) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            familyRepository.acceptInvitation(invitationId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Invitation accepted. Family member connected to your circle."
                        )
                    }
                    onSuccess?.invoke()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to accept invitation."
                        )
                    }
                }
        }
    }

    fun rejectInvitation(invitationId: String, onSuccess: (() -> Unit)? = null) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            familyRepository.rejectInvitation(invitationId)
                .onSuccess {
                    _uiState.update {
                        it.copy(isLoading = false, successMessage = "Invitation declined.")
                    }
                    onSuccess?.invoke()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to decline invitation."
                        )
                    }
                }
        }
    }

    fun cancelInvitation(invitationId: String, onSuccess: (() -> Unit)? = null) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            familyRepository.cancelInvitation(invitationId)
                .onSuccess {
                    _uiState.update {
                        it.copy(isLoading = false, successMessage = "Invitation cancelled.")
                    }
                    onSuccess?.invoke()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to cancel invitation."
                        )
                    }
                }
        }
    }

    fun loadMemberProfile(memberId: String) {
        viewModelScope.launch {
            val member = familyRepository.getMemberById(memberId)
            _uiState.update { it.copy(selectedMember = member) }
        }
    }

    fun removeFamilyMember(relationshipId: String, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            familyRepository.removeFamilyMember(relationshipId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Family relationship removed."
                        )
                    }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to remove family member."
                        )
                    }
                }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FamilyViewModel(AppContainer.familyRepository)
            }
        }
    }
}

