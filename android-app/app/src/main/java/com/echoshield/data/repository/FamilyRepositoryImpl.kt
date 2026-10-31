package com.echoshield.data.repository

import com.echoshield.data.remote.FamilyRemoteDataSource
import com.echoshield.domain.model.FamilyInvitation
import com.echoshield.domain.model.FamilyMember
import com.echoshield.domain.model.UserSearchInfo
import com.echoshield.domain.repository.FamilyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Concrete implementation of [FamilyRepository] managing cached state and delegating to [FamilyRemoteDataSource].
 */
class FamilyRepositoryImpl(
    private val remoteDataSource: FamilyRemoteDataSource
) : FamilyRepository {

    private val _familyMembers = MutableStateFlow<List<FamilyMember>>(emptyList())
    override val familyMembers: StateFlow<List<FamilyMember>> = _familyMembers.asStateFlow()

    private val _incomingInvitations = MutableStateFlow<List<FamilyInvitation>>(emptyList())
    override val incomingInvitations: StateFlow<List<FamilyInvitation>> = _incomingInvitations.asStateFlow()

    private val _outgoingInvitations = MutableStateFlow<List<FamilyInvitation>>(emptyList())
    override val outgoingInvitations: StateFlow<List<FamilyInvitation>> = _outgoingInvitations.asStateFlow()

    override suspend fun refreshFamilyMembers(): Result<List<FamilyMember>> {
        return try {
            val list = remoteDataSource.getFamilyMembers()
            _familyMembers.value = list
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshInvitations(): Result<Unit> {
        return try {
            val (incoming, outgoing) = remoteDataSource.getInvitations()
            _incomingInvitations.value = incoming
            _outgoingInvitations.value = outgoing
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMemberById(memberId: String): FamilyMember? {
        // First check cached in-memory flow
        val cached = _familyMembers.value.firstOrNull { it.id == memberId || it.memberId == memberId }
        if (cached != null) return cached

        // Otherwise fetch fresh list
        val result = refreshFamilyMembers().getOrNull()
        return result?.firstOrNull { it.id == memberId || it.memberId == memberId }
    }

    override suspend fun searchUser(email: String): Result<UserSearchInfo> {
        return try {
            val info = remoteDataSource.searchUserByEmail(email)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendInvitation(recipientEmail: String, relationship: String): Result<FamilyInvitation> {
        return try {
            val inv = remoteDataSource.sendInvitation(recipientEmail, relationship)
            _outgoingInvitations.update { it + inv }
            Result.success(inv)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptInvitation(invitationId: String): Result<Unit> {
        return try {
            remoteDataSource.acceptInvitation(invitationId)
            // Remove from incoming list
            _incomingInvitations.update { list -> list.filterNot { it.id == invitationId } }
            // Refresh family list
            refreshFamilyMembers()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rejectInvitation(invitationId: String): Result<Unit> {
        return try {
            remoteDataSource.rejectInvitation(invitationId)
            _incomingInvitations.update { list -> list.filterNot { it.id == invitationId } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelInvitation(invitationId: String): Result<Unit> {
        return try {
            remoteDataSource.cancelInvitation(invitationId)
            _outgoingInvitations.update { list -> list.filterNot { it.id == invitationId } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFamilyMember(relationshipId: String): Result<Unit> {
        return try {
            remoteDataSource.removeFamilyRelationship(relationshipId)
            _familyMembers.update { list -> list.filterNot { it.id == relationshipId } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

