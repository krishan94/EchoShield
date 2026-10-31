"""
EchoShield — Speaker Recognition & Voice Identity Matching Package
===================================================================
Exports modular services for audio preprocessing, model loading,
embedding extraction, cosine similarity calculation, and calibrated
verification decision logic.
"""

from app.services.speaker_recognition.audio_preprocessor import (
    AudioQualityReport,
    audio_preprocessor,
)
from app.services.speaker_recognition.decision_engine import (
    DECISION_AUDIO_QUALITY_FAILED,
    DECISION_INCONCLUSIVE,
    DECISION_NO_MATCH,
    DECISION_PROFILE_NOT_ENROLLED,
    DECISION_VOICE_MATCHED,
    DEFAULT_MATCH_THRESHOLD,
    DEFAULT_NON_MATCH_THRESHOLD,
    VerificationDecision,
    decision_engine,
)
from app.services.speaker_recognition.embedding_service import (
    embedding_service,
)
from app.services.speaker_recognition.exceptions import (
    AudioPreprocessingError,
    AudioQualityError,
    BiometricConsentMissingError,
    IncompatibleEmbeddingError,
    ModelLoadError,
    ProfileNotEnrolledError,
    SpeakerRecognitionError,
    UnauthorizedAccessError,
)
from app.services.speaker_recognition.model_loader import (
    EXPECTED_EMBEDDING_DIM,
    MODEL_LICENSE,
    MODEL_NAME,
    MODEL_VERSION,
    model_loader,
)
from app.services.speaker_recognition.similarity_service import (
    similarity_service,
)
from app.services.speaker_recognition.verification_service import (
    verification_service,
)

__all__ = [
    "audio_preprocessor",
    "AudioQualityReport",
    "decision_engine",
    "VerificationDecision",
    "DECISION_VOICE_MATCHED",
    "DECISION_NO_MATCH",
    "DECISION_INCONCLUSIVE",
    "DECISION_AUDIO_QUALITY_FAILED",
    "DECISION_PROFILE_NOT_ENROLLED",
    "DEFAULT_MATCH_THRESHOLD",
    "DEFAULT_NON_MATCH_THRESHOLD",
    "embedding_service",
    "model_loader",
    "EXPECTED_EMBEDDING_DIM",
    "MODEL_NAME",
    "MODEL_VERSION",
    "MODEL_LICENSE",
    "similarity_service",
    "verification_service",
    "SpeakerRecognitionError",
    "ModelLoadError",
    "AudioPreprocessingError",
    "AudioQualityError",
    "ProfileNotEnrolledError",
    "BiometricConsentMissingError",
    "UnauthorizedAccessError",
    "IncompatibleEmbeddingError",
]
