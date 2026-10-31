from typing import List, Optional
from pydantic import BaseModel, Field


class VoiceConsentRequest(BaseModel):
    consent_status: str = Field(..., description="'granted' or 'revoked'")
    consent_version: str = Field(default="1.0", description="Version of biometric disclosure agreement")
    consent_timestamp: Optional[str] = Field(default=None, description="Client ISO-8601 timestamp")


class ConsentResponse(BaseModel):
    user_id: str
    consent_status: str
    consent_version: str
    consent_timestamp: str
    message: str


class SampleQualitySummary(BaseModel):
    sample_index: int
    duration_seconds: float
    rms_energy: float
    snr_db: float
    status: str


class EnrollmentResponse(BaseModel):
    user_id: str
    enrollment_status: str
    voice_quality_score: float
    sample_count: int
    embedding_model: str
    model_version: str
    enrolled_at: str
    samples_summary: List[SampleQualitySummary]
    message: str


class VoiceProfileResponse(BaseModel):
    user_id: str
    enrollment_status: str
    is_voice_enrolled: bool
    voice_quality_score: float
    sample_count: int
    embedding_model: str
    model_version: str
    consent_status: str
    consent_version: str
    enrolled_at: Optional[str] = None
    updated_at: str


class VoiceProfileDeleteResponse(BaseModel):
    user_id: str
    status: str
    message: str
