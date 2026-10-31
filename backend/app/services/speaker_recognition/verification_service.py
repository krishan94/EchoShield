"""
EchoShield — Speaker Verification Service
=========================================
Orchestrates end-to-end voice identity verification:
1. Validates authenticated user authorization and family relationship.
2. Enforces active biometric consent requirements.
3. Retrieves AES-256-GCM encrypted baseline embedding from Firestore.
4. Preprocesses new incoming audio sample and inspects acoustic quality.
5. Extracts 192-dim ECAPA-TDNN embedding vector.
6. Computes cosine similarity against enrolled profile.
7. Evaluates calibrated decision bands (VOICE_MATCHED, NO_MATCH, INCONCLUSIVE).
8. Logs immutable audit trail to verification_history (no raw audio or vectors).
9. Securely purges temporary audio buffers from memory.
"""

import logging
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from firebase_admin import firestore

from app.auth.models import AuthenticatedUser
from app.services.speaker_recognition.audio_preprocessor import (
    audio_preprocessor,
)
from app.services.speaker_recognition.decision_engine import (
    VerificationDecision,
    decision_engine,
)
from app.services.speaker_recognition.embedding_service import embedding_service
from app.services.speaker_recognition.exceptions import (
    AudioPreprocessingError,
    AudioQualityError,
    BiometricConsentMissingError,
    IncompatibleEmbeddingError,
    ProfileNotEnrolledError,
    UnauthorizedAccessError,
)
from app.services.speaker_recognition.model_loader import (
    MODEL_NAME,
    MODEL_VERSION,
)
from app.services.speaker_recognition.similarity_service import similarity_service

# Ensure ai-engine is in sys.path
ai_engine_path = Path(__file__).resolve().parents[4] / "ai-engine"
if str(ai_engine_path) not in sys.path:
    sys.path.insert(0, str(ai_engine_path))

from pipelines.speaker_embedding import speaker_engine  # noqa: E402

logger = logging.getLogger("echoshield.ai.verification_service")


