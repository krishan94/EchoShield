package com.echoshield.domain.repository

import com.echoshield.domain.model.FamilyInvitation
import com.echoshield.domain.model.FamilyMember
import com.echoshield.domain.model.UserSearchInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for managing family circles, invitations, and member relationships.
 */
interface FamilyRepository {
    /**
     * Hot observable flow of active family circle members.
     */
    val familyMembers: StateFlow<List<FamilyMember>>

    /**
     * Hot observable flow of incoming pending invitations.
     */
    val incomingInvitations: StateFlow<List<FamilyInvitation>>

    /**
     * Hot observable flow of outgoing pending invitations.
     */
    val outgoingInvitations: StateFlow<List<FamilyInvitation>>

    /**
     * Synchronizes family members from remote server/Firestore.
     */
    suspend fun refreshFamilyMembers(): Result<List<FamilyMember>>

    /**
     * Synchronizes incoming and outgoing pending invitations.
     */
    suspend fun refreshInvitations(): Result<Unit>

    /**
     * Retrieves specific member details by relationship or member ID.
     */
    suspend fun getMemberById(memberId: String): FamilyMember?

    /**
     * Safely queries if an account exists for the given email without exposing private data.
     */
    suspend fun searchUser(email: String): Result<UserSearchInfo>

    /**
     * Dispatches a new connection invitation to the specified email.
     */
    suspend fun sendInvitation(recipientEmail: String, relationship: String): Result<FamilyInvitation>

    /**
     * Accepts a pending invitation and creates an authorized family relationship.
     */
    suspend fun acceptInvitation(invitationId: String): Result<Unit>

    /**
     * Declines a pending invitation.
     */
    suspend fun rejectInvitation(invitationId: String): Result<Unit>

    /**
     * Cancels an outgoing pending invitation.
     */
    suspend fun cancelInvitation(invitationId: String): Result<Unit>

    /**
     * Removes a family relationship from the caller's circle.
     */
    suspend fun removeFamilyMember(relationshipId: String): Result<Unit>
}

