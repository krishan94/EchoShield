import pytest
from unittest.mock import patch
from fastapi.testclient import TestClient
from fastapi import HTTPException, status
import firebase_admin.auth as fb_auth

import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../../backend")))

from app.main import app
from app.auth.security import verify_firebase_token

client = TestClient(app)


def test_health_check():
    """Verify backend health endpoint is operational."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "echoshield-backend"
    assert "firebase_project" in data


def test_verify_firebase_token_valid():
    """Verify successful decoding of valid Firebase token claims."""
    mock_claims = {
        "uid": "user_xyz789",
        "email": "guardian@echoshield.family",
        "email_verified": True,
        "name": "Sarah Henderson",
        "auth_time": 1700000000
    }
    with patch("firebase_admin.auth.verify_id_token", return_value=mock_claims):
        claims = verify_firebase_token("valid_mock_token")
        assert claims["uid"] == "user_xyz789"
        assert claims["email"] == "guardian@echoshield.family"
        assert claims["email_verified"] is True


def test_verify_firebase_token_expired():
    """Verify 401 error is raised when token has expired."""
    with patch("firebase_admin.auth.verify_id_token", side_effect=fb_auth.ExpiredIdTokenError("Token expired", None)):
        with pytest.raises(HTTPException) as exc_info:
            verify_firebase_token("expired_token")
        assert exc_info.value.status_code == status.HTTP_401_UNAUTHORIZED
        assert "expired" in exc_info.value.detail.lower()


def test_verify_firebase_token_revoked():
    """Verify 401 error is raised when token is revoked."""
    with patch("firebase_admin.auth.verify_id_token", side_effect=fb_auth.RevokedIdTokenError("Token revoked")):
        with pytest.raises(HTTPException) as exc_info:
            verify_firebase_token("revoked_token")
        assert exc_info.value.status_code == status.HTTP_401_UNAUTHORIZED
        assert "revoked" in exc_info.value.detail.lower()


def test_verify_firebase_token_invalid():
    """Verify 401 error is raised when token is invalid or malformed."""
    with patch("firebase_admin.auth.verify_id_token", side_effect=fb_auth.InvalidIdTokenError("Invalid token")):
        with pytest.raises(HTTPException) as exc_info:
            verify_firebase_token("malformed_token")
        assert exc_info.value.status_code == status.HTTP_401_UNAUTHORIZED
        assert "invalid" in exc_info.value.detail.lower()


def test_get_me_unauthorized_without_header():
    """Verify /api/v1/auth/me rejects requests missing Authorization header."""
    response = client.get("/api/v1/auth/me")
    assert response.status_code == 401 or response.status_code == 403


def test_get_me_authorized_success():
    """Verify /api/v1/auth/me derives user identity from verified token."""
    mock_claims = {
        "uid": "secure_uid_100",
        "email": "verified.member@echoshield.family",
        "email_verified": True,
        "name": "David Henderson"
    }
    with patch("app.auth.security.verify_firebase_token", return_value=mock_claims):
        response = client.get(
            "/api/v1/auth/me",
            headers={"Authorization": "Bearer mock_valid_jwt"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["uid"] == "secure_uid_100"
        assert data["email"] == "verified.member@echoshield.family"
        assert data["email_verified"] is True
        assert data["name"] == "David Henderson"


def test_verify_token_endpoint():
    """Verify POST /api/v1/auth/verify-token validates tokens explicitly."""
    mock_claims = {
        "uid": "token_check_user",
        "email": "check@echoshield.family",
        "email_verified": False
    }
    with patch("app.auth.security.verify_firebase_token", return_value=mock_claims):
        response = client.post(
            "/api/v1/auth/verify-token",
            json={"token": "some_raw_jwt"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "valid"
        assert data["uid"] == "token_check_user"
        assert data["email_verified"] is False


def test_gated_endpoint_rejects_unverified_email():
    """Verify unverified users are blocked from accessing protected resources (403 Forbidden)."""
    mock_claims = {
        "uid": "unverified_uid",
        "email": "pending@echoshield.family",
        "email_verified": False,
        "name": "Pending User"
    }
    with patch("app.auth.security.verify_firebase_token", return_value=mock_claims):
        response = client.get(
            "/api/v1/auth/protected-check",
            headers={"Authorization": "Bearer token_with_unverified_email"}
        )
        assert response.status_code == status.HTTP_403_FORBIDDEN
        assert "not yet confirmed" in response.json()["detail"].lower()


def test_gated_endpoint_permits_verified_email():
    """Verify verified users pass email gating check."""
    mock_claims = {
        "uid": "verified_uid",
        "email": "confirmed@echoshield.family",
        "email_verified": True,
        "name": "Confirmed User"
    }
    with patch("app.auth.security.verify_firebase_token", return_value=mock_claims):
        response = client.get(
            "/api/v1/auth/protected-check",
            headers={"Authorization": "Bearer token_with_verified_email"}
        )
        assert response.status_code == 200
        assert response.json()["role"] == "Verified Family Member"

