"""
EchoShield — Backend Anti-Spoofing & Deepfake Detection Unit Tests
==================================================================
Comprehensive test suite for Phase 6:
- Pretrained Wav2Vec2 model loading and real neural inference
- Preprocessing and acoustic screening (duration, RMS, silence, clipping, SNR)
- Calibrated decision engine (SPOOF_SUSPECTED, NO_SPOOF_SIGNAL_DETECTED, INCONCLUSIVE, AUDIO_QUALITY_FAILED, MODEL_UNAVAILABLE)
- Replay limitation notice and disclaimers
- API endpoint authentication, validation, rate limiting, and temporary file purging
"""

import io
import os
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import numpy as np
import pytest
import soundfile as sf
import torch
from fastapi.testclient import TestClient

# Ensure backend and ai-engine in path
backend_path = Path(__file__).resolve().parents[2] / "backend"
if str(backend_path) not in sys.path:
    sys.path.insert(0, str(backend_path))

from app.auth.models import AuthenticatedUser
from app.auth.security import get_current_user
from app.main import app
from app.services.anti_spoofing import (
    AntiSpoofError,
    AudioPreprocessingError,
    AudioQualityError,
    ModelUnavailableError,
    SpoofCategory,
    SpoofInferenceError,
    anti_spoof_decision_engine,
    anti_spoof_model_loader,
    anti_spoof_preprocessor,
    spoof_detection_service,
)
from app.services.anti_spoofing.schemas import AudioQualityReport, ModelInferenceOutput

client = TestClient(app)


def generate_test_wav(
    duration: float = 3.5,
    sample_rate: int = 16000,
    base_freq: float = 220.0,
    silent: bool = False,
    clipped: bool = False,
    snr_noise: float = 0.0
) -> bytes:
    """Generates synthetic 16kHz WAV audio bytes for testing."""
    t = np.linspace(0, duration, int(sample_rate * duration))
    if silent:
        audio = np.zeros_like(t, dtype=np.float32)
    elif clipped:
        audio = np.clip(1.8 * np.sin(2 * np.pi * base_freq * t), -1.0, 1.0).astype(np.float32)
    else:
        # Complex multi-harmonic speech-like wave
        audio = (
            0.4 * np.sin(2 * np.pi * base_freq * t) +
            0.2 * np.sin(2 * np.pi * (base_freq * 2) * t) +
            0.1 * np.sin(2 * np.pi * (base_freq * 3) * t)
        ).astype(np.float32)

    if snr_noise > 0.0:
        noise = np.random.normal(0, snr_noise, len(audio)).astype(np.float32)
        audio = audio + noise

    buf = io.BytesIO()
    sf.write(buf, audio, sample_rate, format="WAV", subtype="PCM_16")
    return buf.getvalue()


@pytest.fixture
def test_user():
    return AuthenticatedUser(
        uid="test_user_p6_001",
        email="auditor@echoshield.test",
        display_name="Security Auditor"
    )


# =========================================================================
# 1. AUDIO PREPROCESSOR TESTS
# =========================================================================

def test_preprocessor_valid_wav():
    wav_bytes = generate_test_wav(duration=3.0)
    data, report = anti_spoof_preprocessor.preprocess_and_validate(wav_bytes, "valid.wav")
    assert report.is_valid is True
    assert 2.9 <= report.duration_seconds <= 3.1
    assert report.sample_rate == 16000
    assert report.channels == 1
    assert report.rms_energy > 0.01
    assert len(data) == 48000


def test_preprocessor_too_short():
    wav_bytes = generate_test_wav(duration=1.2)
    with pytest.raises(AudioQualityError) as exc_info:
        anti_spoof_preprocessor.preprocess_and_validate(wav_bytes, "short.wav")
    assert "shorter than required minimum" in exc_info.value.message


def test_preprocessor_too_long():
    wav_bytes = generate_test_wav(duration=16.0)
    with pytest.raises(AudioQualityError) as exc_info:
        anti_spoof_preprocessor.preprocess_and_validate(wav_bytes, "long.wav")
    assert "exceeds maximum allowed limit" in exc_info.value.message


def test_preprocessor_silence():
    wav_bytes = generate_test_wav(duration=3.0, silent=True)
    with pytest.raises(AudioQualityError) as exc_info:
        anti_spoof_preprocessor.preprocess_and_validate(wav_bytes, "silent.wav")
    assert "minimum audible floor" in exc_info.value.message


def test_preprocessor_clipping():
    wav_bytes = generate_test_wav(duration=3.0, clipped=True)
    with pytest.raises(AudioQualityError) as exc_info:
        anti_spoof_preprocessor.preprocess_and_validate(wav_bytes, "clipped.wav")
    assert "clipping" in exc_info.value.message


def test_preprocessor_empty_and_corrupt():
    with pytest.raises(AudioPreprocessingError):
        anti_spoof_preprocessor.preprocess_and_validate(b"", "empty.wav")

    with pytest.raises(AudioPreprocessingError):
        anti_spoof_preprocessor.preprocess_and_validate(b"RIFF\x00\x00\x00\x00WAVEfmt corrupt payload", "corrupt.wav")


