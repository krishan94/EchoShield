import logging
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import HTTPException, status

from app.core.firebase import get_firestore_client
from app.auth.models import AuthenticatedUser
from app.enrollment.models import (
    ConsentResponse,
    EnrollmentResponse,
    SampleQualitySummary,
    VoiceConsentRequest,
    VoiceProfileDeleteResponse,
    VoiceProfileResponse,
)

# Ensure ai-engine is in sys.path
ai_engine_path = Path(__file__).resolve().parents[3] / "ai-engine"
if str(ai_engine_path) not in sys.path:
    sys.path.insert(0, str(ai_engine_path))

from pipelines.speaker_embedding import EMBEDDING_DIM, MODEL_NAME, MODEL_VERSION, speaker_engine  # noqa: E402

logger = logging.getLogger("echoshield.enrollment")


class VoiceEnrollmentService:
    """
    Business logic and Firestore persistence for EchoShield Voice Enrollment.
    Enforces explicit user consent, real audio quality checks, genuine ECAPA-TDNN
    embedding generation, and AES-256-GCM biometric encryption at rest.
    When in production mode (use_in_memory=False), strictly interacts with Firestore
    and rejects silent fallback to in-memory dictionaries.
    """

    def __init__(self, use_in_memory: bool = False):
        self.use_in_memory = use_in_memory
        # In-memory stores used strictly for hermetic unit tests
        self._mem_consents: Dict[str, Dict[str, Any]] = {}
        self._mem_profiles: Dict[str, Dict[str, Any]] = {}
        self._mem_users: Dict[str, Dict[str, Any]] = {}

    def _get_firestore(self):
        """
        Retrieves Firestore client in production.
        In test mode (use_in_memory=True), returns None.
        In production mode, raises HTTP 503 if Firestore cannot be reached.
        """
        if self.use_in_memory:
            return None
        return get_firestore_client()

    def record_consent(self, user: AuthenticatedUser, request: VoiceConsentRequest) -> ConsentResponse:
        """
        Stores user's informed biometric consent.
        Consent is mandatory prior to capturing or processing voice samples.
        """
        if request.consent_status.lower() not in ("granted", "revoked"):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid consent status. Must be 'granted' or 'revoked'."
            )

        now_iso = datetime.now(timezone.utc).isoformat()
        consent_record = {
            "user_id": user.uid,
            "consent_status": request.consent_status.lower(),
            "consent_version": request.consent_version,
            "consent_timestamp": request.consent_timestamp or now_iso,
            "updated_at": now_iso
        }

        if self.use_in_memory:
            self._mem_consents[user.uid] = consent_record
            user_meta = self._mem_users.setdefault(user.uid, {})
            user_meta["voice_consent_granted"] = (request.consent_status.lower() == "granted")
            user_meta["voice_consent_timestamp"] = consent_record["consent_timestamp"]
            return ConsentResponse(
                user_id=user.uid,
                consent_status=consent_record["consent_status"],
                consent_version=consent_record["consent_version"],
                consent_timestamp=consent_record["consent_timestamp"],
                message="Biometric consent recorded successfully."
            )

        # Production path: Firestore
        db = self._get_firestore()
        try:
            db.collection("voice_consents").document(user.uid).set(consent_record)
            db.collection("users").document(user.uid).set({
                "voice_consent_granted": (request.consent_status.lower() == "granted"),
                "voice_consent_timestamp": consent_record["consent_timestamp"]
            }, merge=True)
            return ConsentResponse(
                user_id=user.uid,
                consent_status=consent_record["consent_status"],
                consent_version=consent_record["consent_version"],
                consent_timestamp=consent_record["consent_timestamp"],
                message="Biometric consent recorded successfully."
            )
        except Exception as e:
            logger.error("Firestore consent write error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to record consent in Firestore: {str(e)}"
            )

    def check_consent(self, user_id: str) -> bool:
        """
        Verifies whether the user has an active informed biometric consent record.
        """
        if self.use_in_memory:
            consent = self._mem_consents.get(user_id)
            return bool(consent and consent.get("consent_status") == "granted")

        # Production path: Firestore
        db = self._get_firestore()
        try:
            doc = db.collection("voice_consents").document(user_id).get()
            if doc.exists:
                data = doc.to_dict()
                return data.get("consent_status") == "granted"
            return False
        except Exception as e:
            logger.error("Firestore consent check error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to query consent record from Firestore: {str(e)}"
            )

    def enroll_voice(
        self,
        user: AuthenticatedUser,
        samples_bytes: List[bytes]
    ) -> EnrollmentResponse:
        """
        Processes 3 distinct 16kHz PCM WAV samples:
        1. Verifies biometric consent.
        2. Validates audio duration, silence ratio, RMS energy, and clipping.
        3. Extracts genuine 192-dim ECAPA-TDNN embeddings.
        4. Calculates normalized centroid and acoustic quality score.
        5. Encrypts embedding with AES-256-GCM.
        6. Persists encrypted biometric profile in Firestore voice_profiles.
        7. Updates user's verification readiness (is_voice_enrolled = True).
        """
        # 1. Enforce informed consent
        if not self.check_consent(user.uid):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Informed biometric consent must be granted before enrolling voice signatures."
            )

        # 2. Check sample count
        if len(samples_bytes) < 3:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"At least 3 distinct audio recordings are required for enrollment, got {len(samples_bytes)}."
            )

        # 3. Audio Quality & Embedding Extraction via ECAPA-TDNN
        try:
            centroid_embedding, quality_score, reports = speaker_engine.process_enrollment_samples(samples_bytes)
        except ValueError as e:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=str(e)
            )
        except Exception as e:
            logger.error("AI embedding generation failure: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Voice embedding generation failed: {str(e)}"
            )

        # 4. AES-256-GCM Biometric Encryption
        encrypted_record = speaker_engine.encrypt_embedding(centroid_embedding)

        now_iso = datetime.now(timezone.utc).isoformat()
        samples_summary = [
            SampleQualitySummary(
                sample_index=idx + 1,
                duration_seconds=round(rep.duration_seconds, 2),
                rms_energy=round(rep.rms_energy, 4),
                snr_db=round(rep.snr_db, 1),
                status="acceptable"
            )
            for idx, rep in enumerate(reports)
        ]

        profile_data = {
            "user_id": user.uid,
            "enrollment_status": "enrolled",
            "is_voice_enrolled": True,
            "voice_quality_score": quality_score,
            "sample_count": len(samples_bytes),
            "embedding_model": MODEL_NAME,
            "model_version": MODEL_VERSION,
            "embedding_dim": EMBEDDING_DIM,
            "encrypted_embedding": encrypted_record,
            "enrolled_at": now_iso,
            "updated_at": now_iso
        }

        # 5. Persist profile
        if self.use_in_memory:
            self._mem_profiles[user.uid] = profile_data
            user_meta = self._mem_users.setdefault(user.uid, {})
            user_meta["is_voice_enrolled"] = True
            user_meta["voice_quality_score"] = quality_score
            return EnrollmentResponse(
                user_id=user.uid,
                enrollment_status="enrolled",
                voice_quality_score=quality_score,
                sample_count=len(samples_bytes),
                embedding_model=MODEL_NAME,
                model_version=MODEL_VERSION,
                enrolled_at=now_iso,
                samples_summary=samples_summary,
                message="Biometric voice signature successfully extracted and encrypted."
            )

        # Production path: Firestore
        db = self._get_firestore()
        try:
            db.collection("voice_profiles").document(user.uid).set(profile_data)
            db.collection("users").document(user.uid).set({
                "is_voice_enrolled": True,
                "voice_quality_score": quality_score,
                "voice_enrolled_at": now_iso
            }, merge=True)

            return EnrollmentResponse(
                user_id=user.uid,
                enrollment_status="enrolled",
                voice_quality_score=quality_score,
                sample_count=len(samples_bytes),
                embedding_model=MODEL_NAME,
                model_version=MODEL_VERSION,
                enrolled_at=now_iso,
                samples_summary=samples_summary,
                message="Biometric voice signature successfully extracted and encrypted."
            )
        except Exception as e:
            logger.error("Firestore profile write error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to persist voice profile in Firestore: {str(e)}"
            )

    def get_voice_profile(self, user: AuthenticatedUser) -> VoiceProfileResponse:
        """
        Retrieves voice profile status and quality metadata.
        Never exposes raw embeddings or cryptographic keys.
        """
        now_iso = datetime.now(timezone.utc).isoformat()
        consent_status = "granted" if self.check_consent(user.uid) else "pending"

        if self.use_in_memory:
            profile_data = self._mem_profiles.get(user.uid)
            if not profile_data:
                return VoiceProfileResponse(
                    user_id=user.uid,
                    enrollment_status="not_enrolled",
                    is_voice_enrolled=False,
                    voice_quality_score=0.0,
                    sample_count=0,
                    embedding_model=MODEL_NAME,
                    model_version=MODEL_VERSION,
                    consent_status=consent_status,
                    consent_version="1.0",
                    enrolled_at=None,
                    updated_at=now_iso
                )
            return VoiceProfileResponse(
                user_id=user.uid,
                enrollment_status=profile_data.get("enrollment_status", "enrolled"),
                is_voice_enrolled=profile_data.get("is_voice_enrolled", True),
                voice_quality_score=float(profile_data.get("voice_quality_score", 0.0)),
                sample_count=int(profile_data.get("sample_count", 3)),
                embedding_model=profile_data.get("embedding_model", MODEL_NAME),
                model_version=profile_data.get("model_version", MODEL_VERSION),
                consent_status=consent_status,
                consent_version="1.0",
                enrolled_at=profile_data.get("enrolled_at"),
                updated_at=profile_data.get("updated_at", now_iso)
            )

        # Production path: Firestore
        db = self._get_firestore()
        try:
            doc = db.collection("voice_profiles").document(user.uid).get()
            if not doc.exists:
                return VoiceProfileResponse(
                    user_id=user.uid,
                    enrollment_status="not_enrolled",
                    is_voice_enrolled=False,
                    voice_quality_score=0.0,
                    sample_count=0,
                    embedding_model=MODEL_NAME,
                    model_version=MODEL_VERSION,
                    consent_status=consent_status,
                    consent_version="1.0",
                    enrolled_at=None,
                    updated_at=now_iso
                )
            profile_data = doc.to_dict()
            return VoiceProfileResponse(
                user_id=user.uid,
                enrollment_status=profile_data.get("enrollment_status", "enrolled"),
                is_voice_enrolled=profile_data.get("is_voice_enrolled", True),
                voice_quality_score=float(profile_data.get("voice_quality_score", 0.0)),
                sample_count=int(profile_data.get("sample_count", 3)),
                embedding_model=profile_data.get("embedding_model", MODEL_NAME),
                model_version=profile_data.get("model_version", MODEL_VERSION),
                consent_status=consent_status,
                consent_version="1.0",
                enrolled_at=profile_data.get("enrolled_at"),
                updated_at=profile_data.get("updated_at", now_iso)
            )
        except Exception as e:
            logger.error("Firestore get profile error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to query voice profile from Firestore: {str(e)}"
            )

    def delete_voice_profile(self, user: AuthenticatedUser) -> VoiceProfileDeleteResponse:
        """
        Cryptographically purges the voice profile, deletes stored encrypted embeddings,
        revokes biometric consent, and updates user account enrollment state to false.
        """
        now_iso = datetime.now(timezone.utc).isoformat()

        if self.use_in_memory:
            self._mem_profiles.pop(user.uid, None)
            if user.uid in self._mem_consents:
                self._mem_consents[user.uid]["consent_status"] = "revoked"
            if user.uid in self._mem_users:
                self._mem_users[user.uid]["is_voice_enrolled"] = False
                self._mem_users[user.uid]["voice_quality_score"] = 0.0

            return VoiceProfileDeleteResponse(
                user_id=user.uid,
                status="deleted",
                message="Biometric voice profile and encrypted embeddings purged successfully."
            )

        # Production path: Firestore
        db = self._get_firestore()
        try:
            db.collection("voice_profiles").document(user.uid).delete()
            db.collection("voice_consents").document(user.uid).update({
                "consent_status": "revoked",
                "updated_at": now_iso
            })
            db.collection("users").document(user.uid).set({
                "is_voice_enrolled": False,
                "voice_quality_score": 0.0,
                "voice_enrolled_at": None
            }, merge=True)
            return VoiceProfileDeleteResponse(
                user_id=user.uid,
                status="deleted",
                message="Biometric voice profile and encrypted embeddings purged successfully."
            )
        except Exception as e:
            logger.error("Firestore delete profile error: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to purge voice profile in Firestore: {str(e)}"
            )


# Global service instance
voice_service = VoiceEnrollmentService()
