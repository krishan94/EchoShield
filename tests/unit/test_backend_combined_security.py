"""
EchoShield — Backend Combined Security Decision Engine Unit Tests
==================================================================
Comprehensive test suite for Phase 7:
- All 7 decision states and strict conflict precedence
- Critical conflict: Spoof Suspected overrides Speaker Matched
- Speaker No-Match, Inconclusive, Quality Failed, Model Unavailable, Profile Not Enrolled
- API endpoint authentication, authorization, format checking, and payload validation
"""

import io
import sys
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

import numpy as np
import pytest
import soundfile as sf
from fastapi.testclient import TestClient

backend_path = Path(__file__).resolve().parents[2] / "backend"
if str(backend_path) not in sys.path:
    sys.path.insert(0, str(backend_path))

from app.auth.models import AuthenticatedUser
from app.auth.security import get_current_user
from app.combined_security.models import (
    AcousticScreeningSummary,
    CombinedSecurityOutcome,
    CombinedSecurityResponse,
)
from app.main import app
from app.services.combined_security.decision_engine import (
    CombinedSecurityDecisionEngine,
    combined_decision_engine,
)
from app.services.combined_security.service import CombinedSecurityService
from app.services.speaker_recognition.exceptions import (
    BiometricConsentMissingError,
    UnauthorizedAccessError,
)

client = TestClient(app)


def generate_test_wav(duration: float = 3.5, sample_rate: int = 16000) -> bytes:
    """Generates standard 16kHz mono PCM WAV bytes for testing."""
    t = np.linspace(0, duration, int(sample_rate * duration))
    # 440 Hz tone + harmonics
    audio = 0.3 * np.sin(2 * np.pi * 440 * t) + 0.1 * np.sin(2 * np.pi * 880 * t)
    bio = io.BytesIO()
    sf.write(bio, audio.astype(np.float32), sample_rate, format="WAV", subtype="PCM_16")
    return bio.getvalue()


# ============================================================================
# 1. DECISION ENGINE UNIT TESTS (PRECEDENCE & CONFLICT RESOLUTION)
# ============================================================================

def test_engine_profile_not_enrolled():
    """Unenrolled voice profile yields PROFILE_NOT_ENROLLED."""
    engine = CombinedSecurityDecisionEngine()
    out = engine.evaluate(
        target_member_name="Alice",
        profile_enrolled=False,
        speaker_data=None,
        spoof_data=None
    )
    assert out.outcome == CombinedSecurityOutcome.PROFILE_NOT_ENROLLED
    assert "Not Enrolled" in out.headline
    assert "Alice" in out.explanation


def test_engine_audio_quality_failed():
    """Audio quality screening failure yields AUDIO_QUALITY_FAILED."""
    engine = CombinedSecurityDecisionEngine()
    bad_quality = AcousticScreeningSummary(
        duration_seconds=1.0,
        sample_rate=16000,
        snr_db=5.0,
        rms_energy=0.001,
        status_label="TOO_SHORT",
        is_acceptable=False,
        rejection_reason="Audio duration 1.0s is under 2.0s minimum."
    )
    out = engine.evaluate(
        target_member_name="Alice",
        profile_enrolled=True,
        speaker_data={"result": "AUDIO_QUALITY_FAILED"},
        spoof_data={"result_category": "AUDIO_QUALITY_FAILED"},
        quality_data=bad_quality
    )
    assert out.outcome == CombinedSecurityOutcome.AUDIO_QUALITY_FAILED
    assert "Quality" in out.headline
    assert "Audio duration 1.0s" in out.explanation


def test_engine_critical_conflict_speaker_match_and_spoof_suspected():
    """CRITICAL: Anti-spoofing SPOOF_SUSPECTED must override speaker match -> SUSPICIOUS_AUDIO."""
    engine = CombinedSecurityDecisionEngine()
    speaker_data = {
        "result": "VOICE_MATCHED",
        "similarity_score": 0.89,
        "match_threshold": 0.72,
        "non_match_threshold": 0.58,
        "decision_status": "high_match"
    }
    spoof_data = {
        "result_category": "SPOOF_SUSPECTED",
        "spoof_score": 0.94,
        "confidence_score": 0.94
    }
    out = engine.evaluate(
        target_member_name="Bob",
        profile_enrolled=True,
        speaker_data=speaker_data,
        spoof_data=spoof_data
    )
    assert out.outcome == CombinedSecurityOutcome.SUSPICIOUS_AUDIO
    assert "Suspicious Audio" in out.headline
    assert "POTENTIAL AI VOICE CLONE DETECTED" in out.explanation
    assert any("CRITICAL CONFLICT" in f for f in out.decision_factors)


