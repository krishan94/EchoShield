"""
EchoShield — Anti-Spoofing & Deepfake Audio Detection Router
============================================================
FastAPI routes mounted under /api/v1/voice:
- POST /api/v1/voice/analyze-spoof: Analyzes uploaded speech for synthetic voice/vocoder artifacts.
"""

import logging
import os
import tempfile
import uuid
from typing import Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status

from app.auth.models import AuthenticatedUser
from app.auth.security import get_current_user
from app.antispoof.models import AntiSpoofResponse, AudioQualityMetricsResponse
from app.services.anti_spoofing import (
    AntiSpoofError,
    AudioPreprocessingError,
    AudioQualityError,
    ModelUnavailableError,
    SpoofCategory,
    SpoofInferenceError,
    anti_spoof_decision_engine,
    anti_spoof_preprocessor,
    spoof_detection_service,
)

logger = logging.getLogger("echoshield.api.antispoof")

router = APIRouter(prefix="/voice", tags=["AI Voice Spoofing & Deepfake Detection"])

MAX_AUDIO_SIZE_BYTES = 15 * 1024 * 1024  # 15 MB limit


@router.post(
    "/analyze-spoof",
    response_model=AntiSpoofResponse,
    status_code=status.HTTP_200_OK,
    summary="Analyze Audio for AI Voice Spoofing & Deepfake Signatures",
    description=(
        "Analyzes an uploaded speech sample against the pretrained Wav2Vec2 deepfake audio model. "
        "Evaluates synthetic speech, neural vocoder artifacts, and voice conversion cues. "
        "Strictly disclaims acoustic physical replay detection. Audio is processed transiently and "
        "purged immediately after inference with zero raw audio retention."
    )
)
async def analyze_spoof(
    audio_file: UploadFile = File(..., description="WAV/PCM audio file (2.0s - 15.0s, 16kHz recommended)"),
    request_id: Optional[str] = Form(None, description="Optional client-side correlation or idempotency ID"),
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> AntiSpoofResponse:
    """
    Forensic endpoint for inspecting an audio sample for synthetic generation / vocoder artifacts.
    """
    analysis_id = f"spf_{uuid.uuid4().hex[:16]}"
    temp_path: Optional[str] = None

    try:
        # 1. Read audio bytes and enforce size quota
        audio_bytes = await audio_file.read()
        if not audio_bytes:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Empty audio payload received."
            )

        if len(audio_bytes) > MAX_AUDIO_SIZE_BYTES:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail=f"Audio payload ({len(audio_bytes)} bytes) exceeds the 15 MB upload limit."
            )

        # 2. Transient secure temporary file (for inspection/cleanup guarantees)
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            tmp.write(audio_bytes)
            temp_path = tmp.name

        # 3. Audio decoding & acoustic quality preprocessing
        try:
            audio_data, quality_report = anti_spoof_preprocessor.preprocess_and_validate(
                audio_bytes, filename=audio_file.filename or "audio.wav"
            )
        except AudioQualityError as q_err:
            logger.warning(f"Audio quality rejected for analysis {analysis_id}: {q_err}")
            # Format into quality failure response
            quality_dto = AudioQualityMetricsResponse(
                duration_seconds=0.0,
                sample_rate=16000,
                channels=1,
                rms_energy=0.0,
                snr_db=0.0,
                clipping_ratio=0.0,
                silence_ratio=0.0,
                status_label="REJECTED",
                error_message=q_err.message
            )
            failed_result = anti_spoof_decision_engine.create_quality_failed_result(
                quality=quality_report if 'quality_report' in locals() else None,  # type: ignore
                error_message=q_err.message
            ) if 'quality_report' in locals() else None

            return AntiSpoofResponse(
                analysis_id=analysis_id,
                request_id=request_id,
                result_category=SpoofCategory.AUDIO_QUALITY_FAILED.value,
                spoof_score=None,
                confidence_score=None,
                raw_scores={},
                score_interpretation="Audio failed acoustic quality criteria.",
                model_name="mo-thecreator/Deepfake-audio-detection (Wav2Vec2)",
                model_version="v1.0-hf",
                audio_quality=quality_dto,
                timestamp=failed_result.timestamp if failed_result else "",
                explanation=q_err.message,
                is_experimental=True,
                replay_detection_supported=False,
                replay_limitation_notice=(
                    "Replay detection limitation: This model inspects digital synthesis and vocoder artifacts "
                    "(TTS/VC). Physical loudspeaker replay playback is NOT evaluated by this detector."
                ),
                disclaimer=(
                    "Experimental anti-spoofing evaluation. 'NO_SPOOF_SIGNAL_DETECTED' does NOT prove "
                    "authenticity or genuine identity. Always corroborate with Phase 5 speaker verification."
                )
            )
        except AudioPreprocessingError as p_err:
            logger.warning(f"Audio preprocessing decoding error for analysis {analysis_id}: {p_err}")
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Audio format error: {p_err.message}"
            )

        # 4. Neural Network Inference
        try:
            inference_output = spoof_detection_service.predict(audio_data)
        except ModelUnavailableError as m_err:
            logger.error(f"Anti-spoofing model unavailable during analysis {analysis_id}: {m_err}")
            unavail_result = anti_spoof_decision_engine.create_model_unavailable_result(
                quality=quality_report,
                error_message=m_err.message
            )
            return AntiSpoofResponse(
                analysis_id=analysis_id,
                request_id=request_id,
                result_category=SpoofCategory.MODEL_UNAVAILABLE.value,
                spoof_score=None,
                confidence_score=None,
                raw_scores={},
                score_interpretation="Deepfake detection model is temporarily offline or unavailable.",
                model_name=unavail_result.model_name,
                model_version=unavail_result.model_version,
                audio_quality=AudioQualityMetricsResponse(
                    duration_seconds=quality_report.duration_seconds,
                    sample_rate=quality_report.sample_rate,
                    channels=quality_report.channels,
                    rms_energy=quality_report.rms_energy,
                    snr_db=quality_report.snr_db,
                    clipping_ratio=quality_report.clipping_ratio,
                    silence_ratio=quality_report.silence_ratio,
                    status_label=quality_report.status_label,
                    error_message=quality_report.error_message
                ),
                timestamp=unavail_result.timestamp,
                explanation=unavail_result.explanation,
                is_experimental=True,
                replay_detection_supported=False,
                replay_limitation_notice=unavail_result.replay_limitation_notice,
                disclaimer=unavail_result.disclaimer
            )
        except SpoofInferenceError as inf_err:
            logger.error(f"Inference computation error in analysis {analysis_id}: {inf_err}")
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Inference execution failure: {inf_err.message}"
            )

        # 5. Calibrated Verdict Formulation
        decision = anti_spoof_decision_engine.evaluate(inference_output, quality_report)

        return AntiSpoofResponse(
            analysis_id=analysis_id,
            request_id=request_id,
            result_category=decision.category.value,
            spoof_score=decision.spoof_score,
            confidence_score=decision.confidence_score,
            raw_scores=decision.raw_scores,
            score_interpretation=decision.score_interpretation,
            model_name=decision.model_name,
            model_version=decision.model_version,
            audio_quality=AudioQualityMetricsResponse(
                duration_seconds=quality_report.duration_seconds,
                sample_rate=quality_report.sample_rate,
                channels=quality_report.channels,
                rms_energy=quality_report.rms_energy,
                snr_db=quality_report.snr_db,
                clipping_ratio=quality_report.clipping_ratio,
                silence_ratio=quality_report.silence_ratio,
                status_label=quality_report.status_label,
                error_message=quality_report.error_message
            ),
            timestamp=decision.timestamp,
            explanation=decision.explanation,
            is_experimental=decision.is_experimental,
            replay_detection_supported=decision.replay_detection_supported,
            replay_limitation_notice=decision.replay_limitation_notice,
            disclaimer=decision.disclaimer
        )

    finally:
        # Guarantee transient temporary audio cleanup: Zero raw audio retention
        if temp_path and os.path.exists(temp_path):
            try:
                os.unlink(temp_path)
            except Exception as e:
                logger.warning(f"Failed to remove temporary audio file '{temp_path}': {e}")

