import logging
import uuid
from datetime import datetime, timezone
from typing import List, Optional, Dict, Any

from fastapi import HTTPException, status
from firebase_admin import auth

from app.core.firebase import get_firestore_client
from app.auth.models import AuthenticatedUser
from app.family.models import (
    FamilyMemberResponse,
    InvitationResponse,
    InvitationsListResponse,
    ActionResponse,
    UserSearchResponse
)

logger = logging.getLogger("echoshield.family")


class FamilyService:
    """
    Business logic and Firestore data layer for EchoShield Family Management.
    Enforces server-side authorization, duplicate prevention, and privacy protection.
    When in production mode (use_in_memory=False), all operations strictly require
    a functional Firestore connection; silent in-memory fallback is disabled.
    """

    def __init__(self, use_in_memory: bool = False):
        self.use_in_memory = use_in_memory
        # In-memory stores used strictly for hermetic unit tests
        self._mem_users: Dict[str, Dict[str, Any]] = {}
        self._mem_relationships: Dict[str, Dict[str, Any]] = {}
        self._mem_invitations: Dict[str, Dict[str, Any]] = {}

    def _get_firestore(self):
        """
        Retrieves Firestore client in production.
        In unit test mode (use_in_memory=True), returns None.
        In production, raises HTTP 503 if Firestore cannot be reached.
        """
        if self.use_in_memory:
            return None
        return get_firestore_client()

    def search_user_by_email(self, email: str, current_user: AuthenticatedUser) -> UserSearchResponse:
        """
        Safely queries whether an email is registered in EchoShield.
        Does NOT expose private identifiers, biometrics, or contact details.
        """
        clean_email = email.strip().lower()
        if clean_email == current_user.email.lower():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="You cannot search for your own account."
            )

        if self.use_in_memory:
            for user_data in self._mem_users.values():
                if user_data.get("email", "").lower() == clean_email:
                    return UserSearchResponse(
                        exists=True,
                        email=clean_email,
                        name=user_data.get("display_name")
                    )
            return UserSearchResponse(exists=False, email=clean_email, name=None)

        # Production path: Firestore & Firebase Auth
        db = self._get_firestore()
        try:
            users_ref = db.collection("users")
            query = users_ref.where("email", "==", clean_email).limit(1).stream()
            for doc in query:
                data = doc.to_dict()
                return UserSearchResponse(
                    exists=True,
                    email=clean_email,
                    name=data.get("display_name") or data.get("name")
                )
        except Exception as e:
            logger.warning("Firestore search query note: %s", str(e))

        try:
            user_record = auth.get_user_by_email(clean_email)
            return UserSearchResponse(
                exists=True,
                email=clean_email,
                name=user_record.display_name
            )
        except auth.UserNotFoundError:
            return UserSearchResponse(exists=False, email=clean_email, name=None)
        except Exception as e:
            logger.error("Auth search query error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"User search failed: {str(e)}"
            )

    def send_invitation(
        self,
        sender: AuthenticatedUser,
        recipient_email: str,
        relationship: str
    ) -> InvitationResponse:
        """
        Creates and dispatches a family circle invitation.
        Strictly prevents self-invitations, duplicate invitations, and duplicate relationships.
        """
        clean_recipient_email = recipient_email.strip().lower()
        sender_email = (sender.email or "").strip().lower()

        # 1. Prevent self-invitation
        if clean_recipient_email == sender_email:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="You cannot invite yourself to your own family circle."
            )

        # 2. Check for existing active relationships
        existing_family = self.get_family_members(sender.uid)
        for member in existing_family:
            if member.email.lower() == clean_recipient_email:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"You are already connected in a family circle with {clean_recipient_email}."
                )

        now_iso = datetime.now(timezone.utc).isoformat()
        invitation_id = f"inv_{uuid.uuid4().hex[:12]}"

        # Test mode path
        if self.use_in_memory:
            recipient_id: Optional[str] = None
            for uid, user_data in self._mem_users.items():
                if user_data.get("email", "").lower() == clean_recipient_email:
                    recipient_id = uid
                    break

            for inv in self._mem_invitations.values():
                if inv["status"] == "pending":
                    if inv["sender_id"] == sender.uid and inv["recipient_email"] == clean_recipient_email:
                        raise HTTPException(
                            status_code=status.HTTP_409_CONFLICT,
                            detail="A pending invitation to this family member already exists."
                        )
                    if inv["sender_email"] == clean_recipient_email and inv["recipient_email"] == sender_email:
                        raise HTTPException(
                            status_code=status.HTTP_409_CONFLICT,
                            detail="This family member has already sent you an invitation. Please check your incoming requests."
                        )

            doc_data = {
                "id": invitation_id,
                "sender_id": sender.uid,
                "sender_name": sender.name or "Family Guardian",
                "sender_email": sender_email,
                "recipient_email": clean_recipient_email,
                "recipient_id": recipient_id,
                "relationship": relationship,
                "status": "pending",
                "created_at": now_iso,
                "updated_at": now_iso
            }
            self._mem_invitations[invitation_id] = doc_data
            return InvitationResponse(**doc_data)

        # Production path: Firestore & Auth
        recipient_id = None
        try:
            recipient_record = auth.get_user_by_email(clean_recipient_email)
            recipient_id = recipient_record.uid
        except auth.UserNotFoundError:
            recipient_id = None
        except Exception as e:
            logger.warning("Recipient auth lookup note: %s", str(e))

        db = self._get_firestore()
        try:
            inv_ref = db.collection("family_invitations")
            # Check outgoing duplicate
            dup_query = inv_ref.where("sender_id", "==", sender.uid)\
                               .where("recipient_email", "==", clean_recipient_email)\
                               .where("status", "==", "pending")\
                               .limit(1).stream()
            if any(dup_query):
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="A pending invitation to this family member already exists."
                )

            # Check reverse duplicate
            rev_query = inv_ref.where("sender_email", "==", clean_recipient_email)\
                               .where("recipient_email", "==", sender_email)\
                               .where("status", "==", "pending")\
                               .limit(1).stream()
            if any(rev_query):
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="This family member has already sent you an invitation. Please check your incoming requests."
                )

            doc_data = {
                "id": invitation_id,
                "sender_id": sender.uid,
                "sender_name": sender.name or "Family Guardian",
                "sender_email": sender_email,
                "recipient_email": clean_recipient_email,
                "recipient_id": recipient_id,
                "relationship": relationship,
                "status": "pending",
                "created_at": now_iso,
                "updated_at": now_iso
            }
            inv_ref.document(invitation_id).set(doc_data)
            return InvitationResponse(**doc_data)
        except HTTPException:
            raise
        except Exception as e:
            logger.error("Firestore invitation write error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to record family invitation in Firestore: {str(e)}"
            )

    def get_invitations(self, user: AuthenticatedUser) -> InvitationsListResponse:
        """
        Retrieves all incoming and outgoing invitations for the authenticated user.
        Users can ONLY see invitations where they are either sender or recipient.
        """
        user_email = (user.email or "").strip().lower()

        if self.use_in_memory:
            incoming: List[InvitationResponse] = []
            outgoing: List[InvitationResponse] = []
            for inv in self._mem_invitations.values():
                if inv["status"] == "pending":
                    if inv["recipient_email"] == user_email or inv.get("recipient_id") == user.uid:
                        incoming.append(InvitationResponse(**inv))
                    elif inv["sender_id"] == user.uid:
                        outgoing.append(InvitationResponse(**inv))
            return InvitationsListResponse(incoming=incoming, outgoing=outgoing)

        # Production path: Firestore
        db = self._get_firestore()
        incoming = []
        outgoing = []
        try:
            inv_ref = db.collection("family_invitations")

            in_query = inv_ref.where("recipient_email", "==", user_email)\
                              .where("status", "==", "pending")\
                              .stream()
            for doc in in_query:
                incoming.append(InvitationResponse(**doc.to_dict()))

            out_query = inv_ref.where("sender_id", "==", user.uid)\
                               .where("status", "==", "pending")\
                               .stream()
            for doc in out_query:
                outgoing.append(InvitationResponse(**doc.to_dict()))

            return InvitationsListResponse(incoming=incoming, outgoing=outgoing)
        except Exception as e:
            logger.error("Firestore invitations query error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to retrieve invitations from Firestore: {str(e)}"
            )

    def accept_invitation(self, invitation_id: str, recipient: AuthenticatedUser) -> ActionResponse:
        """
        Accepts a pending family invitation.
        Verifies recipient identity, updates invitation status, and creates bidirectional family relationship.
        """
        recipient_email = (recipient.email or "").strip().lower()
        now_iso = datetime.now(timezone.utc).isoformat()

        if self.use_in_memory:
            invitation_data = self._mem_invitations.get(invitation_id)
            if not invitation_data:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invitation not found.")
            if invitation_data["recipient_email"].lower() != recipient_email and invitation_data.get("recipient_id") != recipient.uid:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Security violation: You are not authorized to accept this invitation."
                )
            if invitation_data["status"] != "pending":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Cannot accept invitation with status '{invitation_data['status']}'."
                )

            sender_id = invitation_data["sender_id"]
            rel_id = f"rel_{min(sender_id, recipient.uid)}_{max(sender_id, recipient.uid)}"
            rel_data = {
                "id": rel_id,
                "members": [sender_id, recipient.uid],
                "user_a_id": sender_id,
                "user_b_id": recipient.uid,
                "sender_name": invitation_data["sender_name"],
                "sender_email": invitation_data["sender_email"],
                "recipient_name": recipient.name or "Family Member",
                "recipient_email": recipient_email,
                "relationship": invitation_data["relationship"],
                "created_at": now_iso,
                "updated_at": now_iso
            }
            invitation_data["status"] = "accepted"
            invitation_data["recipient_id"] = recipient.uid
            invitation_data["updated_at"] = now_iso
            self._mem_relationships[rel_id] = rel_data
            return ActionResponse(
                status="accepted",
                message="Family invitation accepted. Member added to your circle.",
                relationship_id=rel_id
            )

        # Production path: Firestore
        db = self._get_firestore()
        try:
            inv_doc = db.collection("family_invitations").document(invitation_id).get()
            if not inv_doc.exists:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invitation not found.")
            invitation_data = inv_doc.to_dict()

            if invitation_data["recipient_email"].lower() != recipient_email and invitation_data.get("recipient_id") != recipient.uid:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Security violation: You are not authorized to accept this invitation."
                )
            if invitation_data["status"] != "pending":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Cannot accept invitation with status '{invitation_data['status']}'."
                )

            sender_id = invitation_data["sender_id"]
            rel_id = f"rel_{min(sender_id, recipient.uid)}_{max(sender_id, recipient.uid)}"
            rel_data = {
                "id": rel_id,
                "members": [sender_id, recipient.uid],
                "user_a_id": sender_id,
                "user_b_id": recipient.uid,
                "sender_name": invitation_data["sender_name"],
                "sender_email": invitation_data["sender_email"],
                "recipient_name": recipient.name or "Family Member",
                "recipient_email": recipient_email,
                "relationship": invitation_data["relationship"],
                "created_at": now_iso,
                "updated_at": now_iso
            }

            db.collection("family_invitations").document(invitation_id).update({
                "status": "accepted",
                "recipient_id": recipient.uid,
                "updated_at": now_iso
            })
            db.collection("family_relationships").document(rel_id).set(rel_data)

            return ActionResponse(
                status="accepted",
                message="Family invitation accepted. Member added to your circle.",
                relationship_id=rel_id
            )
        except HTTPException:
            raise
        except Exception as e:
            logger.error("Firestore accept invitation error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to accept invitation in Firestore: {str(e)}"
            )

    def reject_invitation(self, invitation_id: str, recipient: AuthenticatedUser) -> ActionResponse:
        """
        Declines a pending family invitation.
        Verifies recipient identity before status modification.
        """
        recipient_email = (recipient.email or "").strip().lower()
        now_iso = datetime.now(timezone.utc).isoformat()

        if self.use_in_memory:
            invitation_data = self._mem_invitations.get(invitation_id)
            if not invitation_data:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invitation not found.")
            if invitation_data["recipient_email"].lower() != recipient_email and invitation_data.get("recipient_id") != recipient.uid:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Security violation: You are not authorized to decline this invitation."
                )
            if invitation_data["status"] != "pending":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Cannot decline invitation with status '{invitation_data['status']}'."
                )
            invitation_data["status"] = "rejected"
            invitation_data["updated_at"] = now_iso
            return ActionResponse(status="rejected", message="Invitation declined.")

        # Production path: Firestore
        db = self._get_firestore()
        try:
            inv_doc = db.collection("family_invitations").document(invitation_id).get()
            if not inv_doc.exists:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invitation not found.")
            invitation_data = inv_doc.to_dict()

            if invitation_data["recipient_email"].lower() != recipient_email and invitation_data.get("recipient_id") != recipient.uid:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Security violation: You are not authorized to decline this invitation."
                )
            if invitation_data["status"] != "pending":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Cannot decline invitation with status '{invitation_data['status']}'."
                )

            db.collection("family_invitations").document(invitation_id).update({
                "status": "rejected",
                "updated_at": now_iso
            })
            return ActionResponse(status="rejected", message="Invitation declined.")
        except HTTPException:
            raise
        except Exception as e:
            logger.error("Firestore reject error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to decline invitation in Firestore: {str(e)}"
            )

    def cancel_invitation(self, invitation_id: str, sender: AuthenticatedUser) -> ActionResponse:
        """
        Cancels an outgoing pending invitation.
        Strictly enforces that only the original sender can cancel.
        """
        now_iso = datetime.now(timezone.utc).isoformat()

        if self.use_in_memory:
            invitation_data = self._mem_invitations.get(invitation_id)
            if not invitation_data:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invitation not found.")
            if invitation_data["sender_id"] != sender.uid:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Security violation: Only the sender can cancel this invitation."
                )
            if invitation_data["status"] != "pending":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Cannot cancel an invitation with status '{invitation_data['status']}'."
                )
            invitation_data["status"] = "cancelled"
            invitation_data["updated_at"] = now_iso
            return ActionResponse(status="cancelled", message="Invitation cancelled successfully.")

        # Production path: Firestore
        db = self._get_firestore()
        try:
            inv_doc = db.collection("family_invitations").document(invitation_id).get()
            if not inv_doc.exists:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invitation not found.")
            invitation_data = inv_doc.to_dict()

            if invitation_data["sender_id"] != sender.uid:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Security violation: Only the sender can cancel this invitation."
                )
            if invitation_data["status"] != "pending":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Cannot cancel an invitation with status '{invitation_data['status']}'."
                )

            db.collection("family_invitations").document(invitation_id).update({
                "status": "cancelled",
                "updated_at": now_iso
            })
            return ActionResponse(status="cancelled", message="Invitation cancelled successfully.")
        except HTTPException:
            raise
        except Exception as e:
            logger.error("Firestore cancel error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to cancel invitation in Firestore: {str(e)}"
            )

    def get_family_members(self, user_id: str) -> List[FamilyMemberResponse]:
        """
        Lists all connected family members for the specified user.
        Derives members strictly from authenticated relationship documents.
        """
        if self.use_in_memory:
            members: List[FamilyMemberResponse] = []
            for data in self._mem_relationships.values():
                if user_id in data["members"]:
                    other_uid = [m for m in data["members"] if m != user_id][0]
                    if other_uid == data.get("user_a_id"):
                        name = data.get("sender_name", "Family Member")
                        email = data.get("sender_email", "")
                    else:
                        name = data.get("recipient_name", "Family Member")
                        email = data.get("recipient_email", "")

                    user_meta = self._mem_users.get(other_uid, {})
                    members.append(FamilyMemberResponse(
                        id=data["id"],
                        member_id=other_uid,
                        name=user_meta.get("display_name", name),
                        email=email,
                        relationship=data.get("relationship", "Relative"),
                        is_online=False,
                        is_voice_enrolled=user_meta.get("is_voice_enrolled", False),
                        voice_quality_score=user_meta.get("voice_quality_score", 0.0),
                        last_active="Connected"
                    ))
            return members

        # Production path: Firestore
        db = self._get_firestore()
        members = []
        try:
            rel_ref = db.collection("family_relationships")
            query = rel_ref.where("members", "array_contains", user_id).stream()
            for doc in query:
                data = doc.to_dict()
                other_uid = [m for m in data["members"] if m != user_id][0]
                if other_uid == data.get("user_a_id"):
                    name = data.get("sender_name", "Family Member")
                    email = data.get("sender_email", "")
                else:
                    name = data.get("recipient_name", "Family Member")
                    email = data.get("recipient_email", "")

                is_enrolled = False
                score = 0.0
                try:
                    u_doc = db.collection("users").document(other_uid).get()
                    if u_doc.exists:
                        u_data = u_doc.to_dict()
                        is_enrolled = u_data.get("is_voice_enrolled", False)
                        score = float(u_data.get("voice_quality_score", 0.0))
                        name = u_data.get("display_name") or name
                except Exception:
                    pass

                members.append(FamilyMemberResponse(
                    id=data["id"],
                    member_id=other_uid,
                    name=name,
                    email=email,
                    relationship=data.get("relationship", "Relative"),
                    is_online=False,
                    is_voice_enrolled=is_enrolled,
                    voice_quality_score=score,
                    last_active="Connected"
                ))
            return members
        except Exception as e:
            logger.error("Firestore query family error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to query family relationships from Firestore: {str(e)}"
            )

    def remove_family_relationship(self, relationship_id: str, user: AuthenticatedUser) -> ActionResponse:
        """
        Securely severs a family relationship.
        Requires that the caller is an active participant in the relationship.
        Does NOT delete any user account or voice biometric profile.
        """
        if self.use_in_memory:
            rel_data = self._mem_relationships.get(relationship_id)
            if not rel_data:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Family relationship not found.")
            if user.uid not in rel_data.get("members", []):
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Security violation: You are not authorized to remove this family relationship."
                )
            self._mem_relationships.pop(relationship_id, None)
            return ActionResponse(
                status="removed",
                message="Family member removed from your circle successfully."
            )

        # Production path: Firestore
        db = self._get_firestore()
        try:
            rel_doc = db.collection("family_relationships").document(relationship_id).get()
            if not rel_doc.exists:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Family relationship not found.")
            rel_data = rel_doc.to_dict()

            if user.uid not in rel_data.get("members", []):
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Security violation: You are not authorized to remove this family relationship."
                )

            db.collection("family_relationships").document(relationship_id).delete()
            return ActionResponse(
                status="removed",
                message="Family member removed from your circle successfully."
            )
        except HTTPException:
            raise
        except Exception as e:
            logger.error("Firestore delete error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to delete relationship in Firestore: {str(e)}"
            )


# Global service instance
family_service = FamilyService()
