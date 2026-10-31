import pytest
from unittest.mock import patch
from fastapi.testclient import TestClient
from fastapi import status

import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../../backend")))

from app.main import app

client = TestClient(app)

# Test User Fixtures
USER_A = {
    "uid": "uid_account_a_111",
    "email": "sarah.a@echoshield.family",
    "name": "Sarah Henderson",
    "email_verified": True
}

USER_B = {
    "uid": "uid_account_b_222",
    "email": "david.b@echoshield.family",
    "name": "David Henderson",
    "email_verified": True
}

USER_ATTACKER = {
    "uid": "uid_attacker_999",
    "email": "intruder@malicious.org",
    "name": "Intruder",
    "email_verified": True
}


@pytest.fixture(autouse=True)
def isolated_family_service():
    """Ensure a clean, in-memory isolated service instance for each test run."""
    from app.family.router import family_service
    # Reset internal memory state
    family_service.use_in_memory = True
    family_service._mem_users = {
        USER_A["uid"]: USER_A,
        USER_B["uid"]: USER_B,
        USER_ATTACKER["uid"]: USER_ATTACKER
    }
    family_service._mem_relationships.clear()
    family_service._mem_invitations.clear()
    yield family_service


def test_scenario_step_1_to_8_full_family_lifecycle():
    """
    Executes the comprehensive 8-step family management lifecycle:
    1. Account A sends invitation to Account B.
    2. Account B receives invitation in incoming requests.
    3. Account B accepts invitation.
    4. Both accounts see the correct family relationship in their circle.
    5. Duplicate invitation is rejected with 409 Conflict.
    6. Unauthorized user cannot accept another user's invitation (403 Forbidden).
    7. Account A removes the relationship.
    8. Both accounts see the updated relationship state (empty circle).
    """

    # --- Step 1: Account A sends invitation to Account B ---
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        invite_res = client.post(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_a"},
            json={
                "recipient_email": USER_B["email"],
                "relationship": "Child"
            }
        )
        assert invite_res.status_code == status.HTTP_201_CREATED
        invite_data = invite_res.json()
        invitation_id = invite_data["id"]
        assert invite_data["sender_id"] == USER_A["uid"]
        assert invite_data["recipient_email"] == USER_B["email"]
        assert invite_data["relationship"] == "Child"
        assert invite_data["status"] == "pending"

    # --- Step 2: Account B receives invitation ---
    with patch("app.auth.security.verify_firebase_token", return_value=USER_B):
        b_invites_res = client.get(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_b"}
        )
        assert b_invites_res.status_code == status.HTTP_200_OK
        b_invites = b_invites_res.json()
        assert len(b_invites["incoming"]) == 1
        assert b_invites["incoming"][0]["id"] == invitation_id
        assert b_invites["incoming"][0]["sender_email"] == USER_A["email"]

    # --- Step 5 (Pre-test): Duplicate invitation attempt while pending is rejected ---
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        dup_res = client.post(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_a"},
            json={
                "recipient_email": USER_B["email"],
                "relationship": "Child"
            }
        )
        assert dup_res.status_code == status.HTTP_409_CONFLICT
        assert "already exists" in dup_res.json()["detail"].lower()

    # --- Step 6: Unauthorized user cannot accept another user's invitation ---
    with patch("app.auth.security.verify_firebase_token", return_value=USER_ATTACKER):
        hijack_res = client.post(
            f"/api/v1/family/invitations/{invitation_id}/accept",
            headers={"Authorization": "Bearer token_attacker"}
        )
        assert hijack_res.status_code == status.HTTP_403_FORBIDDEN
        assert "security violation" in hijack_res.json()["detail"].lower()

    # --- Step 3: Account B accepts invitation ---
    with patch("app.auth.security.verify_firebase_token", return_value=USER_B):
        accept_res = client.post(
            f"/api/v1/family/invitations/{invitation_id}/accept",
            headers={"Authorization": "Bearer token_account_b"}
        )
        assert accept_res.status_code == status.HTTP_200_OK
        accept_data = accept_res.json()
        assert accept_data["status"] == "accepted"
        relationship_id = accept_data["relationship_id"]
        assert relationship_id is not None

    # --- Step 4: Both accounts see the correct family relationship ---
    # Account A checks family list:
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        family_a_res = client.get(
            "/api/v1/family",
            headers={"Authorization": "Bearer token_account_a"}
        )
        assert family_a_res.status_code == status.HTTP_200_OK
        members_a = family_a_res.json()
        assert len(members_a) == 1
        assert members_a[0]["member_id"] == USER_B["uid"]
        assert members_a[0]["email"] == USER_B["email"]
        assert members_a[0]["relationship"] == "Child"

    # Account B checks family list:
    with patch("app.auth.security.verify_firebase_token", return_value=USER_B):
        family_b_res = client.get(
            "/api/v1/family",
            headers={"Authorization": "Bearer token_account_b"}
        )
        assert family_b_res.status_code == status.HTTP_200_OK
        members_b = family_b_res.json()
        assert len(members_b) == 1
        assert members_b[0]["member_id"] == USER_A["uid"]
        assert members_b[0]["email"] == USER_A["email"]

    # --- Step 5 (Post-acceptance): Duplicate invitation to connected member is rejected ---
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        dup_connected_res = client.post(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_a"},
            json={
                "recipient_email": USER_B["email"],
                "relationship": "Child"
            }
        )
        assert dup_connected_res.status_code == status.HTTP_409_CONFLICT
        assert "already connected" in dup_connected_res.json()["detail"].lower()

    # --- Step 7: Account A removes the relationship ---
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        delete_res = client.delete(
            f"/api/v1/family/{relationship_id}",
            headers={"Authorization": "Bearer token_account_a"}
        )
        assert delete_res.status_code == status.HTTP_200_OK
        assert delete_res.json()["status"] == "removed"

    # --- Step 8: Both accounts see the updated relationship state (empty) ---
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        empty_a_res = client.get(
            "/api/v1/family",
            headers={"Authorization": "Bearer token_account_a"}
        )
        assert empty_a_res.status_code == status.HTTP_200_OK
        assert len(empty_a_res.json()) == 0

    with patch("app.auth.security.verify_firebase_token", return_value=USER_B):
        empty_b_res = client.get(
            "/api/v1/family",
            headers={"Authorization": "Bearer token_account_b"}
        )
        assert empty_b_res.status_code == status.HTTP_200_OK
        assert len(empty_b_res.json()) == 0


