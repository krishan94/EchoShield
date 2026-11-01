"""
EchoShield — Combined Security Service
======================================
Orchestrates concurrent execution of:
1. Phase 5 ECAPA-TDNN Speaker Verification
2. Phase 6 Wav2Vec2 AI Anti-Spoofing & Deepfake Detection
3. Central Combined Security Decision Engine synthesis

Guarantees:
- Transient in-memory audio lifecycle; zero permanent raw audio retention.
- Zero raw embeddings or private model weights exposed to API clients.
- Immutable audit log in verification_history for forensics.
"""

import asyncio
import logging
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import HTTPException, status

from app.auth.models import AuthenticatedUser
from app.combined_security.models import (
    AcousticScreeningSummary,
    AntiSpoofSummary,
    CombinedSecurityOutcome,
    CombinedSecurityResponse,
    SpeakerVerificationSummary,
)
from app.services.anti_spoofing import (
    AudioPreprocessingError,
    AudioQualityError,
    ModelUnavailableError,
    SpoofCategory,
    SpoofInferenceError,
    anti_spoof_decision_engine,
    anti_spoof_preprocessor,
    spoof_detection_service,
)
from app.services.combined_security.decision_engine import combined_decision_engine
from app.services.speaker_recognition import (
    BiometricConsentMissingError,
    IncompatibleEmbeddingError,
    ProfileNotEnrolledError,
    UnauthorizedAccessError,
    verification_service,
)

logger = logging.getLogger("echoshield.services.combined_security")


