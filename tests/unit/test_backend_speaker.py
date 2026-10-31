"""
EchoShield — Backend Speaker Recognition & Verification Unit Tests
===================================================================
Hermetic unit and integration tests for Phase 5:
- SpeechBrain ECAPA-TDNN embedding extraction and cosine similarity
- Calibrated decision engine (VOICE_MATCHED, NO_MATCH, INCONCLUSIVE, AUDIO_QUALITY_FAILED, PROFILE_NOT_ENROLLED)
- Authorization and consent verification
- Error handling (empty, corrupt, short, silent, unsupported audio)
- Verification history logging, retrieval, and deletion
"""

import io
import sys
from pathlib import Path

import numpy as np
import pytest
import soundfile as sf
from fastapi.testclient import TestClient

# Ensure backend and ai-engine in path
backend_path = Path(__file__).resolve().parents[2] / "backend"
ai_engine_path = Path(__file__).resolve().parents[2] / "ai-engine"
if str(backend_path) not in sys.path:
    sys.path.insert(0, str(backend_path))
if str(ai_engine_path) not in sys.path:
    sys.path.insert(0, str(ai_engine_path))

from app.auth.models import AuthenticatedUser  # noqa: E402
from app.auth.security import get_current_user  # noqa: E402
from app.enrollment.models import VoiceConsentRequest  # noqa: E402
from app.enrollment.service import voice_service  # noqa: E402
from app.main import app  # noqa: E402
from app.services.speaker_recognition import (  # noqa: E402
    DECISION_AUDIO_QUALITY_FAILED,
    DECISION_INCONCLUSIVE,
    DECISION_NO_MATCH,
    DECISION_PROFILE_NOT_ENROLLED,
    DECISION_VOICE_MATCHED,
    IncompatibleEmbeddingError,
    decision_engine,
    similarity_service,
    verification_service,
)

client = TestClient(app)


def generate_test_wav(
    duration: float = 3.5,
    sample_rate: int = 16000,
    base_freq: float = 220.0,
    silent: bool = False,
    clipped: bool = False
) -> bytes:
    """Generates synthetic 16kHz WAV audio bytes for testing."""
    t = np.linspace(0, duration, int(sample_rate * duration))
    if silent:
        audio = np.zeros_like(t, dtype=np.float32)
    elif clipped:
        audio = (1.5 * np.sin(2 * np.pi * base_freq * t)).astype(np.float32)
        audio = np.clip(audio, -1.0, 1.0)
    else:
        audio = (
            0.28 * np.sin(2 * np.pi * base_freq * t) +
            0.22 * np.sin(2 * np.pi * (base_freq * 2) * t) +
            0.15 * np.sin(2 * np.pi * (base_freq * 3) * t)
        ).astype(np.float32)

    bio = io.BytesIO()
    sf.write(bio, audio, sample_rate, format="WAV")
    return bio.getvalue()


@pytest.fixture(autouse=True)
def reset_services():
    """Resets in-memory state before each test."""
    voice_service.use_in_memory = True
    voice_service._mem_consents.clear()
    voice_service._mem_profiles.clear()
    voice_service._mem_users.clear()

    verification_service.use_in_memory = True
    verification_service._mem_history.clear()
    verification_service._mem_relationships.clear()
    verification_service._mem_profiles.clear()
    verification_service._mem_consents.clear()
    verification_service._mem_users.clear()


def enroll_test_user(user_id: str, base_freq: float = 220.0):
    """Helper to enroll a user with 3 synthetic samples."""
    user = AuthenticatedUser(uid=user_id, email=f"{user_id}@test.com", email_verified=True, name=f"User {user_id}")
    voice_service.record_consent(user, VoiceConsentRequest(consent_status="granted", consent_version="1.0"))

    samples = [
        generate_test_wav(duration=3.0, base_freq=base_freq),
        generate_test_wav(duration=3.2, base_freq=base_freq * 1.05),
        generate_test_wav(duration=3.5, base_freq=base_freq * 0.98),
    ]
    resp = voice_service.enroll_voice(user, samples)
    # Sync profile to verification_service
    profile = voice_service._mem_profiles.get(user_id)
    if profile:
        verification_service._mem_profiles[user_id] = profile
    verification_service._mem_consents[user_id] = {"consent_status": "granted"}
    return resp


# -----------------------------------------------------------------------------
# Unit Tests for Speaker Recognition Modular Services
# -----------------------------------------------------------------------------

def test_cosine_similarity_identical_vectors():
    """Identical unit vectors must yield a cosine similarity of exactly 1.0."""
    v = np.random.randn(192).astype(np.float32)
    v /= np.linalg.norm(v)
    score = similarity_service.compute_cosine_similarity(v, v)
    assert score == 1.0


