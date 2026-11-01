"""
EchoShield — Anti-Spoofing & Deepfake Detection Engine Exceptions
================================================================
Domain exceptions for audio preprocessing, model loading, tensor inference,
and quality inspection in AI voice spoofing detection.
"""

from typing import Optional


class AntiSpoofError(Exception):
    """Base exception for all anti-spoofing engine errors."""

    def __init__(self, message: str, code: str = "ANTI_SPOOF_ERROR"):
        super().__init__(message)
        self.message = message
        self.code = code


class ModelUnavailableError(AntiSpoofError):
    """Raised when the pretrained anti-spoofing model fails to load or execute."""

    def __init__(self, message: str = "Pretrained anti-spoofing model is unavailable or failed to initialize."):
        super().__init__(message, code="MODEL_UNAVAILABLE")


class AudioPreprocessingError(AntiSpoofError):
    """Raised when audio decoding fails or the format is invalid/corrupt."""

    def __init__(self, message: str = "Invalid or corrupted audio data."):
        super().__init__(message, code="AUDIO_PREPROCESSING_ERROR")


class AudioQualityError(AntiSpoofError):
    """Raised when audio fails acoustic quality constraints (duration, RMS, silence, clipping)."""

    def __init__(self, message: str, quality_detail: Optional[str] = None):
        super().__init__(message, code="AUDIO_QUALITY_FAILED")
        self.quality_detail = quality_detail or message


class SpoofInferenceError(AntiSpoofError):
    """Raised when model inference outputs invalid, non-finite, or unexpected shapes."""

    def __init__(self, message: str = "Error during anti-spoofing tensor inference."):
        super().__init__(message, code="SPOOF_INFERENCE_ERROR")

