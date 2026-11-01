"""
EchoShield — Combined Security Decision Engine Pydantic Schemas
==============================================================
Defines the request and response data contracts for the central Combined
Security Decision Engine (Phase 7), synthesizing Phase 5 speaker recognition
and Phase 6 AI anti-spoofing into a unified cybersecurity verdict.
"""

from enum import Enum
from typing import Dict, List, Optional
from pydantic import BaseModel, Field


class CombinedSecurityOutcome(str, Enum):
    """
    Final decision verdict states for the EchoShield Combined Security Engine.
    """
    IDENTITY_CONFIRMED = "IDENTITY_CONFIRMED"
    SUSPICIOUS_AUDIO = "SUSPICIOUS_AUDIO"
    IDENTITY_NOT_CONFIRMED = "IDENTITY_NOT_CONFIRMED"
    INCONCLUSIVE = "INCONCLUSIVE"
    ANALYSIS_UNAVAILABLE = "ANALYSIS_UNAVAILABLE"
    AUDIO_QUALITY_FAILED = "AUDIO_QUALITY_FAILED"
    PROFILE_NOT_ENROLLED = "PROFILE_NOT_ENROLLED"


class SpeakerVerificationSummary(BaseModel):
    """Summarized outcome from the SpeechBrain ECAPA-TDNN speaker verification system."""
    result: str = Field(..., description="Verdict: VOICE_MATCHED, NO_MATCH, INCONCLUSIVE, AUDIO_QUALITY_FAILED, PROFILE_NOT_ENROLLED")
    similarity_score: Optional[float] = Field(None, description="Cosine similarity score against enrolled biometric reference")
    match_threshold: float = Field(0.72, description="Operating threshold for positive voice identity match")
    non_match_threshold: float = Field(0.58, description="Operating threshold below which identity is considered a distinct non-match")
    decision_status: str = Field(..., description="Decision band: high_match, non_match, uncertain_band, quality_rejected, not_enrolled")
    explanation: str = Field(..., description="Forensic rationale from speaker recognition model")
    model_name: str = Field("SpeechBrain ECAPA-TDNN", description="Neural architecture used for embedding extraction")
    model_version: str = Field("voxceleb-v1.1", description="Model checkpoint version")


class AntiSpoofSummary(BaseModel):
    """Summarized outcome from the Wav2Vec2 AI deepfake and spoofing detection system."""
    result_category: str = Field(..., description="Verdict: SPOOF_SUSPECTED, NO_SPOOF_SIGNAL_DETECTED, INCONCLUSIVE, AUDIO_QUALITY_FAILED, MODEL_UNAVAILABLE")
    spoof_score: Optional[float] = Field(None, description="Calibrated probability of synthetic/vocoder speech artifacts [0.0 - 1.0]")
    confidence_score: Optional[float] = Field(None, description="Confidence magnitude for anti-spoof classification")
    score_interpretation: str = Field(..., description="Forensic interpretation of spoof detection score")
    model_name: str = Field("mo-thecreator/Deepfake-audio-detection (Wav2Vec2)", description="Model architecture")
    model_version: str = Field("v1.0-hf", description="Model release version")
    replay_detection_supported: bool = Field(False, description="Whether acoustic loudspeaker replay detection is supported")
    replay_limitation_notice: str = Field(..., description="Mandatory notice that physical loudspeaker replay is not detected")


class AcousticScreeningSummary(BaseModel):
    """Acoustic inspection diagnostics."""
    duration_seconds: float = Field(..., description="Processed audio duration in seconds")
    sample_rate: int = Field(16000, description="Sampling rate in Hz")
    snr_db: float = Field(..., description="Estimated signal-to-noise ratio in decibels")
    rms_energy: float = Field(..., description="Root-mean-square amplitude energy")
    status_label: str = Field(..., description="Quality grading label (e.g. EXCELLENT, FAIR, NOISY, REJECTED)")
    is_acceptable: bool = Field(..., description="Whether acoustic quality meets biometric standards")
    rejection_reason: Optional[str] = Field(None, description="Detailed acoustic rejection reason if rejected")


class CombinedSecurityResponse(BaseModel):
    """
    Unified forensic response from the EchoShield Combined Security Decision Engine.
    Consumes outputs from both neural pipelines and enforces strict conflict precedence.
    """
    analysis_id: str = Field(..., description="Unique security audit record identifier")
    request_id: Optional[str] = Field(None, description="Client-provided idempotency or correlation ID")
    target_member_id: str = Field(..., description="Target family member UID evaluated")
    target_member_name: str = Field(..., description="Target family member display name")
    combined_decision: CombinedSecurityOutcome = Field(
        ...,
        description="Unified security verdict: IDENTITY_CONFIRMED, SUSPICIOUS_AUDIO, IDENTITY_NOT_CONFIRMED, INCONCLUSIVE, ANALYSIS_UNAVAILABLE, AUDIO_QUALITY_FAILED, PROFILE_NOT_ENROLLED"
    )
    decision_headline: str = Field(..., description="Concise, high-visibility cybersecurity status headline")
    explanation: str = Field(..., description="Forensic synthesis detailing how the decision was reached")
    decision_factors: List[str] = Field(default_factory=list, description="Granular contributing factors that determined the decision")
    speaker_verification: Optional[SpeakerVerificationSummary] = Field(None, description="Underlying speaker identity verification outcome")
    anti_spoofing: Optional[AntiSpoofSummary] = Field(None, description="Underlying AI deepfake / voice spoofing detection outcome")
    audio_quality: AcousticScreeningSummary = Field(..., description="Acoustic quality assessment metrics")
    limitations: List[str] = Field(default_factory=list, description="Forensic caveats, replay limits, and operational limitations")
    is_experimental: bool = Field(True, description="Always true: anti-spoofing heuristics are experimental")
    analyzed_at: str = Field(..., description="ISO-8601 UTC timestamp of analysis")
    disclaimer: str = Field(
        "EchoShield Voice Security Engine provides calibrated probabilistic defense against voice impersonation. "
        "No biometric or AI deepfake detection system can guarantee 100% detection of zero-day synthetic audio. "
        "Never use voice alone to authorize irrevocable high-stakes financial transactions.",
        description="Mandatory operational disclaimer"
    )

