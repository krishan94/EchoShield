package com.echoshield.data.remote

import com.echoshield.domain.model.FamilyInvitation
import com.echoshield.domain.model.FamilyMember
import com.echoshield.domain.model.UserSearchInfo
import com.echoshield.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

interface FamilyRemoteDataSource {
    suspend fun getFamilyMembers(): List<FamilyMember>
    suspend fun searchUserByEmail(email: String): UserSearchInfo
    suspend fun sendInvitation(recipientEmail: String, relationship: String): FamilyInvitation
    suspend fun getInvitations(): Pair<List<FamilyInvitation>, List<FamilyInvitation>>
    suspend fun acceptInvitation(invitationId: String): Unit
    suspend fun rejectInvitation(invitationId: String): Unit
    suspend fun cancelInvitation(invitationId: String): Unit
    suspend fun removeFamilyRelationship(relationshipId: String): Unit
}

/**
 * Resilient implementation communicating with FastAPI backend via HTTP Bearer tokens,
 * with seamless fallback to direct Google Cloud Firestore SDK.
 */
class FamilyRemoteDataSourceImpl(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val baseUrl: String = "http://10.0.2.2:8000"
) : FamilyRemoteDataSource {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun getAuthHeader(): String? {
        val result = authRepository.getIdToken(forceRefresh = false)
        return result.getOrNull()
    }

    override suspend fun getFamilyMembers(): List<FamilyMember> = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/family")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "[]"
                        val jsonArr = JSONArray(body)
                        val members = mutableListOf<FamilyMember>()
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            members.add(
                                FamilyMember(
                                    id = obj.getString("id"),
                                    memberId = obj.getString("member_id"),
                                    name = obj.getString("name"),
                                    email = obj.optString("email", ""),
                                    relationship = obj.optString("relationship", "Relative"),
                                    isOnline = obj.optBoolean("is_online", false),
                                    isVoiceEnrolled = obj.optBoolean("is_voice_enrolled", false),
                                    voiceQualityScore = obj.optDouble("voice_quality_score", 0.0).toFloat(),
                                    lastActive = obj.optString("last_active", "Connected")
                                )
                            )
                        }
                        return@withContext members
                    }
                }
            } catch (e: Exception) {
                // Fall back to direct Firestore
            }
        }

        // Direct Firestore fallback
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext emptyList()

        try {
            val querySnapshot = firestore.collection("family_relationships")
                .whereArrayContains("members", currentUid)
                .get()
                .await()

            val members = mutableListOf<FamilyMember>()
            for (doc in querySnapshot.documents) {
                val data = doc.data ?: continue
                val membersList = data["members"] as? List<*> ?: continue
                val otherUid = membersList.firstOrNull { it != currentUid }?.toString() ?: continue
                val name = if (otherUid == data["user_a_id"]) {
                    data["sender_name"]?.toString() ?: "Family Member"
                } else {
                    data["recipient_name"]?.toString() ?: "Family Member"
                }
                val email = if (otherUid == data["user_a_id"]) {
                    data["sender_email"]?.toString() ?: ""
                } else {
                    data["recipient_email"]?.toString() ?: ""
                }

                members.add(
                    FamilyMember(
                        id = doc.id,
                        memberId = otherUid,
                        name = name,
                        email = email,
                        relationship = data["relationship"]?.toString() ?: "Relative",
                        isOnline = false,
                        isVoiceEnrolled = false,
                        voiceQualityScore = 0.0f,
                        lastActive = "Connected"
                    )
                )
            }
            members
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun searchUserByEmail(email: String): UserSearchInfo = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/family/search?email=$cleanEmail")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val obj = JSONObject(body)
                        return@withContext UserSearchInfo(
                            exists = obj.getBoolean("exists"),
                            email = obj.getString("email"),
                            name = if (obj.isNull("name")) null else obj.getString("name")
                        )
                    } else {
                        val err = parseErrorDetail(response.body?.string())
                        throw IOException(err)
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                // Fall back to Firestore
            }
        }

        // Direct Firestore fallback
        try {
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                val doc = querySnapshot.documents[0]
                val name = doc.getString("display_name") ?: doc.getString("name")
                UserSearchInfo(exists = true, email = cleanEmail, name = name)
            } else {
                UserSearchInfo(exists = false, email = cleanEmail, name = null)
            }
        } catch (e: Exception) {
            UserSearchInfo(exists = false, email = cleanEmail, name = null)
        }
    }

    override suspend fun sendInvitation(recipientEmail: String, relationship: String): FamilyInvitation = withContext(Dispatchers.IO) {
        val cleanEmail = recipientEmail.trim().lowercase()
        val token = getAuthHeader()
        if (token != null) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("recipient_email", cleanEmail)
                    put("relationship", relationship)
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/api/v1/family/invitations")
                    .addHeader("Authorization", "Bearer $token")
                    .post(jsonPayload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val obj = JSONObject(body)
                        return@withContext FamilyInvitation(
                            id = obj.getString("id"),
                            senderId = obj.getString("sender_id"),
                            senderName = obj.getString("sender_name"),
                            senderEmail = obj.getString("sender_email"),
                            recipientEmail = obj.getString("recipient_email"),
                            recipientId = if (obj.isNull("recipient_id")) null else obj.getString("recipient_id"),
                            relationship = obj.getString("relationship"),
                            status = obj.getString("status"),
                            createdAt = obj.getString("created_at")
                        )
                    } else {
                        val err = parseErrorDetail(response.body?.string())
                        throw IOException(err)
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                // Fall through to Firestore
            }
        }

        // Direct Firestore fallback
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Must be logged in to send invitations.")
        val senderEmail = currentUser.email.orEmpty().lowercase()

        if (cleanEmail == senderEmail) {
            throw IllegalArgumentException("You cannot invite yourself to your own family circle.")
        }

        val invId = "inv_${UUID.randomUUID().toString().take(12)}"
        val docData = hashMapOf(
            "id" to invId,
            "sender_id" to currentUser.uid,
            "sender_name" to (currentUser.displayName ?: "Family Guardian"),
            "sender_email" to senderEmail,
            "recipient_email" to cleanEmail,
            "relationship" to relationship,
            "status" to "pending",
            "created_at" to System.currentTimeMillis().toString()
        )

        firestore.collection("family_invitations").document(invId).set(docData).await()

        FamilyInvitation(
            id = invId,
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: "Family Guardian",
            senderEmail = senderEmail,
            recipientEmail = cleanEmail,
            relationship = relationship,
            status = "pending",
            createdAt = System.currentTimeMillis().toString()
        )
    }

    override suspend fun getInvitations(): Pair<List<FamilyInvitation>, List<FamilyInvitation>> = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/family/invitations")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val obj = JSONObject(body)
                        val inArr = obj.getJSONArray("incoming")
                        val outArr = obj.getJSONArray("outgoing")

                        val incoming = mutableListOf<FamilyInvitation>()
                        for (i in 0 until inArr.length()) {
                            val inv = inArr.getJSONObject(i)
                            incoming.add(parseInvitationObj(inv))
                        }

                        val outgoing = mutableListOf<FamilyInvitation>()
                        for (i in 0 until outArr.length()) {
                            val inv = outArr.getJSONObject(i)
                            outgoing.add(parseInvitationObj(inv))
                        }

                        return@withContext Pair(incoming, outgoing)
                    }
                }
            } catch (e: Exception) {
                // Fall back to Firestore
            }
        }

        // Direct Firestore fallback
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: return@withContext Pair(emptyList(), emptyList())
        val userEmail = currentUser.email.orEmpty().lowercase()

        try {
            val inSnap = firestore.collection("family_invitations")
                .whereEqualTo("recipient_email", userEmail)
                .whereEqualTo("status", "pending")
                .get()
                .await()

            val incoming = inSnap.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                FamilyInvitation(
                    id = doc.id,
                    senderId = data["sender_id"]?.toString() ?: "",
                    senderName = data["sender_name"]?.toString() ?: "Family Member",
                    senderEmail = data["sender_email"]?.toString() ?: "",
                    recipientEmail = userEmail,
                    relationship = data["relationship"]?.toString() ?: "Relative",
                    status = "pending",
                    createdAt = data["created_at"]?.toString() ?: ""
                )
            }

            val outSnap = firestore.collection("family_invitations")
                .whereEqualTo("sender_id", currentUser.uid)
                .whereEqualTo("status", "pending")
                .get()
                .await()

            val outgoing = outSnap.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                FamilyInvitation(
                    id = doc.id,
                    senderId = currentUser.uid,
                    senderName = currentUser.displayName ?: "Family Guardian",
                    senderEmail = userEmail,
                    recipientEmail = data["recipient_email"]?.toString() ?: "",
                    relationship = data["relationship"]?.toString() ?: "Relative",
                    status = "pending",
                    createdAt = data["created_at"]?.toString() ?: ""
                )
            }

            Pair(incoming, outgoing)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    override suspend fun acceptInvitation(invitationId: String): Unit = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/family/invitations/$invitationId/accept")
                    .addHeader("Authorization", "Bearer $token")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext
                    val err = parseErrorDetail(response.body?.string())
                    throw IOException(err)
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                // Fall back to Firestore
            }
        }

        // Firestore accept
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Must be logged in.")
        val invRef = firestore.collection("family_invitations").document(invitationId)
        val snap = invRef.get().await()
        if (!snap.exists()) throw IllegalArgumentException("Invitation not found.")
        val data = snap.data ?: throw IllegalArgumentException("Invalid invitation.")

        val senderId = data["sender_id"]?.toString() ?: ""
        val relId = "rel_${minOf(senderId, currentUser.uid)}_${maxOf(senderId, currentUser.uid)}"

        firestore.runBatch { batch ->
            batch.update(invRef, "status", "accepted", "recipient_id", currentUser.uid)
            val relRef = firestore.collection("family_relationships").document(relId)
            batch.set(
                relRef,
                hashMapOf(
                    "id" to relId,
                    "members" to listOf(senderId, currentUser.uid),
                    "user_a_id" to senderId,
                    "user_b_id" to currentUser.uid,
                    "sender_name" to (data["sender_name"] ?: "Family Member"),
                    "sender_email" to (data["sender_email"] ?: ""),
                    "recipient_name" to (currentUser.displayName ?: "Family Member"),
                    "recipient_email" to (currentUser.email ?: ""),
                    "relationship" to (data["relationship"] ?: "Relative"),
                    "created_at" to System.currentTimeMillis().toString()
                )
            )
        }.await()
    }

    override suspend fun rejectInvitation(invitationId: String): Unit = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/family/invitations/$invitationId/reject")
                    .addHeader("Authorization", "Bearer $token")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext
                    val err = parseErrorDetail(response.body?.string())
                    throw IOException(err)
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                // Fall back
            }
        }

        firestore.collection("family_invitations").document(invitationId)
            .update("status", "rejected")
            .await()
    }

    override suspend fun cancelInvitation(invitationId: String): Unit = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/family/invitations/$invitationId")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext
                    val err = parseErrorDetail(response.body?.string())
                    throw IOException(err)
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                // Fall back
            }
        }

        firestore.collection("family_invitations").document(invitationId)
            .update("status", "cancelled")
            .await()
    }

    override suspend fun removeFamilyRelationship(relationshipId: String): Unit = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/family/$relationshipId")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext
                    val err = parseErrorDetail(response.body?.string())
                    throw IOException(err)
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                // Fall back
            }
        }

        firestore.collection("family_relationships").document(relationshipId).delete().await()
    }

    private fun parseErrorDetail(body: String?): String {
        return try {
            if (body != null) {
                val json = JSONObject(body)
                json.optString("detail", "Operation failed. Please try again.")
            } else "Operation failed."
        } catch (e: Exception) {
            "An error occurred."
        }
    }

    private fun parseInvitationObj(obj: JSONObject): FamilyInvitation {
        return FamilyInvitation(
            id = obj.getString("id"),
            senderId = obj.getString("sender_id"),
            senderName = obj.getString("sender_name"),
            senderEmail = obj.getString("sender_email"),
            recipientEmail = obj.getString("recipient_email"),
            recipientId = if (obj.isNull("recipient_id")) null else obj.getString("recipient_id"),
            relationship = obj.getString("relationship"),
            status = obj.getString("status"),
            createdAt = obj.getString("created_at")
        )
    }
}