def test_engine_spoof_suspected_with_speaker_no_match():
    """Spoof suspected with speaker no match yields SUSPICIOUS_AUDIO."""
    engine = CombinedSecurityDecisionEngine()
    speaker_data = {
        "result": "NO_MATCH",
        "similarity_score": 0.42,
        "match_threshold": 0.72,
        "non_match_threshold": 0.58,
        "decision_status": "non_match"
    }
    spoof_data = {
        "result_category": "SPOOF_SUSPECTED",
        "spoof_score": 0.88,
        "confidence_score": 0.88
    }
    out = engine.evaluate(
        target_member_name="Charlie",
        profile_enrolled=True,
        speaker_data=speaker_data,
        spoof_data=spoof_data
    )
    assert out.outcome == CombinedSecurityOutcome.SUSPICIOUS_AUDIO


def test_engine_dual_affirmation_identity_confirmed():
    """Speaker match + No spoof signal yields IDENTITY_CONFIRMED."""
    engine = CombinedSecurityDecisionEngine()
    speaker_data = {
        "result": "VOICE_MATCHED",
        "similarity_score": 0.85,
        "match_threshold": 0.72,
        "non_match_threshold": 0.58,
        "decision_status": "high_match"
    }
    spoof_data = {
        "result_category": "NO_SPOOF_SIGNAL_DETECTED",
        "spoof_score": 0.04,
        "confidence_score": 0.96
    }
    out = engine.evaluate(
        target_member_name="Mom",
        profile_enrolled=True,
        speaker_data=speaker_data,
        spoof_data=spoof_data
    )
    assert out.outcome == CombinedSecurityOutcome.IDENTITY_CONFIRMED
    assert "Voice Identity Confirmed" in out.headline
    assert "Dual security confirmation" in out.explanation
    assert len(out.limitations) >= 3


def test_engine_identity_not_confirmed():
    """Speaker NO_MATCH + No spoof signal yields IDENTITY_NOT_CONFIRMED."""
    engine = CombinedSecurityDecisionEngine()
    speaker_data = {
        "result": "NO_MATCH",
        "similarity_score": 0.44,
        "match_threshold": 0.72,
        "non_match_threshold": 0.58,
        "decision_status": "non_match"
    }
    spoof_data = {
        "result_category": "NO_SPOOF_SIGNAL_DETECTED",
        "spoof_score": 0.03,
        "confidence_score": 0.97
    }
    out = engine.evaluate(
        target_member_name="Dad",
        profile_enrolled=True,
        speaker_data=speaker_data,
        spoof_data=spoof_data
    )
    assert out.outcome == CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED
    assert "Identity Not Confirmed" in out.headline
    assert "distinctly differs" in out.explanation


def test_engine_speaker_inconclusive_yields_inconclusive():
    """Speaker INCONCLUSIVE + No spoof signal yields INCONCLUSIVE."""
    engine = CombinedSecurityDecisionEngine()
    speaker_data = {
        "result": "INCONCLUSIVE",
        "similarity_score": 0.65,
        "match_threshold": 0.72,
        "non_match_threshold": 0.58,
        "decision_status": "uncertain_band"
    }
    spoof_data = {
        "result_category": "NO_SPOOF_SIGNAL_DETECTED",
        "spoof_score": 0.05,
        "confidence_score": 0.95
    }
    out = engine.evaluate(
        target_member_name="Sister",
        profile_enrolled=True,
        speaker_data=speaker_data,
        spoof_data=spoof_data
    )
    assert out.outcome == CombinedSecurityOutcome.INCONCLUSIVE
    assert "Inconclusive" in out.headline


def test_engine_anti_spoof_inconclusive_yields_inconclusive():
    """Speaker match + Anti-spoof INCONCLUSIVE yields INCONCLUSIVE."""
    engine = CombinedSecurityDecisionEngine()
    speaker_data = {
        "result": "VOICE_MATCHED",
        "similarity_score": 0.88,
        "match_threshold": 0.72,
        "non_match_threshold": 0.58,
        "decision_status": "high_match"
    }
    spoof_data = {
        "result_category": "INCONCLUSIVE",
        "spoof_score": 0.50,
        "confidence_score": 0.50
    }
    out = engine.evaluate(
        target_member_name="Brother",
        profile_enrolled=True,
        speaker_data=speaker_data,
        spoof_data=spoof_data
    )
    assert out.outcome == CombinedSecurityOutcome.INCONCLUSIVE


