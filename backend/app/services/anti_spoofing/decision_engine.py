"""
EchoShield — Anti-Spoofing Calibrated Decision Engine
=====================================================
Translates raw Wav2Vec2 deepfake logits and probabilities into
calibrated, non-misleading forensic verdicts:
- SPOOF_SUSPECTED
- NO_SPOOF_SIGNAL_DETECTED
- INCONCLUSIVE
- AUDIO_QUALITY_FAILED
- MODEL_UNAVAILABLE

Calibrated Operating Boundaries:
- Suspicion Threshold (T_spoof): >= 0.65
  Indicates statistical alignment with neural vocoder / TTS / VC artifacts.
- Low Suspicion Threshold (T_clean): < 0.35
  Indicates an absence of detectable synthetic vocoder signatures.
  CRITICAL: This does NOT prove the audio is genuine or authentic.
- Ambiguous Region: [0.35, 0.65)
  Acoustic evidence is insufficient or contradictory; strictly classified as INCONCLUSIVE.
- Acoustic Noise Safeguard:
  If estimated SNR is below 7.0 dB and score is borderline, designated INCONCLUSIVE.

Replay Detection Limitation:
  Replay attacks (playing back recorded human speech via a speaker) involve physical acoustic
  propagation rather than digital vocoder artifacts. This engine explicitly disclaims replay detection.
"""

from datetime import datetime, timezone
import logging
from typing import Optional

from app.services.anti_spoofing.schemas import (
    AntiSpoofResult,
    AudioQualityReport,
    ModelInferenceOutput,
    SpoofCategory,
)

logger = logging.getLogger("echoshield.antispoof.decision")

# Operating thresholds
THRESHOLD_SPOOF_SUSPECTED = 0.65
THRESHOLD_NO_SPOOF = 0.35
MIN_RELIABLE_SNR_DB = 7.0


