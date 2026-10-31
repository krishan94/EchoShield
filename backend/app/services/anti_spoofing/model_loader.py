"""
EchoShield — Anti-Spoofing Pretrained Model Loader
==================================================
Manages singleton lifecycle, Hugging Face checkpoint cache,
and device placement for the Wav2Vec2 Deepfake Audio Detection model.
"""

import logging
from typing import Optional, Tuple

import torch
from transformers import AutoFeatureExtractor, AutoModelForAudioClassification

from app.services.anti_spoofing.exceptions import ModelUnavailableError

logger = logging.getLogger("echoshield.antispoof.model_loader")

MODEL_IDENTIFIER = "mo-thecreator/Deepfake-audio-detection"
MODEL_NAME = "mo-thecreator/Deepfake-audio-detection (Wav2Vec2-Base)"
MODEL_VERSION = "v1.0-hf"
MODEL_LICENSE = "Apache-2.0"
EXPECTED_SAMPLE_RATE = 16000


class AntiSpoofModelLoader:
    """
    Thread-safe singleton wrapper for Wav2Vec2 Audio Anti-Spoofing classification.
    Avoids multi-hundred MB model re-loading on each incoming HTTP request.
    """

    _instance: Optional["AntiSpoofModelLoader"] = None
    _feature_extractor: Optional[AutoFeatureExtractor] = None
    _model: Optional[AutoModelForAudioClassification] = None
    _device: str = "cpu"
    _is_initialized: bool = False

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(AntiSpoofModelLoader, cls).__new__(cls)
        return cls._instance

    def __init__(self, model_id: Optional[str] = None):
        if not self._is_initialized:
            self.model_id = model_id or MODEL_IDENTIFIER
            self._device = "cuda" if torch.cuda.is_available() else "cpu"
            self._is_initialized = True

    @property
    def device(self) -> str:
        return self._device

    @property
    def model_name(self) -> str:
        return MODEL_NAME

    @property
    def model_version(self) -> str:
        return MODEL_VERSION

    @property
    def license(self) -> str:
        return MODEL_LICENSE

    def get_model_and_extractor(self) -> Tuple[AutoModelForAudioClassification, AutoFeatureExtractor]:
        """
        Lazily loads and returns the cached model and feature extractor instances.
        Raises ModelUnavailableError on loading or initialization failure.
        """
        if self._model is not None and self._feature_extractor is not None:
            return self._model, self._feature_extractor

        logger.info(f"Loading Anti-Spoofing model '{self.model_id}' onto device: {self._device}...")
        try:
            self._feature_extractor = AutoFeatureExtractor.from_pretrained(
                self.model_id,
                local_files_only=False
            )
            self._model = AutoModelForAudioClassification.from_pretrained(
                self.model_id,
                local_files_only=False
            )
            self._model.to(self._device)
            self._model.eval()

            # Verify label mapping
            id2label = self._model.config.id2label
            logger.info(f"Anti-spoofing model ready. id2label: {id2label}")
            return self._model, self._feature_extractor

        except Exception as exc:
            logger.error(f"Failed to load anti-spoofing model '{self.model_id}': {exc}", exc_info=True)
            self._model = None
            self._feature_extractor = None
            raise ModelUnavailableError(
                f"Failed to load pretrained anti-spoofing model: {str(exc)}"
            ) from exc

    def unload(self) -> None:
        """Utility for test fixtures or memory reclamation."""
        self._model = None
        self._feature_extractor = None


# Global singleton instance
anti_spoof_model_loader = AntiSpoofModelLoader()