def test_engine_model_unavailable_yields_analysis_unavailable():
    """Model unavailable error yields ANALYSIS_UNAVAILABLE."""
    engine = CombinedSecurityDecisionEngine()
    speaker_data = {
        "result": "VOICE_MATCHED",
        "similarity_score": 0.85
    }
    spoof_data = {
        "result_category": "MODEL_UNAVAILABLE"
    }
    out = engine.evaluate(
        target_member_name="Grandpa",
        profile_enrolled=True,
        speaker_data=speaker_data,
        spoof_data=spoof_data,
        spoof_error="Wav2Vec2 model offline"
    )
    assert out.outcome == CombinedSecurityOutcome.ANALYSIS_UNAVAILABLE
    assert "Unavailable" in out.headline


# ============================================================================
# 2. API ENDPOINT INTEGRATION TESTS (POST /api/v1/voice/analyze-security)
# ============================================================================

mock_user = AuthenticatedUser(
    uid="test_user_caller_123",
    email="caller@example.com",
    name="Caller User",
    email_verified=True
)


def mock_get_current_user_override():
    return mock_user


@pytest.fixture(autouse=True)
def apply_auth_override():
    app.dependency_overrides[get_current_user] = mock_get_current_user_override
    yield
    app.dependency_overrides.clear()


def test_api_security_analysis_success_identity_confirmed():
    """End-to-end API test yielding IDENTITY_CONFIRMED."""
    audio_wav = generate_test_wav(duration=3.5)

    with patch("app.services.combined_security.service.verification_service._check_family_authorization"), \
         patch("app.services.combined_security.service.verification_service._check_member_consent"), \
         patch("app.services.combined_security.service.verification_service._get_enrolled_embedding"), \
         patch("app.services.combined_security.service.verification_service.verify_voice_sample") as mock_speaker, \
         patch("app.services.combined_security.service.spoof_detection_service.predict") as mock_spoof:

        mock_speaker.return_value = {
            "verification_id": "verif_123",
            "member_id": "member_mom_456",
            "member_name": "Mom",
            "result": "VOICE_MATCHED",
            "similarity_score": 0.86,
            "match_threshold": 0.72,
            "non_match_threshold": 0.58,
            "decision_status": "high_match",
            "audio_quality_status": "acceptable",
            "explanation": "Matched Mom baseline."
        }

        mock_inference = MagicMock()
        mock_inference.fake_probability = 0.05
        mock_inference.real_probability = 0.95
        mock_inference.fake_logit = -2.5
        mock_inference.real_logit = 2.5
        mock_inference.model_name = "Wav2Vec2"
        mock_inference.model_version = "v1.0"
        mock_spoof.return_value = mock_inference

        response = client.post(
            "/api/v1/voice/analyze-security",
            data={"member_id": "member_mom_456", "request_id": "req_test_001"},
            files={"audio_file": ("test.wav", audio_wav, "audio/wav")}
        )

        assert response.status_code == 200
        data = response.json()
        assert data["combined_decision"] == "IDENTITY_CONFIRMED"
        assert data["target_member_id"] == "member_mom_456"
        assert data["speaker_verification"]["result"] == "VOICE_MATCHED"
        assert data["anti_spoofing"]["result_category"] == "NO_SPOOF_SIGNAL_DETECTED"
        assert data["audio_quality"]["is_acceptable"] is True
        assert len(data["decision_factors"]) >= 2
        assert len(data["limitations"]) >= 3


