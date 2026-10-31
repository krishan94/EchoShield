"""
EchoShield — Anti-Spoofing Service Schemas & Data Structures
============================================================
Data models and typed representations for AI voice spoofing detection,
acoustic quality metrics, and calibrated suspicion outcomes.
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, Optional


class SpoofCategory(str, Enum):
    """
    Five distinct, non-conflated verdict categories for audio anti-spoofing.
    Never presents raw similarity as 100% genuine or 100% fake.
    """
    SPOOF_SUSPECTED = "SPOOF_SUSPECTED"
    NO_SPOOF_SIGNAL_DETECTED = "NO_SPOOF_SIGNAL_DETECTED"
    INCONCLUSIVE = "INCONCLUSIVE"
    AUDIO_QUALITY_FAILED = "AUDIO_QUALITY_FAILED"
    MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE"


@dataclass
class AudioQualityReport:
    """Acoustic inspection metrics assessed prior to model inference."""
    is_valid: bool
    duration_seconds: float
    sample_rate: int
    channels: int
    rms_energy: float
    silence_ratio: float
    clipping_ratio: float
    snr_db: float
    status_label: str
    error_message: Optional[str] = None


@dataclass
class ModelInferenceOutput:
    """Raw mathematical outputs produced by the sequence classification head."""
    fake_logit: float
    real_logit: float
    fake_probability: float
    real_probability: float
    model_name: str
    model_version: str


@dataclass
class AntiSpoofResult:
    """
    Complete anti-spoofing decision and diagnostic payload.
    Separates synthetic/vocoder detection from physical acoustic replay.
    """
    category: SpoofCategory
    spoof_score: Optional[float]  # Calibrated probability [0.0, 1.0] or None if unavailable/quality failed
    confidence_score: Optional[float]
    raw_scores: Dict[str, float]
    score_interpretation: str
    model_name: str
    model_version: str
    audio_quality: AudioQualityReport
    timestamp: str
    explanation: str
    is_experimental: bool = True
    replay_detection_supported: bool = False
    replay_limitation_notice: str = (
        "Replay detection limitation: This model inspects digital synthesis and vocoder artifacts "
        "(TTS/VC). Physical loudspeaker replay playback is NOT evaluated by this detector."
    )
    disclaimer: str = (
        "Experimental anti-spoofing evaluation. 'NO_SPOOF_SIGNAL_DETECTED' does NOT prove "
        "authenticity or genuine identity. Always corroborate with Phase 5 speaker verification."
    )