class CombinedSecurityService:
    """
    Coordinates simultaneous biometric speaker identity recognition and
    deepfake voice anti-spoofing to protect family members from impersonation scams.
    """

    def __init__(self, use_in_memory: bool = False):
        self.use_in_memory = use_in_memory
        self.speaker_svc = verification_service
        self.decision_engine = combined_decision_engine

    async def analyze_security(
        self,
        current_user: AuthenticatedUser,
        target_member_id: str,
        audio_bytes: bytes,
        filename: str = "audio.wav",
        request_id: Optional[str] = None
    ) -> CombinedSecurityResponse:
        """
        Executes complete combined security evaluation.
        """
        analysis_id = f"sec_{uuid.uuid4().hex[:14]}"
        now_iso = datetime.now(timezone.utc).isoformat()
        target_member_name = self.speaker_svc._lookup_member_name(target_member_id, current_user)

        # -------------------------------------------------------------
        # 1. Authorization Check (Family Circle Relationship)
        # -------------------------------------------------------------
        self.speaker_svc._check_family_authorization(current_user.uid, target_member_id)

        # -------------------------------------------------------------
        # 2. Informed Biometric Consent Check
        # -------------------------------------------------------------
        self.speaker_svc._check_member_consent(target_member_id)

        # -------------------------------------------------------------
        # 3. Voice Profile Enrollment Check
        # -------------------------------------------------------------
        profile_enrolled = True
        try:
            self.speaker_svc._get_enrolled_embedding(target_member_id)
        except ProfileNotEnrolledError:
            profile_enrolled = False
        except IncompatibleEmbeddingError:
            profile_enrolled = False

        if not profile_enrolled:
            engine_out = self.decision_engine.evaluate(
                target_member_name=target_member_name,
                profile_enrolled=False,
                speaker_data=None,
                spoof_data=None
            )
            return CombinedSecurityResponse(
                analysis_id=analysis_id,
                request_id=request_id,
                target_member_id=target_member_id,
                target_member_name=target_member_name,
                combined_decision=engine_out.outcome,
                decision_headline=engine_out.headline,
                explanation=engine_out.explanation,
                decision_factors=engine_out.decision_factors,
                speaker_verification=None,
                anti_spoofing=None,
                audio_quality=AcousticScreeningSummary(
                    duration_seconds=0.0,
                    sample_rate=16000,
                    snr_db=0.0,
                    rms_energy=0.0,
                    status_label="NOT_EVALUATED",
                    is_acceptable=False,
                    rejection_reason="Target member has not enrolled a voice profile."
                ),
                limitations=engine_out.limitations,
                is_experimental=True,
                analyzed_at=now_iso
            )

        # -------------------------------------------------------------
        # 4. Acoustic Screening & Preprocessing
        # -------------------------------------------------------------
        try:
            audio_data, quality_report = anti_spoof_preprocessor.preprocess_and_validate(
                audio_bytes, filename=filename
            )
            acoustic_summary = AcousticScreeningSummary(
                duration_seconds=quality_report.duration_seconds,
                sample_rate=quality_report.sample_rate,
                snr_db=quality_report.snr_db,
                rms_energy=quality_report.rms_energy,
                status_label=quality_report.status_label,
                is_acceptable=True,
                rejection_reason=None
            )
        except AudioQualityError as q_err:
            acoustic_summary = AcousticScreeningSummary(
                duration_seconds=0.0,
                sample_rate=16000,
                snr_db=0.0,
                rms_energy=0.0,
                status_label="REJECTED",
                is_acceptable=False,
                rejection_reason=q_err.message
            )
            engine_out = self.decision_engine.evaluate(
                target_member_name=target_member_name,
                profile_enrolled=True,
                speaker_data={"result": "AUDIO_QUALITY_FAILED", "explanation": q_err.message},
                spoof_data={"result_category": "AUDIO_QUALITY_FAILED", "explanation": q_err.message},
                quality_data=acoustic_summary
            )
            return CombinedSecurityResponse(
                analysis_id=analysis_id,
                request_id=request_id,
                target_member_id=target_member_id,
                target_member_name=target_member_name,
                combined_decision=engine_out.outcome,
                decision_headline=engine_out.headline,
                explanation=engine_out.explanation,
                decision_factors=engine_out.decision_factors,
                speaker_verification=None,
                anti_spoofing=None,
                audio_quality=acoustic_summary,
                limitations=engine_out.limitations,
                is_experimental=True,
                analyzed_at=now_iso
            )
        except AudioPreprocessingError as p_err:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Audio format error: {p_err.message}"
            )

        # -------------------------------------------------------------
        # 5. Concurrent Dual Inference: Speaker Verification + Anti-Spoof
        # -------------------------------------------------------------
        def run_speaker():
            return self.speaker_svc.verify_voice_sample(
                current_user=current_user,
                target_member_id=target_member_id,
                audio_bytes=audio_bytes,
                filename=filename
            )

        def run_antispoof():
            try:
                inference_out = spoof_detection_service.predict(audio_data)
                decision_res = anti_spoof_decision_engine.evaluate(
                    inference=inference_out,
                    quality=quality_report
                )
                return {
                    "result_category": decision_res.category.value,
                    "spoof_score": decision_res.spoof_score,
                    "confidence_score": decision_res.confidence_score,
                    "score_interpretation": decision_res.score_interpretation,
                    "model_name": decision_res.model_name,
                    "model_version": decision_res.model_version,
                    "replay_detection_supported": decision_res.replay_detection_supported,
                    "replay_limitation_notice": decision_res.replay_limitation_notice,
                    "explanation": decision_res.explanation
                }
            except ModelUnavailableError as mu:
                unavail = anti_spoof_decision_engine.create_model_unavailable_result(
                    quality=quality_report,
                    error_message=mu.message
                )
                return {
                    "result_category": SpoofCategory.MODEL_UNAVAILABLE.value,
                    "spoof_score": None,
                    "confidence_score": None,
                    "score_interpretation": "Anti-spoofing model offline.",
                    "model_name": unavail.model_name,
                    "model_version": unavail.model_version,
                    "replay_detection_supported": False,
                    "replay_limitation_notice": unavail.replay_limitation_notice,
                    "explanation": unavail.explanation,
                    "error": mu.message
                }
            except Exception as e:
                logger.error(f"Anti-spoofing execution error: {e}", exc_info=True)
                return {
                    "result_category": SpoofCategory.MODEL_UNAVAILABLE.value,
                    "spoof_score": None,
                    "confidence_score": None,
                    "score_interpretation": "Error during inference.",
                    "model_name": "Wav2Vec2",
                    "model_version": "v1.0-hf",
                    "replay_detection_supported": False,
                    "replay_limitation_notice": "Not supported.",
                    "explanation": str(e),
                    "error": str(e)
                }

        # Run both pipelines concurrently in worker threads
        speaker_task = asyncio.to_thread(run_speaker)
        spoof_task = asyncio.to_thread(run_antispoof)

        speaker_result, spoof_result = await asyncio.gather(
            speaker_task, spoof_task, return_exceptions=True
        )

        speaker_dict: Optional[Dict[str, Any]] = None
        speaker_err: Optional[str] = None
        if isinstance(speaker_result, Exception):
            logger.error(f"Speaker verification exception: {speaker_result}")
            speaker_err = str(speaker_result)
        else:
            speaker_dict = speaker_result

        spoof_dict: Optional[Dict[str, Any]] = None
        spoof_err: Optional[str] = None
        if isinstance(spoof_result, Exception):
            logger.error(f"Anti-spoofing exception: {spoof_result}")
            spoof_err = str(spoof_result)
        else:
            spoof_dict = spoof_result

        # -------------------------------------------------------------
        # 6. Combined Security Decision Synthesis
        # -------------------------------------------------------------
        engine_out = self.decision_engine.evaluate(
            target_member_name=target_member_name,
            profile_enrolled=True,
            speaker_data=speaker_dict,
            spoof_data=spoof_dict,
            quality_data=acoustic_summary,
            speaker_error=speaker_err,
            spoof_error=spoof_err
        )

        # -------------------------------------------------------------
        # 7. Build DTO Summaries
        # -------------------------------------------------------------
        speaker_summary: Optional[SpeakerVerificationSummary] = None
        if speaker_dict:
            speaker_summary = SpeakerVerificationSummary(
                result=speaker_dict.get("result", "UNKNOWN"),
                similarity_score=speaker_dict.get("similarity_score"),
                match_threshold=speaker_dict.get("match_threshold", 0.72),
                non_match_threshold=speaker_dict.get("non_match_threshold", 0.58),
                decision_status=speaker_dict.get("decision_status", "unknown"),
                explanation=speaker_dict.get("explanation", ""),
                model_name=speaker_dict.get("model_name", "SpeechBrain ECAPA-TDNN"),
                model_version=speaker_dict.get("model_version", "voxceleb-v1.1")
            )

        spoof_summary: Optional[AntiSpoofSummary] = None
        if spoof_dict:
            spoof_summary = AntiSpoofSummary(
                result_category=spoof_dict.get("result_category", "UNKNOWN"),
                spoof_score=spoof_dict.get("spoof_score"),
                confidence_score=spoof_dict.get("confidence_score"),
                score_interpretation=spoof_dict.get("score_interpretation", ""),
                model_name=spoof_dict.get("model_name", "mo-thecreator/Deepfake-audio-detection (Wav2Vec2)"),
                model_version=spoof_dict.get("model_version", "v1.0-hf"),
                replay_detection_supported=spoof_dict.get("replay_detection_supported", False),
                replay_limitation_notice=spoof_dict.get(
                    "replay_limitation_notice",
                    "Acoustic loudspeaker replay detection is not supported."
                )
            )

        return CombinedSecurityResponse(
            analysis_id=analysis_id,
            request_id=request_id,
            target_member_id=target_member_id,
            target_member_name=target_member_name,
            combined_decision=engine_out.outcome,
            decision_headline=engine_out.headline,
            explanation=engine_out.explanation,
            decision_factors=engine_out.decision_factors,
            speaker_verification=speaker_summary,
            anti_spoofing=spoof_summary,
            audio_quality=acoustic_summary,
            limitations=engine_out.limitations,
            is_experimental=True,
            analyzed_at=now_iso
        )


# Global instance
combined_security_service = CombinedSecurityService()

