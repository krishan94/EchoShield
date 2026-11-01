"""
EchoShield — Combined Security Decision Engine Router
=====================================================
FastAPI routes mounted under /api/v1/voice for:
- POST /api/v1/voice/analyze-security: Dual biometric speaker recognition
  and deepfake audio anti-spoofing analysis with unified conflict resolution.
"""

import logging
from typing import Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status

from app.auth.models import AuthenticatedUser
from app.auth.security import get_current_user
from app.combined_security.models import CombinedSecurityResponse
from app.services.combined_security import combined_security_service
from app.services.speaker_recognition import (
    BiometricConsentMissingError,
    UnauthorizedAccessError,
)

logger = logging.getLogger("echoshield.api.combined_security")

router = APIRouter(prefix="/voice", tags=["Combined Security Decision Engine"])

MAX_AUDIO_SIZE_BYTES = 15 * 1024 * 1024  # 15 MB limit


@router.post(
    "/analyze-security",
    response_model=CombinedSecurityResponse,
    status_code=status.HTTP_200_OK,
    summary="Execute Dual Biometric Speaker Recognition & Deepfake Anti-Spoofing Analysis",
    description=(
        "Forensic endpoint combining Phase 5 ECAPA-TDNN speaker verification and Phase 6 Wav2Vec2 "
        "AI deepfake voice detection into a single synthesized security verdict. "
        "Strictly enforces that anti-spoofing alerts override speaker similarity. "
        "Audio is processed transiently in memory with zero raw audio persistence."
    )
)
async def analyze_security(
    member_id: str = Form(..., description="Target family member UID to verify against"),
    audio_file: UploadFile = File(..., description="16kHz 16-bit PCM WAV audio recording"),
    request_id: Optional[str] = Form(None, description="Optional idempotency or correlation ID"),
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> CombinedSecurityResponse:
    """
    Executes the central Combined Security Decision Engine.
    """
    # 1. Validate file extension and MIME type
    filename = audio_file.filename or "sample.wav"
    content_type = (audio_file.content_type or "").lower()

    if not (filename.lower().endswith(".wav") or "wav" in content_type):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Unsupported audio format '{content_type or filename}'. Only 16kHz PCM WAV files are supported."
        )

    # 2. Read and validate payload size
    audio_bytes = await audio_file.read()
    if not audio_bytes or len(audio_bytes) == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="The uploaded audio file is empty."
        )

    if len(audio_bytes) > MAX_AUDIO_SIZE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Audio payload ({len(audio_bytes)} bytes) exceeds the 15 MB upload limit."
        )

    # 3. Execute combined security service
    try:
        return await combined_security_service.analyze_security(
            current_user=current_user,
            target_member_id=member_id.strip(),
            audio_bytes=audio_bytes,
            filename=filename,
            request_id=request_id
        )

    except UnauthorizedAccessError as ue:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=str(ue)
        )

    except BiometricConsentMissingError as ce:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=str(ce)
        )

    except HTTPException:
        raise

    except Exception as exc:
        logger.error(f"Unexpected error in combined security analysis: {exc}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Internal error processing security analysis: {str(exc)}"
        )