class AntiSpoofDecisionEngine:
    """
    Evaluates model outputs and acoustic quality to formulate an evidence-based verdict.
    """

    def evaluate(
        self,
        inference: ModelInferenceOutput,
        quality: AudioQualityReport
    ) -> AntiSpoofResult:
        """
        Synthesizes model inference and acoustic metrics into a calibrated AntiSpoofResult.
        """
        spoof_prob = inference.fake_probability
        bonafide_prob = inference.real_probability
        timestamp_str = datetime.now(timezone.utc).isoformat()

        raw_scores = {
            "fake_logit": inference.fake_logit,
            "real_logit": inference.real_logit,
            "fake_probability": spoof_prob,
            "real_probability": bonafide_prob,
        }

        # 1. Noise-degraded safeguard: if SNR is very low and score is not overwhelmingly spoof
        if quality.snr_db < MIN_RELIABLE_SNR_DB and spoof_prob < 0.85:
            return AntiSpoofResult(
                category=SpoofCategory.INCONCLUSIVE,
                spoof_score=spoof_prob,
                confidence_score=round(abs(spoof_prob - 0.5) * 2, 4),
                raw_scores=raw_scores,
                score_interpretation="Noise-degraded acoustic conditions prevent reliable analysis.",
                model_name=inference.model_name,
                model_version=inference.model_version,
                audio_quality=quality,
                timestamp=timestamp_str,
                explanation=(
                    f"The audio SNR ({quality.snr_db:.1f} dB) is below the reliable analysis floor "
                    f"({MIN_RELIABLE_SNR_DB:.1f} dB). Ambient background noise obscures spectral phase "
                    "relationships, making deepfake classification inconclusive."
                ),
                is_experimental=True,
                replay_detection_supported=False
            )

        # 2. Strong spoof suspicion
        if spoof_prob >= THRESHOLD_SPOOF_SUSPECTED:
            category = SpoofCategory.SPOOF_SUSPECTED
            confidence = round(spoof_prob, 4)
            interpretation = (
                f"Synthetic vocoder or voice conversion characteristics detected "
                f"(probability: {spoof_prob:.2f} >= threshold {THRESHOLD_SPOOF_SUSPECTED:.2f})."
            )
            explanation = (
                "Acoustic features exhibit patterns commonly associated with AI speech synthesis, "
                "neural vocoders, or voice conversion algorithms. Caution is strongly advised."
            )

        # 3. Low spoof suspicion
        elif spoof_prob < THRESHOLD_NO_SPOOF:
            category = SpoofCategory.NO_SPOOF_SIGNAL_DETECTED
            confidence = round(bonafide_prob, 4)
            interpretation = (
                f"No synthetic vocoder signatures detected within current sensitivity bounds "
                f"(spoof probability: {spoof_prob:.2f} < {THRESHOLD_NO_SPOOF:.2f})."
            )
            explanation = (
                "The sample does not exhibit detectable synthetic speech artifacts under the model's "
                "evaluation criteria. IMPORTANT: This does not prove the recording is authentic or genuine. "
                "It only indicates an absence of digital synthesis cues."
            )

        # 4. Inconclusive band
        else:
            category = SpoofCategory.INCONCLUSIVE
            confidence = round(1.0 - (2.0 * abs(spoof_prob - 0.5)), 4)
            interpretation = (
                f"Score ({spoof_prob:.2f}) falls in the ambiguous intermediate zone "
                f"[{THRESHOLD_NO_SPOOF:.2f}, {THRESHOLD_SPOOF_SUSPECTED:.2f})."
            )
            explanation = (
                "Acoustic features do not provide decisive evidence for either synthetic or human speech. "
                "Audio may contain compression artifacts, reverberation, or unfamiliar vocal acoustics."
            )

        return AntiSpoofResult(
            category=category,
            spoof_score=spoof_prob,
            confidence_score=confidence,
            raw_scores=raw_scores,
            score_interpretation=interpretation,
            model_name=inference.model_name,
            model_version=inference.model_version,
            audio_quality=quality,
            timestamp=timestamp_str,
            explanation=explanation,
            is_experimental=True,
            replay_detection_supported=False
        )

    def create_quality_failed_result(
        self,
        quality: AudioQualityReport,
        model_name: str = "mo-thecreator/Deepfake-audio-detection",
        model_version: str = "v1.0-hf"
    ) -> AntiSpoofResult:
        """Constructs an AntiSpoofResult when audio quality screening fails."""
        timestamp_str = datetime.now(timezone.utc).isoformat()
        return AntiSpoofResult(
            category=SpoofCategory.AUDIO_QUALITY_FAILED,
            spoof_score=None,
            confidence_score=None,
            raw_scores={},
            score_interpretation="Audio failed acoustic quality requirements.",
            model_name=model_name,
            model_version=model_version,
            audio_quality=quality,
            timestamp=timestamp_str,
            explanation=quality.error_message or "Audio sample does not meet duration, energy, or format standards.",
            is_experimental=True,
            replay_detection_supported=False
        )

    def create_model_unavailable_result(
        self,
        quality: Optional[AudioQualityReport] = None,
        error_message: Optional[str] = None
    ) -> AntiSpoofResult:
        """Constructs an AntiSpoofResult when model cannot be loaded or inference fails."""
        timestamp_str = datetime.now(timezone.utc).isoformat()
        dummy_quality = quality or AudioQualityReport(
            is_valid=False,
            duration_seconds=0.0,
            sample_rate=16000,
            channels=1,
            rms_energy=0.0,
            silence_ratio=0.0,
            clipping_ratio=0.0,
            snr_db=0.0,
            status_label="UNAVAILABLE"
        )
        return AntiSpoofResult(
            category=SpoofCategory.MODEL_UNAVAILABLE,
            spoof_score=None,
            confidence_score=None,
            raw_scores={},
            score_interpretation="Deepfake detection model unavailable.",
            model_name="mo-thecreator/Deepfake-audio-detection",
            model_version="unavailable",
            audio_quality=dummy_quality,
            timestamp=timestamp_str,
            explanation=error_message or "Pretrained anti-spoofing model is unavailable or failed to initialize.",
            is_experimental=True,
            replay_detection_supported=False
        )


# Global decision engine instance
anti_spoof_decision_engine = AntiSpoofDecisionEngine()