def test_cosine_similarity_orthogonal_vectors():
    """Orthogonal vectors must yield a cosine similarity of ~0.0."""
    v1 = np.zeros(192, dtype=np.float32)
    v1[0] = 1.0
    v2 = np.zeros(192, dtype=np.float32)
    v2[1] = 1.0
    score = similarity_service.compute_cosine_similarity(v1, v2)
    assert abs(score) < 1e-4


def test_cosine_similarity_dimension_mismatch():
    """Vector dimension mismatch must raise IncompatibleEmbeddingError."""
    v1 = np.zeros(192, dtype=np.float32)
    v2 = np.zeros(128, dtype=np.float32)
    with pytest.raises(IncompatibleEmbeddingError):
        similarity_service.compute_cosine_similarity(v1, v2)


def test_calibrated_decision_bands():
    """Verifies calibrated operating bands: MATCH, NON_MATCH, INCONCLUSIVE."""
    # Strong match
    d1 = decision_engine.evaluate(similarity_score=0.85, snr_db=22.0)
    assert d1.category == DECISION_VOICE_MATCHED
    assert d1.confidence_band == "high_match"

    # Distinct speaker non-match
    d2 = decision_engine.evaluate(similarity_score=0.45, snr_db=22.0)
    assert d2.category == DECISION_NO_MATCH
    assert d2.confidence_band == "non_match"

    # Uncertain / Inconclusive region [0.58, 0.72)
    d3 = decision_engine.evaluate(similarity_score=0.65, snr_db=20.0)
    assert d3.category == DECISION_INCONCLUSIVE
    assert d3.confidence_band == "uncertain_band"

    # Degraded noise downgrades borderline match to inconclusive
    d4 = decision_engine.evaluate(similarity_score=0.73, snr_db=7.0)
    assert d4.category == DECISION_INCONCLUSIVE
    assert d4.confidence_band == "noise_degraded"


# -----------------------------------------------------------------------------
# End-to-End API Integration Tests (POST /api/v1/voice/verify)
# -----------------------------------------------------------------------------

