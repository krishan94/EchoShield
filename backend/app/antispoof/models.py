"""
EchoShield — Anti-Spoofing Pydantic API Schemas
================================================
Defines request validation and response models for deepfake voice detection,
preventing raw probability conflation and enforcing clear forensic metadata.
"""

from typing import Dict, Optional
from pydantic import BaseModel, Field


class AudioQualityMetricsResponse(BaseModel):
    """Acoustic inspection diagnostics."""
    duration_seconds: float = Field(..., description="Duration of processed audio in seconds")
    sample_rate: int = Field(..., description="Sample rate in Hz (standardized to 16,000 Hz)")
    channels: int = Field(..., description="Number of channels (standardized to mono)")
    rms_energy: float = Field(..., description="Root-mean-square amplitude energy")
    snr_db: float = Field(..., description="Estimated signal-to-noise ratio in decibels")
    clipping_ratio: float = Field(..., description="Fraction of samples reaching digital clipping ceiling")
    silence_ratio: float = Field(..., description="Fraction of frames below silence threshold")
    status_label: str = Field(..., description="Acoustic quality grading (e.g., EXCELLENT, FAIR, NOISY, TOO_SHORT)")
    error_message: Optional[str] = Field(None, description="Quality rejection reason if rejected")


class AntiSpoofResponse(BaseModel):
    """
    Forensic analysis outcome from the dedicated AI voice spoofing detection engine.
    """
    analysis_id: str = Field(..., description="Unique analysis record identifier")
    request_id: Optional[str] = Field(None, description="Client-provided idempotency or correlation ID")
    result_category: str = Field(
        ...,
        description=(
            "Verdict category: SPOOF_SUSPECTED, NO_SPOOF_SIGNAL_DETECTED, "
            "INCONCLUSIVE, AUDIO_QUALITY_FAILED, MODEL_UNAVAILABLE"
        )
    )
    spoof_score: Optional[float] = Field(
        None,
        description="Calibrated probability of synthetic/vocoder artifacts [0.0 - 1.0]"
    )
    confidence_score: Optional[float] = Field(
        None,
        description="Confidence magnitude for the emitted classification"
    )
    raw_scores: Dict[str, float] = Field(
        default_factory=dict,
        description="Raw neural network logits and uncalibrated probabilities"
    )
    score_interpretation: str = Field(..., description="Concise forensic interpretation of the score")
    model_name: str = Field(..., description="Name of the underlying acoustic anti-spoofing model")
    model_version: str = Field(..., description="Model checkpoint or release version")
    audio_quality: AudioQualityMetricsResponse = Field(..., description="Acoustic screening metrics")
    timestamp: str = Field(..., description="ISO-8601 UTC timestamp of analysis")
    explanation: str = Field(..., description="Detailed forensic context and recommended user posture")
    is_experimental: bool = Field(True, description="Always true: indicates experimental research status")
    replay_detection_supported: bool = Field(
        False,
        description="Explicitly indicates whether acoustic loudspeaker replay detection is supported"
    )
    replay_limitation_notice: str = Field(
        ...,
        description="Mandatory disclaimer detailing that physical replay is not detected by this model"
    )
    disclaimer: str = Field(..., description="Mandatory legal and operational advisory disclaimer")
