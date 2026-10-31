"""
EchoShield — Speaker Recognition & Voice Verification Router
============================================================
FastAPI routes mounted under /api/v1/voice for:
- POST /api/v1/voice/verify: Real-time speaker verification against enrolled profile
- GET /api/v1/voice/verification-history: Audit log retrieval
- DELETE /api/v1/voice/verification-history: Audit trail purge
"""

import logging

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, UploadFile, status

from app.auth.models import AuthenticatedUser
from app.auth.security import get_current_user
from app.services.speaker_recognition import (
    AudioPreprocessingError,
    BiometricConsentMissingError,
    IncompatibleEmbeddingError,
    ModelLoadError,
    ProfileNotEnrolledError,
    UnauthorizedAccessError,
    decision_engine,
    verification_service,
)
from app.speaker.models import (
    ClearHistoryResponse,
    VerificationHistoryItem,
    VerificationHistoryResponse,
    VerificationResponse,
)

logger = logging.getLogger("echoshield.api.speaker")

router = APIRouter(prefix="/voice", tags=["Speaker Recognition & Verification"])

MAX_AUDIO_SIZE_BYTES = 15 * 1024 * 1024  # 15 MB


@router.post(
    "/verify",
    response_model=VerificationResponse,
    status_code=status.HTTP_200_OK,
    summary="Verify Voice Sample against Enrolled Family Member",
    description=(
        "Performs speaker verification by extracting an ECAPA-TDNN 192-dim embedding from the "
        "uploaded audio sample and comparing it against the family member's AES-256-GCM encrypted "
        "enrolled biometric baseline. Enforces active consent and relationship authorization."
    )
)
async def verify_voice(
    member_id: str = Form(..., description="Target family member UID to verify against"),
    audio_file: UploadFile = File(..., description="16kHz 16-bit PCM WAV audio recording"),
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> VerificationResponse:
    """
    Executes real-time speaker verification against an authorized family member's enrolled voice.
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
            detail=f"Audio file exceeds maximum size limit of {MAX_AUDIO_SIZE_BYTES // (1024 * 1024)}MB."
        )

    # 3. Execute verification pipeline
    try:
        result_dict = verification_service.verify_voice_sample(
            current_user=current_user,
            target_member_id=member_id.strip(),
            audio_bytes=audio_bytes,
            filename=filename
        )
        return VerificationResponse(**result_dict)

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

    except ProfileNotEnrolledError:
        # Return structured outcome indicating the target member has not enrolled
        decision = decision_engine.evaluate_profile_not_enrolled(member_name="Family Member")
        from datetime import datetime, timezone
        return VerificationResponse(
            verification_id=f"verif_unenrolled_{member_id[:8]}",
            member_id=member_id,
            member_name="Family Member",
            result=decision.category,
            similarity_score=None,
            match_threshold=decision.match_threshold,
            non_match_threshold=decision.non_match_threshold,
            decision_status=decision.confidence_band,
            audio_quality_status="bypassed",
            model_name="SpeechBrain ECAPA-TDNN",
            model_version="voxceleb-v1.1",
            explanation=decision.explanation,
            verified_at=datetime.now(timezone.utc).isoformat()
        )

    except AudioPreprocessingError as pe:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(pe)
        )

    except IncompatibleEmbeddingError as ie:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(ie)
        )

    except ModelLoadError as me:
        logger.error("Model failure during verification: %s", str(me))
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"AI model inference service unavailable: {str(me)}"
        )

    except Exception as e:
        logger.error("Unexpected error in voice verification: %s", str(e), exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Voice verification failed due to internal error: {str(e)}"
        )


@router.get(
    "/verification-history",
    response_model=VerificationHistoryResponse,
    status_code=status.HTTP_200_OK,
    summary="Get Verification History",
    description="Retrieves the chronological audit log of voice verifications performed by the user."
)
async def get_verification_history(
    limit: int = Query(50, ge=1, le=200, description="Max records to return"),
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> VerificationHistoryResponse:
    """
    Returns authenticated user's verification audit history.
    """
    records = verification_service.get_verification_history(current_user=current_user, limit=limit)
    items = [VerificationHistoryItem(**r) for r in records]
    return VerificationHistoryResponse(records=items, total_count=len(items))


@router.delete(
    "/verification-history",
    response_model=ClearHistoryResponse,
    status_code=status.HTTP_200_OK,
    summary="Clear Verification History",
    description="Permanently deletes the requesting user's verification history records."
)
async def clear_verification_history(
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> ClearHistoryResponse:
    """
    Purges verification history records for the current authenticated user.
    """
    count = verification_service.clear_verification_history(current_user=current_user)
    return ClearHistoryResponse(
        deleted_count=count,
        message=f"Successfully purged {count} verification history record(s)."
    )