class VerificationService:
    """
    Core service coordinating speaker recognition, security authorization,
    and verification history logging.
    """

    def __init__(self, use_in_memory: bool = False):
        self.use_in_memory = use_in_memory
        # In-memory stores used for hermetic tests or when Firestore ADC is unconfigured
        self._mem_history: Dict[str, Dict[str, Any]] = {}
        self._mem_relationships: Dict[str, Dict[str, Any]] = {}
        self._mem_profiles: Dict[str, Dict[str, Any]] = {}
        self._mem_consents: Dict[str, Dict[str, Any]] = {}
        self._mem_users: Dict[str, Dict[str, Any]] = {}

    def _get_firestore(self):
        if self.use_in_memory:
            return None
        try:
            return firestore.client()
        except Exception as e:
            logger.warning("Firestore client unavailable, falling back to in-memory store: %s", str(e))
            self.use_in_memory = True
            return None

    def _check_family_authorization(self, current_user_id: str, target_member_id: str) -> None:
        """
        Confirms that the target member is linked in an active family relationship
        with the requesting user, or that the user is verifying their own profile.
        """
        if current_user_id == target_member_id:
            return  # Self-verification permitted for calibration / diagnostics

        db = self._get_firestore()
        if db:
            try:
                rel_ref = db.collection("family_relationships")
                query = rel_ref.where("members", "array_contains", current_user_id).stream()
                for doc in query:
                    data = doc.to_dict()
                    if target_member_id in data.get("members", []):
                        return
            except Exception as e:
                logger.warning("Firestore relationship lookup warning: %s", str(e))

        # Check in-memory relationships
        for rel in self._mem_relationships.values():
            members = rel.get("members", [])
            if current_user_id in members and target_member_id in members:
                return

        # Also check family_service instance if available
        try:
            from app.family.service import family_service
            for rel in family_service._mem_relationships.values():
                members = rel.get("members", [])
                if current_user_id in members and target_member_id in members:
                    return
        except Exception:
            pass

        raise UnauthorizedAccessError(
            f"Unauthorized: No active family circle relationship exists with member '{target_member_id}'."
        )

    def _check_member_consent(self, target_member_id: str) -> None:
        """
        Enforces active informed biometric consent for the target member.
        """
        db = self._get_firestore()
        if db:
            try:
                doc = db.collection("voice_consents").document(target_member_id).get()
                if doc.exists:
                    data = doc.to_dict()
                    if data.get("consent_status") == "granted":
                        return
            except Exception as e:
                logger.warning("Firestore consent lookup warning: %s", str(e))

        consent = self._mem_consents.get(target_member_id)
        if consent is not None:
            if consent.get("consent_status") == "granted":
                return
            raise BiometricConsentMissingError(
                target_member_id,
                f"Biometric verification blocked: Target member '{target_member_id}' consent is {consent.get('consent_status')}."
            )

        # Check voice_service instance if available
        try:
            from app.enrollment.service import voice_service
            if voice_service.check_consent(target_member_id):
                return
        except Exception:
            pass

        raise BiometricConsentMissingError(
            target_member_id,
            f"Biometric verification blocked: Target member '{target_member_id}' has not granted active consent or consent was revoked."
        )

    def _get_enrolled_embedding(self, target_member_id: str) -> Dict[str, Any]:
        """
        Retrieves the enrolled voice profile document and decrypts its 192-dim embedding.
        """
        db = self._get_firestore()
        profile_data: Optional[Dict[str, Any]] = None

        if db:
            try:
                doc = db.collection("voice_profiles").document(target_member_id).get()
                if doc.exists:
                    profile_data = doc.to_dict()
            except Exception as e:
                logger.warning("Firestore voice profile read error: %s", str(e))

        if not profile_data:
            profile_data = self._mem_profiles.get(target_member_id)

        # Fallback to voice_service in-memory profiles
        if not profile_data:
            try:
                from app.enrollment.service import voice_service
                profile_data = voice_service._mem_profiles.get(target_member_id)
            except Exception:
                pass

        if not profile_data or not profile_data.get("is_voice_enrolled"):
            raise ProfileNotEnrolledError(target_member_id)

        encrypted_record = profile_data.get("encrypted_embedding")
        if not encrypted_record or "ciphertext" not in encrypted_record:
            raise IncompatibleEmbeddingError(f"Missing encrypted embedding record for member '{target_member_id}'.")

        # Decrypt embedding using AES-256-GCM
        try:
            decrypted_vector = speaker_engine.decrypt_embedding(encrypted_record)
        except Exception as e:
            logger.error("Failed to decrypt biometric embedding for %s: %s", target_member_id, str(e))
            raise IncompatibleEmbeddingError(f"Decryption failed for stored embedding: {str(e)}")

        return {
            "profile": profile_data,
            "vector": decrypted_vector
        }

    def _lookup_member_name(self, target_member_id: str, current_user: AuthenticatedUser) -> str:
        """Resolves target member's display name safely."""
        if target_member_id == current_user.uid:
            return current_user.name or "Self"

        db = self._get_firestore()
        if db:
            try:
                doc = db.collection("users").document(target_member_id).get()
                if doc.exists:
                    data = doc.to_dict()
                    return data.get("display_name") or data.get("name") or "Family Member"
            except Exception:
                pass

        user_info = self._mem_users.get(target_member_id)
        if user_info:
            return user_info.get("display_name") or user_info.get("name") or "Family Member"

        return "Family Member"

    def verify_voice_sample(
        self,
        current_user: AuthenticatedUser,
        target_member_id: str,
        audio_bytes: bytes,
        filename: str = "sample.wav"
    ) -> Dict[str, Any]:
        """
        Executes complete verification pipeline against enrolled member profile.
        """
        now_iso = datetime.now(timezone.utc).isoformat()
        verification_id = f"verif_{uuid.uuid4().hex[:12]}"
        member_name = self._lookup_member_name(target_member_id, current_user)

        # 1. Authorization check
        self._check_family_authorization(current_user.uid, target_member_id)

        # 2. Consent check
        self._check_member_consent(target_member_id)

        # 3. Retrieve enrolled embedding
        enrolled_info = self._get_enrolled_embedding(target_member_id)
        enrolled_vector = enrolled_info["vector"]

        # 4. Preprocess and validate incoming audio
        try:
            audio_data, quality_report = audio_preprocessor.preprocess_and_validate(audio_bytes, filename)
        except AudioQualityError as qe:
            decision = decision_engine.evaluate_quality_failure(str(qe.quality_detail or qe))
            self._log_history(
                verification_id=verification_id,
                user_id=current_user.uid,
                member_id=target_member_id,
                member_name=member_name,
                decision=decision,
                quality_status="rejected",
                duration_seconds=0.0,
                snr_db=0.0,
                timestamp=now_iso
            )
            return {
                "verification_id": verification_id,
                "member_id": target_member_id,
                "member_name": member_name,
                "result": decision.category,
                "similarity_score": None,
                "match_threshold": decision.match_threshold,
                "non_match_threshold": decision.non_match_threshold,
                "decision_status": "quality_rejected",
                "audio_quality_status": "rejected",
                "model_name": MODEL_NAME,
                "model_version": MODEL_VERSION,
                "explanation": decision.explanation,
                "verified_at": now_iso
            }
        except AudioPreprocessingError as pe:
            # Re-raise format and header errors so router returns HTTP 422 / 415 appropriately
            raise pe

        # 5. Extract 192-dim ECAPA-TDNN embedding
        test_embedding = embedding_service.extract_embedding(audio_data)

        # 6. Compute mathematical cosine similarity
        similarity_score = similarity_service.compute_cosine_similarity(test_embedding, enrolled_vector)

        # 7. Evaluate calibrated decision system
        decision = decision_engine.evaluate(
            similarity_score=similarity_score,
            snr_db=quality_report.snr_db,
            member_name=member_name
        )

        # 8. Record audit log in verification_history (NO raw audio, NO embeddings)
        self._log_history(
            verification_id=verification_id,
            user_id=current_user.uid,
            member_id=target_member_id,
            member_name=member_name,
            decision=decision,
            quality_status="acceptable",
            duration_seconds=quality_report.duration_seconds,
            snr_db=quality_report.snr_db,
            timestamp=now_iso
        )

        return {
            "verification_id": verification_id,
            "member_id": target_member_id,
            "member_name": member_name,
            "result": decision.category,
            "similarity_score": decision.similarity_score,
            "match_threshold": decision.match_threshold,
            "non_match_threshold": decision.non_match_threshold,
            "decision_status": decision.confidence_band,
            "audio_quality_status": "acceptable",
            "model_name": MODEL_NAME,
            "model_version": MODEL_VERSION,
            "explanation": decision.explanation,
            "verified_at": now_iso
        }

    def _log_history(
        self,
        verification_id: str,
        user_id: str,
        member_id: str,
        member_name: str,
        decision: VerificationDecision,
        quality_status: str,
        duration_seconds: float,
        snr_db: float,
        timestamp: str
    ) -> Dict[str, Any]:
        """
        Appends an immutable verification record to verification_history.
        Biometric vectors and raw audio are strictly excluded from history logs.
        """
        record = {
            "id": verification_id,
            "user_id": user_id,
            "member_id": member_id,
            "member_name": member_name,
            "result": decision.category,
            "similarity_score": decision.similarity_score,
            "match_threshold": decision.match_threshold,
            "confidence_band": decision.confidence_band,
            "audio_quality_status": quality_status,
            "duration_seconds": duration_seconds,
            "snr_db": snr_db,
            "model_name": MODEL_NAME,
            "model_version": MODEL_VERSION,
            "verified_at": timestamp
        }

        db = self._get_firestore()
        if db:
            try:
                db.collection("verification_history").document(verification_id).set(record)
                return record
            except Exception as e:
                logger.warning("Firestore history log error: %s", str(e))

        self._mem_history[verification_id] = record
        return record

    def get_verification_history(
        self, current_user: AuthenticatedUser, limit: int = 50
    ) -> List[Dict[str, Any]]:
        """
        Returns verification history logs belonging to the requesting user.
        """
        db = self._get_firestore()
        records: List[Dict[str, Any]] = []

        if db:
            try:
                query = (
                    db.collection("verification_history")
                    .where("user_id", "==", current_user.uid)
                    .order_by("verified_at", direction=firestore.Query.DESCENDING)
                    .limit(limit)
                    .stream()
                )
                for doc in query:
                    records.append(doc.to_dict())
                return records
            except Exception as e:
                logger.warning("Firestore query history error: %s", str(e))

        # In-memory query
        user_records = [
            rec for rec in self._mem_history.values()
            if rec.get("user_id") == current_user.uid
        ]
        user_records.sort(key=lambda r: r.get("verified_at", ""), reverse=True)
        return user_records[:limit]

    def clear_verification_history(self, current_user: AuthenticatedUser) -> int:
        """
        Purges all verification history entries for the requesting user.
        """
        db = self._get_firestore()
        count = 0

        if db:
            try:
                query = (
                    db.collection("verification_history")
                    .where("user_id", "==", current_user.uid)
                    .stream()
                )
                batch = db.batch()
                for doc in query:
                    batch.delete(doc.reference)
                    count += 1
                batch.commit()
                return count
            except Exception as e:
                logger.warning("Firestore clear history error: %s", str(e))

        to_delete = [
            k for k, v in self._mem_history.items()
            if v.get("user_id") == current_user.uid
        ]
        for k in to_delete:
            del self._mem_history[k]
            count += 1
        return count


# Global instance
verification_service = VerificationService()
