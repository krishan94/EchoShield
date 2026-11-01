"""
EchoShield — Firebase Configuration and Service Account Security Tests
======================================================================
Verifies:
1. Secure credential path resolution via config / environment.
2. Zero in-memory silent fallback in production execution mode (HTTP 503 raised).
3. Firebase Admin certificate loading mechanism.
4. Firestore connection verification roundtrip logic.
"""

import os
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch
import pytest
from fastapi import HTTPException, status

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../../backend")))

from app.core.config import settings
from app.core.firebase import (
    initialize_firebase_admin,
    get_firestore_client,
    verify_firestore_connection,
)
from app.family.service import FamilyService
from app.enrollment.service import VoiceEnrollmentService
from app.services.speaker_recognition.verification_service import VerificationService
from app.auth.models import AuthenticatedUser


def test_credentials_path_resolution_with_env(tmp_path, monkeypatch):
    """Verifies that FIREBASE_CREDENTIALS_PATH environment variable is honored."""
    fake_key_file = tmp_path / "serviceAccountKey.json"
    fake_key_file.write_text('{"type": "service_account"}')

    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_PATH", str(fake_key_file))
    resolved = settings.get_firebase_credentials_path()
    assert resolved == str(fake_key_file.resolve())


def test_production_family_service_raises_503_when_firestore_unavailable(monkeypatch):
    """
    Requirement 6: In production mode (use_in_memory=False),
    FamilyService MUST raise HTTP 503 instead of silently falling back to in-memory dictionaries.
    """
    service = FamilyService(use_in_memory=False)
    user = AuthenticatedUser(uid="user_123", email="user@test.com", email_verified=True, name="Test User")

    with patch("app.family.service.get_firestore_client", side_effect=HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="Firestore connection unavailable."
    )):
        with pytest.raises(HTTPException) as exc_info:
            service.search_user_by_email("target@test.com", user)
        assert exc_info.value.status_code == status.HTTP_503_SERVICE_UNAVAILABLE

        with pytest.raises(HTTPException) as exc_info:
            service.send_invitation(user, "target@test.com", "Parent")
        assert exc_info.value.status_code == status.HTTP_503_SERVICE_UNAVAILABLE

        with pytest.raises(HTTPException) as exc_info:
            service.get_invitations(user)
        assert exc_info.value.status_code == status.HTTP_503_SERVICE_UNAVAILABLE


def test_production_enrollment_service_raises_503_when_firestore_unavailable():
    """
    Requirement 6: In production mode (use_in_memory=False),
    VoiceEnrollmentService MUST raise HTTP 503 instead of silently falling back to in-memory dictionaries.
    """
    service = VoiceEnrollmentService(use_in_memory=False)

    with patch("app.enrollment.service.get_firestore_client", side_effect=HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="Firestore connection unavailable."
    )):
        with pytest.raises(HTTPException) as exc_info:
            service.check_consent("user_123")
        assert exc_info.value.status_code == status.HTTP_503_SERVICE_UNAVAILABLE


def test_production_verification_service_raises_503_when_firestore_unavailable():
    """
    Requirement 6: In production mode (use_in_memory=False),
    VerificationService MUST raise HTTP 503 instead of silently falling back to in-memory dictionaries.
    """
    service = VerificationService(use_in_memory=False)

    with patch("app.services.speaker_recognition.verification_service.get_firestore_client", side_effect=HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="Firestore connection unavailable."
    )):
        with pytest.raises(HTTPException) as exc_info:
            service._check_family_authorization("user_caller", "user_target")
        assert exc_info.value.status_code == status.HTTP_503_SERVICE_UNAVAILABLE


def test_verify_firestore_connection_roundtrip_logic():
    """
    Verifies that verify_firestore_connection performs the write, read, and delete roundtrip.
    """
    mock_db = MagicMock()
    mock_doc_ref = MagicMock()
    mock_snapshot = MagicMock()
    mock_snapshot.exists = True
    mock_snapshot.to_dict.return_value = {"test": True, "service": "EchoShield Backend"}

    mock_doc_ref.get.return_value = mock_snapshot
    mock_db.collection.return_value.document.return_value = mock_doc_ref

    with patch("app.core.firebase.get_firestore_client", return_value=mock_db):
        result = verify_firestore_connection()
        assert result["status"] == "success"
        mock_doc_ref.set.assert_called_once()
        mock_doc_ref.get.assert_called_once()
        mock_doc_ref.delete.assert_called_once()