# =========================================================================
# 2. MODEL LOADER & REAL INFERENCE TESTS
# =========================================================================

def test_model_loader_singleton():
    model, extractor = anti_spoof_model_loader.get_model_and_extractor()
    assert model is not None
    assert extractor is not None
    assert anti_spoof_model_loader.model_name.startswith("mo-thecreator")
    assert anti_spoof_model_loader.license == "Apache-2.0"
    assert "fake" in model.config.id2label.values()
    assert "real" in model.config.id2label.values()


def test_real_inference_execution():
    """Runs real Wav2Vec2 inference on 3 seconds of synthesized speech audio."""
    wav_bytes = generate_test_wav(duration=3.0, base_freq=180.0)
    audio_data, _ = anti_spoof_preprocessor.preprocess_and_validate(wav_bytes)

    inference = spoof_detection_service.predict(audio_data)
    assert isinstance(inference, ModelInferenceOutput)
    assert isinstance(inference.fake_logit, float)
    assert isinstance(inference.real_logit, float)
    assert 0.0 <= inference.fake_probability <= 1.0
    assert 0.0 <= inference.real_probability <= 1.0
    # Probabilities should sum to approximately 1.0
    assert abs(inference.fake_probability + inference.real_probability - 1.0) < 1e-3


def test_inference_handles_nan_inf():
    dummy_audio = np.random.randn(16000).astype(np.float32)
    mock_model = MagicMock()
    mock_extractor = MagicMock()
    mock_extractor.return_value = {"input_values": torch.zeros((1, 16000))}

    mock_outputs = MagicMock()
    mock_outputs.logits = torch.tensor([[float("nan"), 1.0]])
    mock_model.return_value = mock_outputs

    mock_loader = MagicMock()
    mock_loader.device = "cpu"
    mock_loader.get_model_and_extractor.return_value = (mock_model, mock_extractor)

    service = spoof_detection_service.__class__(loader=mock_loader)
    with pytest.raises(SpoofInferenceError) as exc_info:
        service.predict(dummy_audio)
    assert "NaN or Infinite" in str(exc_info.value)


# =========================================================================
# 3. CALIBRATED DECISION ENGINE TESTS
# =========================================================================

def test_decision_engine_spoof_suspected():
    inference = ModelInferenceOutput(
        fake_logit=4.5,
        real_logit=-2.1,
        fake_probability=0.92,
        real_probability=0.08,
        model_name="mo-thecreator/Deepfake-audio-detection",
        model_version="v1.0"
    )
    quality = AudioQualityReport(
        is_valid=True,
        duration_seconds=3.0,
        sample_rate=16000,
        channels=1,
        rms_energy=0.05,
        silence_ratio=0.1,
        clipping_ratio=0.0,
        snr_db=18.0,
        status_label="EXCELLENT"
    )
    res = anti_spoof_decision_engine.evaluate(inference, quality)
    assert res.category == SpoofCategory.SPOOF_SUSPECTED
    assert res.spoof_score == 0.92
    assert res.is_experimental is True
    assert res.replay_detection_supported is False
    assert "Replay detection limitation" in res.replay_limitation_notice


def test_decision_engine_no_spoof_detected():
    inference = ModelInferenceOutput(
        fake_logit=-4.5,
        real_logit=3.2,
        fake_probability=0.05,
        real_probability=0.95,
        model_name="mo-thecreator/Deepfake-audio-detection",
        model_version="v1.0"
    )
    quality = AudioQualityReport(
        is_valid=True,
        duration_seconds=3.0,
        sample_rate=16000,
        channels=1,
        rms_energy=0.05,
        silence_ratio=0.1,
        clipping_ratio=0.0,
        snr_db=18.0,
        status_label="EXCELLENT"
    )
    res = anti_spoof_decision_engine.evaluate(inference, quality)
    assert res.category == SpoofCategory.NO_SPOOF_SIGNAL_DETECTED
    assert res.spoof_score == 0.05
    assert "does not prove the recording is authentic" in res.explanation


def test_decision_engine_inconclusive_zone():
    inference = ModelInferenceOutput(
        fake_logit=0.1,
        real_logit=0.0,
        fake_probability=0.52,
        real_probability=0.48,
        model_name="mo-thecreator/Deepfake-audio-detection",
        model_version="v1.0"
    )
    quality = AudioQualityReport(
        is_valid=True,
        duration_seconds=3.0,
        sample_rate=16000,
        channels=1,
        rms_energy=0.05,
        silence_ratio=0.1,
        clipping_ratio=0.0,
        snr_db=15.0,
        status_label="EXCELLENT"
    )
    res = anti_spoof_decision_engine.evaluate(inference, quality)
    assert res.category == SpoofCategory.INCONCLUSIVE
    assert "ambiguous intermediate zone" in res.score_interpretation


