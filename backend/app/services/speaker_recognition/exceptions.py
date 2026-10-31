"""
EchoShield — Speaker Recognition Engine Exceptions
===================================================
Domain exceptions for audio preprocessing, embedding extraction,
similarity comparison, and authorization in voice identity verification.
"""

from typing import Optional


class SpeakerRecognitionError(Exception):
    """Base exception for all speaker recognition errors."""

    def __init__(self, message: str, code: str = "SPEAKER_RECOGNITION_ERROR"):
        super().__init__(message)
        self.message = message
        self.code = code


class ModelLoadError(SpeakerRecognitionError):
    """Raised when the pretrained speaker recognition model fails to initialize."""

    def __init__(self, message: str = "Failed to load speaker recognition model"):
        super().__init__(message, code="MODEL_LOAD_ERROR")


class AudioPreprocessingError(SpeakerRecognitionError):
    """Raised when audio decoding fails or the format is invalid/corrupt."""

    def __init__(self, message: str = "Invalid or corrupted audio data"):
        super().__init__(message, code="AUDIO_PREPROCESSING_ERROR")


class AudioQualityError(SpeakerRecognitionError):
    """Raised when audio fails quality criteria (duration, silence, clipping, SNR)."""

    def __init__(self, message: str, quality_detail: Optional[str] = None):
        super().__init__(message, code="AUDIO_QUALITY_FAILED")
        self.quality_detail = quality_detail or message


class ProfileNotEnrolledError(SpeakerRecognitionError):
    """Raised when the requested family member has no enrolled biometric profile."""

    def __init__(self, member_id: str, message: Optional[str] = None):
        msg = message or f"Family member '{member_id}' does not have an active enrolled voice profile."
        super().__init__(msg, code="PROFILE_NOT_ENROLLED")
        self.member_id = member_id


class BiometricConsentMissingError(SpeakerRecognitionError):
    """Raised when biometric consent is not granted or has been revoked."""

    def __init__(self, member_id: str, message: Optional[str] = None):
        msg = message or f"Active biometric consent missing or revoked for member '{member_id}'."
        super().__init__(msg, code="CONSENT_MISSING")
        self.member_id = member_id


class UnauthorizedAccessError(SpeakerRecognitionError):
    """Raised when a user attempts to verify a non-family member or unauthorized profile."""

    def __init__(self, message: str = "Unauthorized: No active family circle relationship exists."):
        super().__init__(message, code="UNAUTHORIZED_ACCESS")


class IncompatibleEmbeddingError(SpeakerRecognitionError):
    """Raised when stored or test embeddings have incompatible dimensions or formats."""

    def __init__(self, message: str = "Embedding dimension or format mismatch"):
        super().__init__(message, code="INCOMPATIBLE_EMBEDDING")

