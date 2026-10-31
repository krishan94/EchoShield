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
from pipelines.speaker_embedding import speaker_engine  # noqa: E402

client = TestClient(app)


def generate_test_wav(duration: float = 3.5, sample_rate: int = 16000, silent: bool = False) -> bytes:
    """Generates synthetic 16kHz WAV audio bytes for testing."""
    t = np.linspace(0, duration, int(sample_rate * duration))
    if silent:
        audio = np.zeros_like(t, dtype=np.float32)
    else:
        # Multi-tone acoustic signal simulating speech frequencies
        audio = (
            0.25 * np.sin(2 * np.pi * 220 * t) +
            0.20 * np.sin(2 * np.pi * 440 * t) +
            0.15 * np.sin(2 * np.pi * 880 * t)
        ).astype(np.float32)

    bio = io.BytesIO()
    sf.write(bio, audio, sample_rate, format="WAV")
    return bio.getvalue()


@pytest.fixture(autouse=True)
def reset_voice_service():
    """Resets in-memory voice service state before each test."""
    voice_service.use_in_memory = True
    voice_service._mem_consents.clear()
    voice_service._mem_profiles.clear()
    voice_service._mem_users.clear()


@pytest.fixture
def mock_auth_user():
    user = AuthenticatedUser(
        uid="uid_voice_tester",
        email="voice.tester@echoshield.family",
        name="Elena Rostova",
        email_verified=True
    )
    app.dependency_overrides[get_current_user] = lambda: user
    yield user
    app.dependency_overrides.pop(get_current_user, None)