def test_prevent_self_invitation():
    """Verify users cannot invite themselves."""
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        res = client.post(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_a"},
            json={
                "recipient_email": USER_A["email"],
                "relationship": "Self"
            }
        )
        assert res.status_code == status.HTTP_400_BAD_REQUEST
        assert "cannot invite yourself" in res.json()["detail"].lower()


def test_reject_family_invitation():
    """Verify recipient can decline an incoming invitation."""
    # Account A creates invite
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        create_res = client.post(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_a"},
            json={
                "recipient_email": USER_B["email"],
                "relationship": "Sibling"
            }
        )
        invitation_id = create_res.json()["id"]

    # Account B rejects it
    with patch("app.auth.security.verify_firebase_token", return_value=USER_B):
        reject_res = client.post(
            f"/api/v1/family/invitations/{invitation_id}/reject",
            headers={"Authorization": "Bearer token_account_b"}
        )
        assert reject_res.status_code == status.HTTP_200_OK
        assert reject_res.json()["status"] == "rejected"

    # Account B incoming list should now be empty (only pending are shown)
    with patch("app.auth.security.verify_firebase_token", return_value=USER_B):
        list_res = client.get(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_b"}
        )
        assert len(list_res.json()["incoming"]) == 0


def test_cancel_outgoing_invitation_by_sender():
    """Verify sender can cancel their pending outgoing invitation."""
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        create_res = client.post(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_a"},
            json={
                "recipient_email": USER_B["email"],
                "relationship": "Parent"
            }
        )
        invitation_id = create_res.json()["id"]

        # Cancel
        cancel_res = client.delete(
            f"/api/v1/family/invitations/{invitation_id}",
            headers={"Authorization": "Bearer token_account_a"}
        )
        assert cancel_res.status_code == status.HTTP_200_OK
        assert cancel_res.json()["status"] == "cancelled"

    # Outgoing list should now be empty
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        out_res = client.get(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_a"}
        )
        assert len(out_res.json()["outgoing"]) == 0


def test_unauthorized_user_cannot_cancel_others_invitation():
    """Verify non-sender cannot cancel another user's outgoing invitation."""
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        create_res = client.post(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_a"},
            json={
                "recipient_email": USER_B["email"],
                "relationship": "Parent"
            }
        )
        invitation_id = create_res.json()["id"]

    # Attacker tries to cancel
    with patch("app.auth.security.verify_firebase_token", return_value=USER_ATTACKER):
        cancel_res = client.delete(
            f"/api/v1/family/invitations/{invitation_id}",
            headers={"Authorization": "Bearer token_attacker"}
        )
        assert cancel_res.status_code == status.HTTP_403_FORBIDDEN


def test_unauthorized_user_cannot_delete_others_relationship():
    """Verify non-participant cannot delete an existing relationship."""
    # First create a relationship between A and B
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        create_res = client.post(
            "/api/v1/family/invitations",
            headers={"Authorization": "Bearer token_account_a"},
            json={"recipient_email": USER_B["email"], "relationship": "Spouse"}
        )
        invitation_id = create_res.json()["id"]

    with patch("app.auth.security.verify_firebase_token", return_value=USER_B):
        accept_res = client.post(
            f"/api/v1/family/invitations/{invitation_id}/accept",
            headers={"Authorization": "Bearer token_account_b"}
        )
        relationship_id = accept_res.json()["relationship_id"]

    # Attacker tries to delete relationship
    with patch("app.auth.security.verify_firebase_token", return_value=USER_ATTACKER):
        delete_res = client.delete(
            f"/api/v1/family/{relationship_id}",
            headers={"Authorization": "Bearer token_attacker"}
        )
        assert delete_res.status_code == status.HTTP_403_FORBIDDEN


def test_safe_email_search():
    """Verify email search reveals minimal presence info without exposing private metadata."""
    with patch("app.auth.security.verify_firebase_token", return_value=USER_A):
        # Existing user
        res_found = client.get(
            f"/api/v1/family/search?email={USER_B['email']}",
            headers={"Authorization": "Bearer token_account_a"}
        )
        assert res_found.status_code == status.HTTP_200_OK
        data = res_found.json()
        assert data["exists"] is True
        assert data["email"] == USER_B["email"]
        assert "uid" not in data
        assert "voice" not in str(data).lower()

        # Non-existing user
        res_missing = client.get(
            "/api/v1/family/search?email=nonexistent@family.test",
            headers={"Authorization": "Bearer token_account_a"}
        )
        assert res_missing.status_code == status.HTTP_200_OK
        assert res_missing.json()["exists"] is False

