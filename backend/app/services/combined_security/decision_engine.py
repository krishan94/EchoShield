"""
EchoShield — Combined Security Decision Engine
===============================================
Central decision logic synthesizing Phase 5 speaker recognition and
Phase 6 anti-spoofing outputs into an unambiguous final security posture.

Enforces strict conflict resolution:
- Anti-spoofing SPOOF_SUSPECTED always overrides speaker similarity.
- Never treats high speaker similarity as proof of authenticity.
- Disclaims physical loudspeaker replay detection.
"""

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from app.combined_security.models import (
    AcousticScreeningSummary,
    AntiSpoofSummary,
    CombinedSecurityOutcome,
    CombinedSecurityResponse,
    SpeakerVerificationSummary,
)


@dataclass
class EngineDecisionOutput:
    outcome: CombinedSecurityOutcome
    headline: str
    explanation: str
    decision_factors: List[str]
    limitations: List[str]


class CombinedSecurityDecisionEngine:
    """
    Evaluates dual AI model outputs and determines the unified cybersecurity posture.
    """

    REPLAY_LIMITATION = (
        "Physical acoustic replay limitation: The anti-spoofing detector inspects digital "
        "synthesis and vocoder artifacts (TTS/VC). Loudspeaker playback of a genuine prior "
        "recording is NOT evaluated."
    )
    EXPERIMENTAL_LIMITATION = (
        "Anti-spoofing classification uses experimental heuristic neural models. "
        "'NO_SPOOF_SIGNAL_DETECTED' does not guarantee authenticity."
    )
    HIGH_STAKES_NOTICE = (
        "High-stakes advisory: Mutual out-of-band verification is strongly advised for "
        "emergency alerts, banking transactions, or credentials transfers."
    )

    def evaluate(
        self,
        target_member_name: str,
        profile_enrolled: bool,
        speaker_data: Optional[Dict[str, Any]],
        spoof_data: Optional[Dict[str, Any]],
        quality_data: Optional[AcousticScreeningSummary] = None,
        speaker_error: Optional[str] = None,
        spoof_error: Optional[str] = None
    ) -> EngineDecisionOutput:
        """
        Executes strict precedence evaluation over the model outputs.
        """
        factors: List[str] = []
        limitations: List[str] = [
            self.REPLAY_LIMITATION,
            self.EXPERIMENTAL_LIMITATION,
            self.HIGH_STAKES_NOTICE
        ]

        # -------------------------------------------------------------
        # Precedence Rule 1: Profile Enrollment Check
        # -------------------------------------------------------------
        if not profile_enrolled:
            return EngineDecisionOutput(
                outcome=CombinedSecurityOutcome.PROFILE_NOT_ENROLLED,
                headline="Voice Profile Not Enrolled",
                explanation=(
                    f"Target family member '{target_member_name}' does not have an active enrolled "
                    "biometric voice profile. Verification cannot be performed."
                ),
                decision_factors=[
                    f"No active encrypted biometric baseline found for '{target_member_name}'."
                ],
                limitations=limitations
            )

        # -------------------------------------------------------------
        # Precedence Rule 2: Audio Quality Validation Check
        # -------------------------------------------------------------
        speaker_res = (speaker_data or {}).get("result")
        spoof_cat = (spoof_data or {}).get("result_category")

        # Note: If spoofing is explicitly detected even in noisy audio, SPOOF_SUSPECTED takes priority!
        if spoof_cat != "SPOOF_SUSPECTED":
            if (quality_data and not quality_data.is_acceptable) or \
               speaker_res == "AUDIO_QUALITY_FAILED" or \
               spoof_cat == "AUDIO_QUALITY_FAILED":
                rejection_msg = (
                    (quality_data.rejection_reason if quality_data else None)
                    or (speaker_data or {}).get("explanation")
                    or (spoof_data or {}).get("explanation")
                    or "Audio does not meet minimum biometric acoustic standards (SNR, duration, or clipping)."
                )
                return EngineDecisionOutput(
                    outcome=CombinedSecurityOutcome.AUDIO_QUALITY_FAILED,
                    headline="Audio Quality Insufficient",
                    explanation=(
                        f"The speech sample failed acoustic screening: {rejection_msg}. "
                        "Reliable biometric verification and anti-spoofing analysis require clear audio."
                    ),
                    decision_factors=[
                        f"Acoustic quality rejected: {rejection_msg}",
                        "Duration must be 2.0s - 15.0s with SNR > 10 dB in a quiet environment."
                    ],
                    limitations=limitations
                )

        # -------------------------------------------------------------
        # Precedence Rule 3: Anti-Spoofing Detection (SPOOF_SUSPECTED)
        # -------------------------------------------------------------
        # Anti-spoof detection ALWAYS takes precedence over speaker similarity!
        if spoof_cat == "SPOOF_SUSPECTED":
            spoof_score = (spoof_data or {}).get("spoof_score", 0.0)
            factors.append(
                f"AI anti-spoofing engine detected synthetic speech / vocoder artifacts (spoof score: {spoof_score:.2f})."
            )
            if speaker_res == "VOICE_MATCHED":
                similarity = (speaker_data or {}).get("similarity_score", 0.0)
                factors.append(
                    f"CRITICAL CONFLICT: Speaker recognition reported acoustic similarity ({similarity:.2f}), "
                    "indicating a likely AI voice clone or synthetic mimicry scam."
                )
                explanation = (
                    f"POTENTIAL AI VOICE CLONE DETECTED: While acoustic characteristics resemble "
                    f"'{target_member_name}' (similarity {similarity:.2f}), neural anti-spoofing detected "
                    f"synthetic generation artifacts (spoof probability {spoof_score:.2f}). "
                    "Do NOT trust this audio or comply with high-stakes requests."
                )
            else:
                factors.append(
                    f"Speaker verification status: {speaker_res or 'NOT_EVALUATED'}."
                )
                explanation = (
                    "SUSPICIOUS AUDIO DETECTED: The speech sample exhibits synthetic voice, neural vocoder, "
                    f"or voice conversion artifacts (spoof probability {spoof_score:.2f}). "
                    "The audio is classified as untrusted."
                )

            return EngineDecisionOutput(
                outcome=CombinedSecurityOutcome.SUSPICIOUS_AUDIO,
                headline="Suspicious Audio — Potential Deepfake",
                explanation=explanation,
                decision_factors=factors,
                limitations=limitations
            )

        # -------------------------------------------------------------
        # Precedence Rule 4: Model / Infrastructure Unavailability
        # -------------------------------------------------------------
        if spoof_cat == "MODEL_UNAVAILABLE" or speaker_res == "MODEL_UNAVAILABLE" or \
           spoof_error or speaker_error:
            unavail_reason = spoof_error or speaker_error or "Neural model offline or failed to initialize."
            return EngineDecisionOutput(
                outcome=CombinedSecurityOutcome.ANALYSIS_UNAVAILABLE,
                headline="Security Analysis Unavailable",
                explanation=(
                    "One or more required security models could not be loaded or executed: "
                    f"{unavail_reason}. Analysis cannot be safely completed."
                ),
                decision_factors=[
                    f"System availability error: {unavail_reason}",
                    "Both speaker recognition and deepfake analysis must be available for a definitive verdict."
                ],
                limitations=limitations
            )

        # -------------------------------------------------------------
        # Precedence Rule 5: Dual Affirmation (IDENTITY_CONFIRMED)
        # -------------------------------------------------------------
        if speaker_res == "VOICE_MATCHED" and spoof_cat == "NO_SPOOF_SIGNAL_DETECTED":
            similarity = (speaker_data or {}).get("similarity_score", 0.0)
            threshold = (speaker_data or {}).get("match_threshold", 0.72)
            spoof_score = (spoof_data or {}).get("spoof_score", 0.0)

            factors.append(
                f"Speaker similarity ({similarity:.2f}) meets or exceeds match threshold ({threshold:.2f})."
            )
            factors.append(
                f"Anti-spoofing detector found no synthetic vocoder or voice conversion cues (spoof score: {spoof_score:.2f})."
            )

            return EngineDecisionOutput(
                outcome=CombinedSecurityOutcome.IDENTITY_CONFIRMED,
                headline="Voice Identity Confirmed",
                explanation=(
                    f"Dual security confirmation: The voice sample matches the enrolled biometric "
                    f"profile of '{target_member_name}' (similarity {similarity:.2f} >= {threshold:.2f}), "
                    "and no synthetic deepfake vocoder artifacts were detected."
                ),
                decision_factors=factors,
                limitations=limitations
            )

        # -------------------------------------------------------------
        # Precedence Rule 6: Identity Not Confirmed (NO_MATCH)
        # -------------------------------------------------------------
        if speaker_res == "NO_MATCH" and spoof_cat == "NO_SPOOF_SIGNAL_DETECTED":
            similarity = (speaker_data or {}).get("similarity_score", 0.0)
            non_match_thr = (speaker_data or {}).get("non_match_threshold", 0.58)
            spoof_score = (spoof_data or {}).get("spoof_score", 0.0)

            factors.append(
                f"Speaker similarity ({similarity:.2f}) is below non-match boundary ({non_match_thr:.2f})."
            )
            factors.append(
                f"Anti-spoofing detector found no synthetic cues (spoof score: {spoof_score:.2f})."
            )

            return EngineDecisionOutput(
                outcome=CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED,
                headline="Identity Not Confirmed",
                explanation=(
                    f"The caller's voice distinctly differs from '{target_member_name}' "
                    f"(similarity {similarity:.2f} < {non_match_thr:.2f}). Although no synthetic deepfake "
                    "signatures were observed, the voice does not belong to the enrolled family member."
                ),
                decision_factors=factors,
                limitations=limitations
            )

        # -------------------------------------------------------------
        # Precedence Rule 7: Inconclusive / Indeterminate Evidence
        # -------------------------------------------------------------
        # Handles:
        # - Speaker INCONCLUSIVE + Spoof NO_SPOOF_SIGNAL_DETECTED
        # - Speaker VOICE_MATCHED + Spoof INCONCLUSIVE
        # - Speaker NO_MATCH + Spoof INCONCLUSIVE
        # - Both INCONCLUSIVE
        similarity_val = (speaker_data or {}).get("similarity_score")
        sim_str = f"{similarity_val:.2f}" if similarity_val is not None else "N/A"

        factors.append(f"Speaker recognition status: {speaker_res or 'UNKNOWN'} (similarity: {sim_str}).")
        factors.append(f"Anti-spoofing status: {spoof_cat or 'UNKNOWN'}.")

        reasons = []
        if speaker_res == "INCONCLUSIVE":
            reasons.append("Speaker similarity falls within the calibrated margin of acoustic uncertainty.")
        if spoof_cat == "INCONCLUSIVE":
            reasons.append("Anti-spoofing engine returned inconclusive confidence scores.")

        explanation = (
            "SECURITY EVALUATION INCONCLUSIVE: " +
            (" ".join(reasons) if reasons else "One or both security systems yielded insufficient statistical evidence.") +
            " Cannot confirm identity or rule out impersonation. Verification should be re-attempted with a longer, clearer audio sample."
        )

        return EngineDecisionOutput(
            outcome=CombinedSecurityOutcome.INCONCLUSIVE,
            headline="Security Evaluation Inconclusive",
            explanation=explanation,
            decision_factors=factors,
            limitations=limitations
        )


# Global instance
combined_decision_engine = CombinedSecurityDecisionEngine()

