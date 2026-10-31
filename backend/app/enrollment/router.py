import logging
from typing import List

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status

from app.auth.models import AuthenticatedUser
from app.auth.security import get_current_user
from app.enrollment.models import (
    ConsentResponse,
    EnrollmentResponse,
    VoiceConsentRequest,
    VoiceProfileDeleteResponse,
    VoiceProfileResponse,
)
from app.enrollment.service import voice_service

logger = logging.getLogger("echoshield.enrollment.router")

router = APIRouter(prefix="/voice", tags=["Voice Enrollment & Biometrics"])

MAX_AUDIO_FILE_SIZE = 10 * 1024 * 1024  # 10 MB per sample max


@router.post(
    "/consent",
    response_model=ConsentResponse,
    summary="Record informed biometric voice consent",
    description="Registers explicit informed biometric consent before capturing voice samples."
)
async def record_consent(
    request: VoiceConsentRequest,
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> ConsentResponse:
    return voice_service.record_consent(current_user, request)


@router.post(
    "/enroll",
    response_model=EnrollmentResponse,
    summary="Enroll biometric voice profile",
    description="Accepts 3 independent 16kHz PCM WAV speech samples, validates quality, extracts ECAPA-TDNN embeddings, and encrypts profile."
)
async def enroll_voice(
    sample_1: UploadFile = File(..., description="First 16kHz WAV speech sample"),
    sample_2: UploadFile = File(..., description="Second 16kHz WAV speech sample"),
    sample_3: UploadFile = File(..., description="Third 16kHz WAV speech sample"),
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> EnrollmentResponse:
    uploaded_files = [sample_1, sample_2, sample_3]
    samples_bytes: List[bytes] = []

    for idx, f in enumerate(uploaded_files):
        # Validate filename and WAV content type
        content_type = f.content_type or ""
        filename = (f.filename or "").lower()
        if not (filename.endswith(".wav") or "wav" in content_type.lower()):
            raise HTTPException(
                status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
                detail=f"Sample #{idx + 1} is not a valid WAV audio file. Received format: '{content_type}'."
            )

        data = await f.read()
        if len(data) == 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Sample #{idx + 1} audio file is completely empty."
            )
        if len(data) > MAX_AUDIO_FILE_SIZE:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail=f"Sample #{idx + 1} exceeds the 10 MB size limit."
            )
        samples_bytes.append(data)

    return voice_service.enroll_voice(current_user, samples_bytes)


@router.get(
    "/profile",
    response_model=VoiceProfileResponse,
    summary="Get user voice certificate status",
    description="Returns biometric enrollment status, quality score, and model metadata. Never exposes raw embeddings."
)
async def get_voice_profile(
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> VoiceProfileResponse:
    return voice_service.get_voice_profile(current_user)


@router.put(
    "/profile",
    response_model=EnrollmentResponse,
    summary="Re-enroll or update biometric voice signature",
    description="Allows a user to re-record and update their registered voice profile with new samples."
)
async def update_voice_profile(
    sample_1: UploadFile = File(..., description="First 16kHz WAV speech sample"),
    sample_2: UploadFile = File(..., description="Second 16kHz WAV speech sample"),
    sample_3: UploadFile = File(..., description="Third 16kHz WAV speech sample"),
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> EnrollmentResponse:
    return await enroll_voice(sample_1, sample_2, sample_3, current_user)


@router.delete(
    "/profile",
    response_model=VoiceProfileDeleteResponse,
    summary="Delete voice profile and biometric embeddings",
    description="Cryptographically purges the user's stored voice embeddings, revokes consent, and resets enrollment status."
)
async def delete_voice_profile(
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> VoiceProfileDeleteResponse:
    return voice_service.delete_voice_profile(current_user)
