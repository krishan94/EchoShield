"""
EchoShield — Anti-Spoofing & Deepfake Detection Service Package
"""

from app.services.anti_spoofing.exceptions import (
    AntiSpoofError,
    AudioPreprocessingError,
    AudioQualityError,
    ModelUnavailableError,
    SpoofInferenceError,
)
from app.services.anti_spoofing.schemas import (
    AntiSpoofResult,
    AudioQualityReport,
    ModelInferenceOutput,
    SpoofCategory,
)
from app.services.anti_spoofing.model_loader import (
    MODEL_NAME,
    MODEL_VERSION,
    AntiSpoofModelLoader,
    anti_spoof_model_loader,
)
from app.services.anti_spoofing.audio_preprocessor import (
    AntiSpoofAudioPreprocessor,
    anti_spoof_preprocessor,
)
from app.services.anti_spoofing.spoof_detection_service import (
    SpoofDetectionService,
    spoof_detection_service,
)
from app.services.anti_spoofing.decision_engine import (
    AntiSpoofDecisionEngine,
    anti_spoof_decision_engine,
)

__all__ = [
    "AntiSpoofError",
    "AudioPreprocessingError",
    "AudioQualityError",
    "ModelUnavailableError",
    "SpoofInferenceError",
    "SpoofCategory",
    "AudioQualityReport",
    "ModelInferenceOutput",
    "AntiSpoofResult",
    "MODEL_NAME",
    "MODEL_VERSION",
    "AntiSpoofModelLoader",
    "anti_spoof_model_loader",
    "AntiSpoofAudioPreprocessor",
    "anti_spoof_preprocessor",
    "SpoofDetectionService",
    "spoof_detection_service",
    "AntiSpoofDecisionEngine",
    "anti_spoof_decision_engine",
]