def test_api_security_analysis_suspicious_audio_override():
    """End-to-end API test: Spoof suspected overrides speaker match to SUSPICIOUS_AUDIO."""
    audio_wav = generate_test_wav(duration=3.5)

    with patch("app.services.combined_security.service.verification_service._check_family_authorization"), \
         patch("app.services.combined_security.service.verification_service._check_member_consent"), \
         patch("app.services.combined_security.service.verification_service._get_enrolled_embedding"), \
         patch("app.services.combined_security.service.verification_service.verify_voice_sample") as mock_speaker, \
         patch("app.services.combined_security.service.spoof_detection_service.predict") as mock_spoof:

        mock_speaker.return_value = {
            "verification_id": "verif_124",
            "member_id": "member_mom_456",
            "member_name": "Mom",
            "result": "VOICE_MATCHED",
            "similarity_score": 0.91,
            "match_threshold": 0.72,
            "non_match_threshold": 0.58,
            "decision_status": "high_match",
            "audio_quality_status": "acceptable",
            "explanation": "High similarity."
        }

        mock_inference = MagicMock()
        mock_inference.fake_probability = 0.92
        mock_inference.real_probability = 0.08
        mock_inference.fake_logit = 3.0
        mock_inference.real_logit = -3.0
        mock_inference.model_name = "Wav2Vec2"
        mock_inference.model_version = "v1.0"
        mock_spoof.return_value = mock_inference

        response = client.post(
            "/api/v1/voice/analyze-security",
            data={"member_id": "member_mom_456"},
            files={"audio_file": ("test.wav", audio_wav, "audio/wav")}
        )

        assert response.status_code == 200
        data = response.json()
        assert data["combined_decision"] == "SUSPICIOUS_AUDIO"
        assert "POTENTIAL AI VOICE CLONE DETECTED" in data["explanation"]
        assert data["anti_spoofing"]["result_category"] == "SPOOF_SUSPECTED"


def test_api_security_analysis_profile_not_enrolled():
    """API returns PROFILE_NOT_ENROLLED when target member has no profile."""
    audio_wav = generate_test_wav(duration=3.5)

    with patch("app.services.combined_security.service.verification_service._check_family_authorization"), \
         patch("app.services.combined_security.service.verification_service._check_member_consent"), \
         patch("app.services.combined_security.service.verification_service._get_enrolled_embedding", side_effect=from_exceptions):

        response = client.post(
            "/api/v1/voice/analyze-security",
            data={"member_id": "member_unenrolled_789"},
            files={"audio_file": ("test.wav", audio_wav, "audio/wav")}
        )

        assert response.status_code == 200
        data = response.json()
        assert data["combined_decision"] == "PROFILE_NOT_ENROLLED"


def from_exceptions(*args, **kwargs):
    from app.services.speaker_recognition.exceptions import ProfileNotEnrolledError
    raise ProfileNotEnrolledError("member_unenrolled_789")


def test_api_security_analysis_unauthorized_relationship_raises_403():
    """Calling with target member outside family circle raises HTTP 403."""
    audio_wav = generate_test_wav(duration=3.5)

    with patch("app.services.combined_security.service.verification_service._check_family_authorization",
               side_effect=UnauthorizedAccessError("No active relationship")):
        response = client.post(
            "/api/v1/voice/analyze-security",
            data={"member_id": "stranger_uid"},
            files={"audio_file": ("test.wav", audio_wav, "audio/wav")}
        )
        assert response.status_code == 403
        assert "No active relationship" in response.json()["detail"]


def test_api_security_analysis_consent_missing_raises_403():
    """Target member without active biometric consent raises HTTP 403."""
    audio_wav = generate_test_wav(duration=3.5)

    with patch("app.services.combined_security.service.verification_service._check_family_authorization"), \
         patch("app.services.combined_security.service.verification_service._check_member_consent",
               side_effect=BiometricConsentMissingError("member_123", "Consent missing")):
        response = client.post(
            "/api/v1/voice/analyze-security",
            data={"member_id": "member_123"},
            files={"audio_file": ("test.wav", audio_wav, "audio/wav")}
        )
        assert response.status_code == 403
        assert "Consent missing" in response.json()["detail"]


def test_api_security_analysis_empty_file_raises_400():
    """Empty audio file returns HTTP 400 Bad Request."""
    response = client.post(
        "/api/v1/voice/analyze-security",
        data={"member_id": "member_123"},
        files={"audio_file": ("test.wav", b"", "audio/wav")}
    )
    assert response.status_code == 400
    assert "empty" in response.json()["detail"].lower()


def test_api_security_analysis_non_wav_file_raises_415():
    """Non-WAV audio returns HTTP 415 Unsupported Media Type."""
    response = client.post(
        "/api/v1/voice/analyze-security",
        data={"member_id": "member_123"},
        files={"audio_file": ("test.mp3", b"ID3some_mp3_data", "audio/mp3")}
    )
    assert response.status_code == 415
    assert "WAV" in response.json()["detail"]