def test_verify_enrolled_voice_match_self():
    """Self-verification of enrolled voice with matching audio returns VOICE_MATCHED."""
    user = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user
    enroll_test_user("user_alice", base_freq=220.0)

    # Audio with same acoustic frequency profile
    test_audio = generate_test_wav(duration=3.2, base_freq=220.0)

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("test.wav", test_audio, "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 200
    data = response.json()
    assert data["result"] == DECISION_VOICE_MATCHED
    assert data["similarity_score"] is not None
    assert data["similarity_score"] >= 0.72
    assert data["audio_quality_status"] == "acceptable"
    assert "SpeechBrain ECAPA-TDNN" in data["model_name"]


def test_verify_different_speaker_no_match():
    """Audio from a distinctly different voice profile returns NO_MATCH or non-match."""
    user = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user
    enroll_test_user("user_alice", base_freq=180.0)

    # Audio from dramatically different frequency characteristics (different vocal tract)
    diff_audio = generate_test_wav(duration=3.5, base_freq=950.0)

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("test.wav", diff_audio, "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 200
    data = response.json()
    # Different acoustic signals should score below match threshold
    assert data["result"] in (DECISION_NO_MATCH, DECISION_INCONCLUSIVE)
    assert data["similarity_score"] < 0.72


def test_verify_family_member_authorized():
    """User can verify a linked family member who is enrolled."""
    user_alice = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user_alice

    # Enroll Bob
    enroll_test_user("user_bob", base_freq=330.0)

    # Establish family relationship between Alice and Bob
    rel_id = "rel_alice_bob"
    verification_service._mem_relationships[rel_id] = {
        "id": rel_id,
        "members": ["user_alice", "user_bob"]
    }

    test_audio = generate_test_wav(duration=3.5, base_freq=330.0)

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_bob"},
        files={"audio_file": ("bob_sample.wav", test_audio, "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 200
    data = response.json()
    assert data["member_id"] == "user_bob"
    assert data["result"] == DECISION_VOICE_MATCHED


def test_verify_unauthorized_non_family_member():
    """Attempting to verify an unrelated user returns HTTP 403 Forbidden."""
    user_alice = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user_alice

    # Charlie is enrolled, but NOT in Alice's family circle
    enroll_test_user("user_charlie", base_freq=400.0)

    test_audio = generate_test_wav(duration=3.0, base_freq=400.0)

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_charlie"},
        files={"audio_file": ("charlie.wav", test_audio, "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 403
    assert "Unauthorized" in response.json()["detail"]


def test_verify_member_missing_consent():
    """Attempting to verify a member without active consent returns HTTP 403 Forbidden."""
    user_alice = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user_alice

    enroll_test_user("user_bob", base_freq=300.0)
    # Revoke Bob's consent
    verification_service._mem_consents["user_bob"]["consent_status"] = "revoked"
    verification_service._mem_relationships["rel_alice_bob"] = {
        "members": ["user_alice", "user_bob"]
    }

    test_audio = generate_test_wav(duration=3.0, base_freq=300.0)

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_bob"},
        files={"audio_file": ("sample.wav", test_audio, "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 403
    assert "consent" in response.json()["detail"].lower()


def test_verify_member_not_enrolled():
    """Attempting to verify a family member who has not enrolled returns PROFILE_NOT_ENROLLED."""
    user_alice = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user_alice

    # Establish relationship with unenrolled Dave
    verification_service._mem_relationships["rel_alice_dave"] = {
        "members": ["user_alice", "user_dave"]
    }
    verification_service._mem_consents["user_dave"] = {"consent_status": "granted"}

    test_audio = generate_test_wav(duration=3.0, base_freq=250.0)

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_dave"},
        files={"audio_file": ("sample.wav", test_audio, "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 200
    data = response.json()
    assert data["result"] == DECISION_PROFILE_NOT_ENROLLED


def test_verify_empty_audio_file():
    """Uploading empty audio returns HTTP 400."""
    user = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user
    enroll_test_user("user_alice")

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("sample.wav", b"", "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 400


def test_verify_unsupported_audio_format():
    """Uploading MP3 format returns HTTP 415."""
    user = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("sample.mp3", b"FAKE_MP3_CONTENT", "audio/mpeg")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 415


def test_verify_corrupted_wav_file():
    """Corrupted WAV headers return HTTP 422."""
    user = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user
    enroll_test_user("user_alice")

    corrupted = b"RIFF" + b"\x00" * 40 + b"RANDOM_CORRUPTED_BYTES"

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("corrupt.wav", corrupted, "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 422


def test_verify_audio_too_short():
    """Audio shorter than 2.5s returns AUDIO_QUALITY_FAILED."""
    user = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user
    enroll_test_user("user_alice")

    short_audio = generate_test_wav(duration=1.2)

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("short.wav", short_audio, "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 200
    data = response.json()
    assert data["result"] == DECISION_AUDIO_QUALITY_FAILED
    assert data["audio_quality_status"] == "rejected"


def test_verify_audio_near_silent():
    """Near-silent audio returns AUDIO_QUALITY_FAILED."""
    user = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user
    enroll_test_user("user_alice")

    silent_audio = generate_test_wav(duration=3.5, silent=True)

    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("silent.wav", silent_audio, "audio/wav")}
    )
    app.dependency_overrides.clear()

    assert response.status_code == 200
    data = response.json()
    assert data["result"] == DECISION_AUDIO_QUALITY_FAILED


def test_verify_requires_authentication():
    """Endpoint without auth token returns HTTP 401."""
    response = client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("sample.wav", b"data", "audio/wav")}
    )
    assert response.status_code == 401


# -----------------------------------------------------------------------------
# Verification History Tests (GET & DELETE /api/v1/voice/verification-history)
# -----------------------------------------------------------------------------

def test_verification_history_lifecycle():
    """Audit records are logged, retrievable, and purgeable."""
    user = AuthenticatedUser(uid="user_alice", email="alice@test.com", email_verified=True, name="Alice")
    app.dependency_overrides[get_current_user] = lambda: user
    enroll_test_user("user_alice", base_freq=220.0)

    # 1. Perform 2 verifications
    audio1 = generate_test_wav(duration=3.0, base_freq=220.0)
    audio2 = generate_test_wav(duration=3.2, base_freq=220.0)

    client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("s1.wav", audio1, "audio/wav")}
    )
    client.post(
        "/api/v1/voice/verify",
        data={"member_id": "user_alice"},
        files={"audio_file": ("s2.wav", audio2, "audio/wav")}
    )

    # 2. Retrieve history
    history_resp = client.get("/api/v1/voice/verification-history")
    assert history_resp.status_code == 200
    history_data = history_resp.json()
    assert history_data["total_count"] >= 2
    assert len(history_data["records"]) >= 2
    # Ensure no raw embeddings or audio leaked
    first_record = history_data["records"][0]
    assert "ciphertext" not in first_record
    assert "vector" not in first_record
    assert "audio_bytes" not in first_record

    # 3. Clear history
    del_resp = client.delete("/api/v1/voice/verification-history")
    assert del_resp.status_code == 200
    assert del_resp.json()["deleted_count"] >= 2

    # 4. Verify history is empty now
    empty_history = client.get("/api/v1/voice/verification-history")
    assert empty_history.json()["total_count"] == 0
    app.dependency_overrides.clear()
