"""
EchoShield — Calibrated Speaker Verification Decision Engine
============================================================
Translates raw cosine similarity scores between SpeechBrain ECAPA-TDNN embeddings
into actionable biometric verification decisions:
- VOICE_MATCHED
- NO_MATCH
- INCONCLUSIVE
- AUDIO_QUALITY_FAILED
- PROFILE_NOT_ENROLLED

Calibrated Decision Framework & Operating Bands:
- Match Threshold (T_match): 0.72
  Targeted operating point derived from ECAPA-TDNN VoxCeleb cosine similarity distributions.
  Minimizes False Acceptance Rate (FAR) while tolerating realistic mobile microphone variations.
- Non-Match Threshold (T_non_match): 0.58
  Cosine scores below 0.58 indicate acoustic divergence consistent with distinct speakers.
- Uncertain / Inconclusive Region: [0.58, 0.72)
  Ambiguous acoustic separation. Biometric identity cannot be reliably asserted or refuted;
  designated INCONCLUSIVE without guessing or simulating probabilities.

Acoustic Quality Safeguard:
  When estimated SNR is below 10.0 dB, ambient room noise distorts TDNN feature maps.
  Scores falling into borderline matching territory with low SNR are conservatively
  flagged as INCONCLUSIVE.

Note on Limitations & Experimental Label:
  This system is labeled EXPERIMENTAL. The raw score is a model vector similarity measurement,
  NOT a percentage probability of caller authenticity or mathematical proof of identity.
"""

import logging
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger("echoshield.ai.decision")

# Operating thresholds for SpeechBrain ECAPA-TDNN (voxceleb-v1.1)
DEFAULT_MATCH_THRESHOLD = 0.72
DEFAULT_NON_MATCH_THRESHOLD = 0.58
MIN_ACCEPTABLE_SNR_DB = 10.0

DECISION_VOICE_MATCHED = "VOICE_MATCHED"
DECISION_NO_MATCH = "NO_MATCH"
DECISION_INCONCLUSIVE = "INCONCLUSIVE"
DECISION_AUDIO_QUALITY_FAILED = "AUDIO_QUALITY_FAILED"
DECISION_PROFILE_NOT_ENROLLED = "PROFILE_NOT_ENROLLED"


@dataclass
class VerificationDecision:
    category: str
    similarity_score: Optional[float]
    match_threshold: float
    non_match_threshold: float
    confidence_band: str  # "high_match", "non_match", "uncertain_band", "noise_degraded", "quality_failed", "unenrolled"
    is_experimental: bool
    explanation: str


class DecisionEngine:
    """
    Evaluates acoustic quality and cosine similarity against calibrated decision boundaries.
    """

    def __init__(
        self,
        match_threshold: float = DEFAULT_MATCH_THRESHOLD,
        non_match_threshold: float = DEFAULT_NON_MATCH_THRESHOLD
    ):
        self.match_threshold = match_threshold
        self.non_match_threshold = non_match_threshold

    def evaluate(
        self,
        similarity_score: float,
        snr_db: float = 20.0,
        member_name: Optional[str] = None
    ) -> VerificationDecision:
        """
        Evaluates a raw cosine similarity score and acoustic SNR.
        Returns a calibrated VerificationDecision.
        """
        target_name = member_name or "enrolled family member"

        # 1. Acoustic noise degradation check
        if snr_db < MIN_ACCEPTABLE_SNR_DB and similarity_score < (self.match_threshold + 0.05):
            return VerificationDecision(
                category=DECISION_INCONCLUSIVE,
                similarity_score=similarity_score,
                match_threshold=self.match_threshold,
                non_match_threshold=self.non_match_threshold,
                confidence_band="noise_degraded",
                is_experimental=True,
                explanation=(
                    f"Acoustic signal has elevated background noise (SNR {snr_db:.1f} dB). "
                    f"Similarity score is {similarity_score:.4f}, but high noise prohibits a definitive match decision."
                )
            )

        # 2. Strong match: S >= match_threshold
        if similarity_score >= self.match_threshold:
            return VerificationDecision(
                category=DECISION_VOICE_MATCHED,
                similarity_score=similarity_score,
                match_threshold=self.match_threshold,
                non_match_threshold=self.non_match_threshold,
                confidence_band="high_match",
                is_experimental=True,
                explanation=(
                    f"The voice sample demonstrates significant acoustic similarity ({similarity_score:.4f} >= {self.match_threshold:.2f}) "
                    f"to {target_name}'s enrolled voice profile under the experimental decision criteria. "
                    "Note: High similarity indicates biometric proximity, not guaranteed identity."
                )
            )

        # 3. Clear non-match: S < non_match_threshold
        if similarity_score < self.non_match_threshold:
            return VerificationDecision(
                category=DECISION_NO_MATCH,
                similarity_score=similarity_score,
                match_threshold=self.match_threshold,
                non_match_threshold=self.non_match_threshold,
                confidence_band="non_match",
                is_experimental=True,
                explanation=(
                    f"The voice sample did not meet the matching criteria ({similarity_score:.4f} < {self.non_match_threshold:.2f}) "
                    f"when compared to {target_name}'s enrolled profile. "
                    "Acoustic features diverge from the registered voice signature."
                )
            )

        # 4. Inconclusive band: non_match_threshold <= S < match_threshold
        return VerificationDecision(
            category=DECISION_INCONCLUSIVE,
            similarity_score=similarity_score,
            match_threshold=self.match_threshold,
            non_match_threshold=self.non_match_threshold,
            confidence_band="uncertain_band",
            is_experimental=True,
            explanation=(
                f"The similarity score ({similarity_score:.4f}) falls within the uncertain boundary "
                f"[{self.non_match_threshold:.2f}, {self.match_threshold:.2f}). "
                "The biometric evidence is inconclusive. Please record another sample under quieter conditions."
            )
        )

    def evaluate_quality_failure(self, error_detail: str) -> VerificationDecision:
        """Helper for audio quality rejection."""
        return VerificationDecision(
            category=DECISION_AUDIO_QUALITY_FAILED,
            similarity_score=None,
            match_threshold=self.match_threshold,
            non_match_threshold=self.non_match_threshold,
            confidence_band="quality_failed",
            is_experimental=True,
            explanation=f"Audio quality criteria not satisfied: {error_detail}"
        )

    def evaluate_profile_not_enrolled(self, member_name: Optional[str] = None) -> VerificationDecision:
        """Helper for missing or unenrolled member profiles."""
        name = member_name or "Selected family member"
        return VerificationDecision(
            category=DECISION_PROFILE_NOT_ENROLLED,
            similarity_score=None,
            match_threshold=self.match_threshold,
            non_match_threshold=self.non_match_threshold,
            confidence_band="unenrolled",
            is_experimental=True,
            explanation=f"{name} has not completed voice enrollment. No biometric reference signature exists."
        )


# Global instance
decision_engine = DecisionEngine()
