package com.echoshield.family

import com.echoshield.domain.model.FamilyInvitation
import com.echoshield.domain.model.FamilyMember
import com.echoshield.domain.model.UserSearchInfo
import com.echoshield.domain.repository.FamilyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake implementation of [FamilyRepository] for deterministic hermetic unit testing.
 */
class FakeFamilyRepository : FamilyRepository {

    private val _familyMembers = MutableStateFlow<List<FamilyMember>>(emptyList())
    override val familyMembers: StateFlow<List<FamilyMember>> = _familyMembers.asStateFlow()

    private val _incomingInvitations = MutableStateFlow<List<FamilyInvitation>>(emptyList())
    override val incomingInvitations: StateFlow<List<FamilyInvitation>> = _incomingInvitations.asStateFlow()

    private val _outgoingInvitations = MutableStateFlow<List<FamilyInvitation>>(emptyList())
    override val outgoingInvitations: StateFlow<List<FamilyInvitation>> = _outgoingInvitations.asStateFlow()

    var currentUserEmail: String = "sarah.a@echoshield.family"
    var shouldFailNetwork: Boolean = false

    override suspend fun refreshFamilyMembers(): Result<List<FamilyMember>> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        return Result.success(_familyMembers.value)
    }

    override suspend fun refreshInvitations(): Result<Unit> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        return Result.success(Unit)
    }

    override suspend fun getMemberById(memberId: String): FamilyMember? {
        return _familyMembers.value.firstOrNull { it.id == memberId || it.memberId == memberId }
    }

    override suspend fun searchUser(email: String): Result<UserSearchInfo> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        if (email.lowercase() == "david.b@echoshield.family") {
            return Result.success(UserSearchInfo(exists = true, email = email, name = "David Henderson"))
        }
        return Result.success(UserSearchInfo(exists = false, email = email, name = null))
    }

    override suspend fun sendInvitation(recipientEmail: String, relationship: String): Result<FamilyInvitation> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        if (recipientEmail.lowercase() == currentUserEmail.lowercase()) {
            return Result.failure(IllegalArgumentException("You cannot invite yourself to your own family circle."))
        }
        if (_outgoingInvitations.value.any { it.recipientEmail.lowercase() == recipientEmail.lowercase() }) {
            return Result.failure(IllegalStateException("A pending invitation to this family member already exists."))
        }
        if (_familyMembers.value.any { it.email.lowercase() == recipientEmail.lowercase() }) {
            return Result.failure(IllegalStateException("You are already connected in a family circle with this member."))
        }

        val inv = FamilyInvitation(
            id = "inv_${System.currentTimeMillis()}",
            senderId = "uid_sarah",
            senderName = "Sarah Henderson",
            senderEmail = currentUserEmail,
            recipientEmail = recipientEmail,
            relationship = relationship,
            status = "pending",
            createdAt = "2026-10-31"
        )
        _outgoingInvitations.update { it + inv }
        return Result.success(inv)
    }

    override suspend fun acceptInvitation(invitationId: String): Result<Unit> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        val invite = _incomingInvitations.value.find { it.id == invitationId }
            ?: return Result.failure(IllegalArgumentException("Invitation not found."))

        _incomingInvitations.update { it.filterNot { item -> item.id == invitationId } }
        val newMember = FamilyMember(
            id = "rel_${System.currentTimeMillis()}",
            memberId = invite.senderId,
            name = invite.senderName,
            email = invite.senderEmail,
            relationship = invite.relationship,
            isOnline = false,
            isVoiceEnrolled = false,
            voiceQualityScore = 0.0f,
            lastActive = "Connected"
        )
        _familyMembers.update { it + newMember }
        return Result.success(Unit)
    }

    override suspend fun rejectInvitation(invitationId: String): Result<Unit> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        _incomingInvitations.update { it.filterNot { item -> item.id == invitationId } }
        return Result.success(Unit)
    }

    override suspend fun cancelInvitation(invitationId: String): Result<Unit> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        _outgoingInvitations.update { it.filterNot { item -> item.id == invitationId } }
        return Result.success(Unit)
    }

    override suspend fun removeFamilyMember(relationshipId: String): Result<Unit> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        _familyMembers.update { it.filterNot { item -> item.id == relationshipId } }
        return Result.success(Unit)
    }

    // Helper setup
    fun setMembers(members: List<FamilyMember>) {
        _familyMembers.value = members
    }

    fun setIncomingInvitations(invites: List<FamilyInvitation>) {
        _incomingInvitations.value = invites
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var fakeRepository: FakeFamilyRepository
    private lateinit var viewModel: FamilyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeFamilyRepository()
        viewModel = FamilyViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialFamilyEmptyState() = testScope.runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.members.isEmpty())
        assertTrue(state.incomingInvitations.isEmpty())
        assertTrue(state.outgoingInvitations.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun testPreventSelfInvitation() = testScope.runTest {
        var successCalled = false
        viewModel.sendInvitation(
            recipientEmail = fakeRepository.currentUserEmail,
            relationship = "Spouse",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("cannot invite yourself"))
    }

    @Test
    fun testSendValidInvitationUpdatesOutgoingState() = testScope.runTest {
        var successCalled = false
        viewModel.sendInvitation(
            recipientEmail = "david.b@echoshield.family",
            relationship = "Child",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertTrue(successCalled)
        val state = viewModel.uiState.value
        assertEquals(1, state.outgoingInvitations.size)
        assertEquals("david.b@echoshield.family", state.outgoingInvitations[0].recipientEmail)
        assertEquals("Child", state.outgoingInvitations[0].relationship)
        assertNotNull(state.successMessage)
    }

    @Test
    fun testPreventDuplicatePendingInvitation() = testScope.runTest {
        viewModel.sendInvitation(
            recipientEmail = "david.b@echoshield.family",
            relationship = "Child",
            onSuccess = {}
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.outgoingInvitations.size)

        // Try second duplicate invite
        var secondSuccess = false
        viewModel.sendInvitation(
            recipientEmail = "david.b@echoshield.family",
            relationship = "Child",
            onSuccess = { secondSuccess = true }
        )
        advanceUntilIdle()

        assertFalse(secondSuccess)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("already exists"))
    }

    @Test
    fun testCancelOutgoingInvitation() = testScope.runTest {
        viewModel.sendInvitation(
            recipientEmail = "david.b@echoshield.family",
            relationship = "Child",
            onSuccess = {}
        )
        advanceUntilIdle()
        val inviteId = viewModel.uiState.value.outgoingInvitations.first().id

        var cancelSuccess = false
        viewModel.cancelInvitation(inviteId) { cancelSuccess = true }
        advanceUntilIdle()

        assertTrue(cancelSuccess)
        assertTrue(viewModel.uiState.value.outgoingInvitations.isEmpty())
    }

    @Test
    fun testAcceptIncomingInvitationCreatesFamilyRelationship() = testScope.runTest {
        val incomingInvite = FamilyInvitation(
            id = "inv_12345",
            senderId = "uid_david",
            senderName = "David Henderson",
            senderEmail = "david.b@echoshield.family",
            recipientEmail = fakeRepository.currentUserEmail,
            relationship = "Sibling",
            status = "pending",
            createdAt = "2026-10-31"
        )
        fakeRepository.setIncomingInvitations(listOf(incomingInvite))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.incomingInvitations.size)

        var acceptSuccess = false
        viewModel.acceptInvitation(incomingInvite.id) { acceptSuccess = true }
        advanceUntilIdle()

        assertTrue(acceptSuccess)
        val state = viewModel.uiState.value
        // Incoming invitations cleared
        assertTrue(state.incomingInvitations.isEmpty())
        // Family member added
        assertEquals(1, state.members.size)
        assertEquals("David Henderson", state.members[0].name)
        assertEquals("david.b@echoshield.family", state.members[0].email)
        assertEquals("Sibling", state.members[0].relationship)
    }

    @Test
    fun testDeclineIncomingInvitationRemovesRequest() = testScope.runTest {
        val incomingInvite = FamilyInvitation(
            id = "inv_99999",
            senderId = "uid_unknown",
            senderName = "Unknown Person",
            senderEmail = "unknown@random.org",
            recipientEmail = fakeRepository.currentUserEmail,
            relationship = "Relative",
            status = "pending",
            createdAt = "2026-10-31"
        )
        fakeRepository.setIncomingInvitations(listOf(incomingInvite))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.incomingInvitations.size)

        var rejectSuccess = false
        viewModel.rejectInvitation(incomingInvite.id) { rejectSuccess = true }
        advanceUntilIdle()

        assertTrue(rejectSuccess)
        assertTrue(viewModel.uiState.value.incomingInvitations.isEmpty())
        assertTrue(viewModel.uiState.value.members.isEmpty())
    }

    @Test
    fun testRemoveFamilyMemberRemovesRelationshipFromList() = testScope.runTest {
        val member = FamilyMember(
            id = "rel_001",
            memberId = "uid_david",
            name = "David Henderson",
            email = "david.b@echoshield.family",
            relationship = "Child",
            isOnline = false,
            isVoiceEnrolled = false,
            voiceQualityScore = 0.0f,
            lastActive = "Connected"
        )
        fakeRepository.setMembers(listOf(member))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.members.size)

        var removeSuccess = false
        viewModel.removeFamilyMember(member.id) { removeSuccess = true }
        advanceUntilIdle()

        assertTrue(removeSuccess)
        assertTrue(viewModel.uiState.value.members.isEmpty())
    }

    @Test
    fun testSafeDirectorySearch() = testScope.runTest {
        viewModel.searchUser("david.b@echoshield.family")
        advanceUntilIdle()

        val result = viewModel.uiState.value.searchResult
        assertNotNull(result)
        assertTrue(result!!.exists)
        assertEquals("david.b@echoshield.family", result.email)
        assertEquals("David Henderson", result.name)

        // Non-existent search
        viewModel.searchUser("nobody@family.test")
        advanceUntilIdle()
        val emptyResult = viewModel.uiState.value.searchResult
        assertNotNull(emptyResult)
        assertFalse(emptyResult!!.exists)
    }

    @Test
    fun testVoiceEnrollmentStatusReflectsActualData() = testScope.runTest {
        val unenrolledMember = FamilyMember(
            id = "rel_unenrolled",
            memberId = "uid_un",
            name = "Pending Voice Member",
            email = "un@family.test",
            relationship = "Parent",
            isOnline = false,
            isVoiceEnrolled = false,
            voiceQualityScore = 0.0f,
            lastActive = "Now"
        )
        fakeRepository.setMembers(listOf(unenrolledMember))
        advanceUntilIdle()

        val member = viewModel.uiState.value.members.first()
        assertFalse(member.isVoiceEnrolled)
        assertEquals(0.0f, member.voiceQualityScore)
    }
}