def test_consent_recording_granted(mock_auth_user):
    response = client.post(
        "/api/v1/voice/consent",
        json={"consent_status": "granted", "consent_version": "1.0"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["user_id"] == mock_auth_user.uid
    assert data["consent_status"] == "granted"
    assert data["consent_version"] == "1.0"
    assert voice_service.check_consent(mock_auth_user.uid) is True


def test_consent_recording_invalid_status(mock_auth_user):
    response = client.post(
        "/api/v1/voice/consent",
        json={"consent_status": "invalid_status", "consent_version": "1.0"}
    )
    assert response.status_code == 400
    assert "Invalid consent status" in response.json()["detail"]


def test_enrollment_rejected_when_consent_missing(mock_auth_user):
    wav = generate_test_wav(3.5)
    files = {
        "sample_1": ("sample1.wav", wav, "audio/wav"),
        "sample_2": ("sample2.wav", wav, "audio/wav"),
        "sample_3": ("sample3.wav", wav, "audio/wav"),
    }
    response = client.post("/api/v1/voice/enroll", files=files)
    assert response.status_code == 403
    assert "Informed biometric consent must be granted" in response.json()["detail"]


def test_enrollment_rejected_unsupported_format(mock_auth_user):
    # Grant consent first
    voice_service.record_consent(
        mock_auth_user,
        VoiceConsentRequest(consent_status="granted")
    )
    files = {
        "sample_1": ("sample1.mp3", b"fake mp3 bytes", "audio/mp3"),
        "sample_2": ("sample2.wav", generate_test_wav(3.0), "audio/wav"),
        "sample_3": ("sample3.wav", generate_test_wav(3.0), "audio/wav"),
    }
    response = client.post("/api/v1/voice/enroll", files=files)
    assert response.status_code == 415
    assert "not a valid WAV audio file" in response.json()["detail"]


def test_enrollment_rejected_empty_audio_file(mock_auth_user):
    voice_service.record_consent(mock_auth_user, VoiceConsentRequest(consent_status="granted"))
    files = {
        "sample_1": ("sample1.wav", b"", "audio/wav"),
        "sample_2": ("sample2.wav", generate_test_wav(3.0), "audio/wav"),
        "sample_3": ("sample3.wav", generate_test_wav(3.0), "audio/wav"),
    }
    response = client.post("/api/v1/voice/enroll", files=files)
    assert response.status_code == 400
    assert "empty" in response.json()["detail"].lower()


def test_enrollment_rejected_corrupted_wav(mock_auth_user):
    voice_service.record_consent(mock_auth_user, VoiceConsentRequest(consent_status="granted"))
    files = {
        "sample_1": ("sample1.wav", b"RIFF1234WAVEfmt 1234567890CORRUPTEDDATA", "audio/wav"),
        "sample_2": ("sample2.wav", generate_test_wav(3.0), "audio/wav"),
        "sample_3": ("sample3.wav", generate_test_wav(3.0), "audio/wav"),
    }
    response = client.post("/api/v1/voice/enroll", files=files)
    assert response.status_code == 422
    assert "quality validation failed" in response.json()["detail"].lower()


def test_enrollment_rejected_audio_too_short(mock_auth_user):
    voice_service.record_consent(mock_auth_user, VoiceConsentRequest(consent_status="granted"))
    short_wav = generate_test_wav(duration=1.0)  # Min required is 2.5s
    files = {
        "sample_1": ("sample1.wav", short_wav, "audio/wav"),
        "sample_2": ("sample2.wav", generate_test_wav(3.0), "audio/wav"),
        "sample_3": ("sample3.wav", generate_test_wav(3.0), "audio/wav"),
    }
    response = client.post("/api/v1/voice/enroll", files=files)
    assert response.status_code == 422
    assert "too short" in response.json()["detail"].lower()


def test_enrollment_rejected_audio_near_silent(mock_auth_user):
    voice_service.record_consent(mock_auth_user, VoiceConsentRequest(consent_status="granted"))
    silent_wav = generate_test_wav(duration=3.5, silent=True)
    files = {
        "sample_1": ("sample1.wav", silent_wav, "audio/wav"),
        "sample_2": ("sample2.wav", generate_test_wav(3.0), "audio/wav"),
        "sample_3": ("sample3.wav", generate_test_wav(3.0), "audio/wav"),
    }
    response = client.post("/api/v1/voice/enroll", files=files)
    assert response.status_code == 422
    assert "too low" in response.json()["detail"].lower() or "silent" in response.json()["detail"].lower()


def test_valid_voice_enrollment_and_profile_lifecycle(mock_auth_user):
    # 1. Initially profile is not enrolled
    res_initial = client.get("/api/v1/voice/profile")
    assert res_initial.status_code == 200
    assert res_initial.json()["is_voice_enrolled"] is False

    # 2. Grant informed consent
    res_consent = client.post(
        "/api/v1/voice/consent",
        json={"consent_status": "granted", "consent_version": "1.0"}
    )
    assert res_consent.status_code == 200

    # 3. Enroll 3 valid samples
    wav1 = generate_test_wav(duration=3.2)
    wav2 = generate_test_wav(duration=3.5)
    wav3 = generate_test_wav(duration=4.0)
    files = {
        "sample_1": ("sample1.wav", wav1, "audio/wav"),
        "sample_2": ("sample2.wav", wav2, "audio/wav"),
        "sample_3": ("sample3.wav", wav3, "audio/wav"),
    }
    res_enroll = client.post("/api/v1/voice/enroll", files=files)
    assert res_enroll.status_code == 200
    enroll_data = res_enroll.json()
    assert enroll_data["enrollment_status"] == "enrolled"
    assert enroll_data["sample_count"] == 3
    assert enroll_data["voice_quality_score"] > 0.6
    assert len(enroll_data["samples_summary"]) == 3

    # 4. Check profile status reflects enrollment without exposing raw vectors
    res_prof = client.get("/api/v1/voice/profile")
    assert res_prof.status_code == 200
    prof_data = res_prof.json()
    assert prof_data["is_voice_enrolled"] is True
    assert prof_data["voice_quality_score"] == enroll_data["voice_quality_score"]
    assert "encrypted_embedding" not in prof_data
    assert "ciphertext" not in prof_data

    # 5. Delete profile and verify biometric erasure
    res_delete = client.delete("/api/v1/voice/profile")
    assert res_delete.status_code == 200
    assert res_delete.json()["status"] == "deleted"

    # 6. Verify profile is reset
    res_prof_after = client.get("/api/v1/voice/profile")
    assert res_prof_after.json()["is_voice_enrolled"] is False


def test_aes_256_gcm_biometric_encryption_roundtrip():
    """Verifies that 192-dim biometric embeddings roundtrip through AES-256-GCM without precision loss."""
    raw_vector = np.random.randn(192).astype(np.float32)
    raw_vector = raw_vector / np.linalg.norm(raw_vector)

    encrypted_record = speaker_engine.encrypt_embedding(raw_vector)
    assert "ciphertext" in encrypted_record
    assert "nonce" in encrypted_record
    assert encrypted_record["algorithm"] == "AES-256-GCM"

    decrypted_vector = speaker_engine.decrypt_embedding(encrypted_record)
    assert decrypted_vector.shape == (192,)
    assert np.allclose(raw_vector, decrypted_vector, atol=1e-6)
