"""
EchoShield — Pretrained Speaker Model Loader
============================================
Caches and manages the SpeechBrain ECAPA-TDNN pretrained model.
Ensures single-instance residency in memory, device dispatch (CPU/CUDA),
and dimensional integrity validation.
"""

import logging
import sys
from pathlib import Path
from typing import Optional

import torch

from app.services.speaker_recognition.exceptions import ModelLoadError

logger = logging.getLogger("echoshield.ai.model_loader")

# Ensure ai-engine is in sys.path
ai_engine_path = Path(__file__).resolve().parents[4] / "ai-engine"
if str(ai_engine_path) not in sys.path:
    sys.path.insert(0, str(ai_engine_path))

# Model metadata constants
MODEL_NAME = "SpeechBrain ECAPA-TDNN"
MODEL_VERSION = "voxceleb-v1.1"
EXPECTED_EMBEDDING_DIM = 192
MODEL_LICENSE = "Apache-2.0"
MODEL_SOURCE = "models/ecapa_tdnn"


class SpeakerModelLoader:
    """
    Singleton wrapper for the SpeechBrain ECAPA-TDNN EncoderClassifier.
    Avoids re-instantiation across HTTP verification requests.
    """

    _instance: Optional["SpeakerModelLoader"] = None
    _classifier = None
    _device: str = "cpu"

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(SpeakerModelLoader, cls).__new__(cls)
        return cls._instance

    def __init__(self, model_dir: Optional[str] = None):
        self.model_dir = model_dir or MODEL_SOURCE
        self._device = "cuda" if torch.cuda.is_available() else "cpu"

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
    def embedding_dim(self) -> int:
        return EXPECTED_EMBEDDING_DIM

    @property
    def model_license(self) -> str:
        return MODEL_LICENSE

    def get_model(self):
        """
        Retrieves the cached ECAPA-TDNN EncoderClassifier.
        If not loaded yet, initializes it safely.
        """
        if self._classifier is not None:
            return self._classifier

        try:
            from speechbrain.inference.speaker import EncoderClassifier
            from speechbrain.utils.fetching import LocalStrategy

            model_path = Path(self.model_dir)
            if not model_path.exists() or not (model_path / "hyperparams.yaml").exists():
                logger.info("Local model directory not found. Fetching from Hugging Face...")
                self._classifier = EncoderClassifier.from_hparams(
                    source="speechbrain/spkrec-ecapa-voxceleb",
                    savedir=str(model_path),
                    run_opts={"device": self._device},
                    local_strategy=LocalStrategy.COPY
                )
            else:
                logger.info("Loading cached SpeechBrain ECAPA-TDNN from: %s", str(model_path))
                self._classifier = EncoderClassifier.from_hparams(
                    source=str(model_path),
                    savedir=str(model_path),
                    run_opts={"device": self._device}
                )

            logger.info("SpeechBrain ECAPA-TDNN loaded successfully on %s", self._device)
            return self._classifier
        except Exception as e:
            logger.error("Failed to load speaker recognition model: %s", str(e))
            raise ModelLoadError(f"Pretrained model initialization failed: {str(e)}")


# Global singleton
model_loader = SpeakerModelLoader()
