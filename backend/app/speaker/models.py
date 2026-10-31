"""
EchoShield — Speaker Recognition Pydantic Data Models
=====================================================
Request and response schemas for voice identity verification,
calibrated decision reporting, and verification history audit trails.
"""

from typing import List, Optional
from pydantic import BaseModel, Field


class VerificationResponse(BaseModel):
    """
    Structured outcome of a voice identity verification attempt.
    """
    verification_id: str = Field(..., description="Unique forensic verification audit identifier")
    member_id: str = Field(..., description="Target family member UID evaluated")
    member_name: str = Field(..., description="Target family member display name")
    result: str = Field(
        ...,
        description="Verification verdict: VOICE_MATCHED, NO_MATCH, INCONCLUSIVE, AUDIO_QUALITY_FAILED, PROFILE_NOT_ENROLLED"
    )
    similarity_score: Optional[float] = Field(
        None,
        description="Cosine similarity score against enrolled reference embedding (model measurement, not probability)"
    )
    match_threshold: float = Field(0.72, description="Configured decision threshold for positive voice match")
    non_match_threshold: float = Field(0.58, description="Configured decision boundary for distinct voice")
    decision_status: str = Field(..., description="Operating band: high_match, non_match, uncertain_band, quality_rejected")
    audio_quality_status: str = Field(..., description="Acoustic validation outcome: acceptable, rejected")
    model_name: str = Field("SpeechBrain ECAPA-TDNN", description="Underlying neural acoustic model")
    model_version: str = Field("voxceleb-v1.1", description="Model training checkpoint version")
    explanation: str = Field(..., description="Human-readable decision rationale and forensic context")
    verified_at: str = Field(..., description="ISO-8601 UTC timestamp of verification")


class VerificationHistoryItem(BaseModel):
    """
    Audit log entry of a historical voice verification event.
    """
    id: str = Field(..., description="Verification record ID")
    user_id: str = Field(..., description="UID of user who performed verification")
    member_id: str = Field(..., description="UID of verified family member")
    member_name: str = Field(..., description="Name of verified family member")
    result: str = Field(..., description="Decision verdict category")
    similarity_score: Optional[float] = Field(None, description="Cosine similarity score")
    confidence_band: str = Field(..., description="Operating decision band")
    audio_quality_status: str = Field(..., description="Acoustic check status")
    duration_seconds: float = Field(0.0, description="Audio sample duration in seconds")
    snr_db: float = Field(0.0, description="Signal-to-noise ratio in decibels")
    model_name: str = Field("SpeechBrain ECAPA-TDNN", description="Model architecture")
    model_version: str = Field("voxceleb-v1.1", description="Model version")
    verified_at: str = Field(..., description="ISO-8601 UTC timestamp")


class VerificationHistoryResponse(BaseModel):
    """
    List of verification history audit logs.
    """
    records: List[VerificationHistoryItem]
    total_count: int


class ClearHistoryResponse(BaseModel):
    """
    Response acknowledging deletion of verification history records.
    """
    deleted_count: int
    message: str