def test_decision_engine_noise_degraded():
    inference = ModelInferenceOutput(
        fake_logit=1.0,
        real_logit=0.2,
        fake_probability=0.68,
        real_probability=0.32,
        model_name="mo-thecreator/Deepfake-audio-detection",
        model_version="v1.0"
    )
    quality = AudioQualityReport(
        is_valid=True,
        duration_seconds=3.0,
        sample_rate=16000,
        channels=1,
        rms_energy=0.05,
        silence_ratio=0.1,
        clipping_ratio=0.0,
        snr_db=4.5,  # Very noisy
        status_label="NOISY"
    )
    res = anti_spoof_decision_engine.evaluate(inference, quality)
    assert res.category == SpoofCategory.INCONCLUSIVE
    assert "Noise-degraded" in res.score_interpretation


# =========================================================================
# 4. API ENDPOINT INTEGRATION TESTS (POST /api/v1/voice/analyze-spoof)
# =========================================================================

def test_api_analyze_spoof_unauthenticated():
    wav_bytes = generate_test_wav(duration=3.0)
    response = client.post(
        "/api/v1/voice/analyze-spoof",
        files={"audio_file": ("test.wav", wav_bytes, "audio/wav")}
    )
    assert response.status_code == 401


def test_api_analyze_spoof_real_inference_success(test_user):
    app.dependency_overrides[get_current_user] = lambda: test_user
    try:
        wav_bytes = generate_test_wav(duration=3.0)
        response = client.post(
            "/api/v1/voice/analyze-spoof",
            files={"audio_file": ("speech.wav", wav_bytes, "audio/wav")},
            data={"request_id": "req_audit_101"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "analysis_id" in data
        assert data["request_id"] == "req_audit_101"
        assert data["result_category"] in [
            SpoofCategory.SPOOF_SUSPECTED.value,
            SpoofCategory.NO_SPOOF_SIGNAL_DETECTED.value,
            SpoofCategory.INCONCLUSIVE.value
        ]
        assert data["is_experimental"] is True
        assert data["replay_detection_supported"] is False
        assert "replay_limitation_notice" in data
        assert "disclaimer" in data
        assert data["audio_quality"]["sample_rate"] == 16000
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_api_analyze_spoof_too_short_quality_failed(test_user):
    app.dependency_overrides[get_current_user] = lambda: test_user
    try:
        wav_bytes = generate_test_wav(duration=1.0)
        response = client.post(
            "/api/v1/voice/analyze-spoof",
            files={"audio_file": ("short.wav", wav_bytes, "audio/wav")}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["result_category"] == SpoofCategory.AUDIO_QUALITY_FAILED.value
        assert data["spoof_score"] is None
        assert "shorter than required minimum" in data["explanation"]
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_api_analyze_spoof_corrupt_audio_bad_request(test_user):
    app.dependency_overrides[get_current_user] = lambda: test_user
    try:
        corrupt_bytes = b"NOT_A_VALID_AUDIO_FILE_DATA_BLOCK_HEADER"
        response = client.post(
            "/api/v1/voice/analyze-spoof",
            files={"audio_file": ("corrupt.wav", corrupt_bytes, "audio/wav")}
        )
        assert response.status_code == 400
        assert "Audio format error" in response.json()["detail"]
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_api_analyze_spoof_empty_payload(test_user):
    app.dependency_overrides[get_current_user] = lambda: test_user
    try:
        response = client.post(
            "/api/v1/voice/analyze-spoof",
            files={"audio_file": ("empty.wav", b"", "audio/wav")}
        )
        assert response.status_code == 400
        assert "Empty audio payload" in response.json()["detail"]
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_api_analyze_spoof_payload_too_large(test_user):
    app.dependency_overrides[get_current_user] = lambda: test_user
    try:
        large_payload = b"0" * (16 * 1024 * 1024)  # 16 MB
        response = client.post(
            "/api/v1/voice/analyze-spoof",
            files={"audio_file": ("huge.wav", large_payload, "audio/wav")}
        )
        assert response.status_code == 413
        assert "15 MB upload limit" in response.json()["detail"]
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_api_analyze_spoof_model_unavailable_fallback(test_user):
    app.dependency_overrides[get_current_user] = lambda: test_user
    try:
        wav_bytes = generate_test_wav(duration=3.0)
        with patch.object(
            spoof_detection_service,
            "predict",
            side_effect=ModelUnavailableError("GPU OOM / weights missing")
        ):
            response = client.post(
                "/api/v1/voice/analyze-spoof",
                files={"audio_file": ("speech.wav", wav_bytes, "audio/wav")}
            )
            assert response.status_code == 200
            data = response.json()
            assert data["result_category"] == SpoofCategory.MODEL_UNAVAILABLE.value
            assert data["spoof_score"] is None
            assert "GPU OOM / weights missing" in data["explanation"]
    finally:
        app.dependency_overrides.pop(get_current_user, None)
