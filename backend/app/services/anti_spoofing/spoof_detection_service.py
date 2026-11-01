"""
EchoShield — Anti-Spoofing Model Inference Service
==================================================
Executes forward classification through the Wav2Vec2 deepfake audio model,
producing raw logits and normalized probability distributions.
"""

import logging
from typing import Optional

import numpy as np
import torch

from app.services.anti_spoofing.exceptions import (
    ModelUnavailableError,
    SpoofInferenceError,
)
from app.services.anti_spoofing.model_loader import (
    MODEL_NAME,
    MODEL_VERSION,
    anti_spoof_model_loader,
)
from app.services.anti_spoofing.schemas import ModelInferenceOutput

logger = logging.getLogger("echoshield.antispoof.inference")


class SpoofDetectionService:
    """
    Coordinates feature extraction and neural network inference for anti-spoofing.
    """

    def __init__(self, loader=None):
        self.loader = loader or anti_spoof_model_loader

    def predict(self, audio_data: np.ndarray) -> ModelInferenceOutput:
        """
        Executes model inference on a 1D 16kHz float32 audio array.

        Returns:
            ModelInferenceOutput with genuine logits and softmax probabilities.

        Raises:
            ModelUnavailableError: If model cannot be loaded.
            SpoofInferenceError: If forward pass produces NaN/inf or fails.
        """
        try:
            model, feature_extractor = self.loader.get_model_and_extractor()
        except ModelUnavailableError:
            raise
        except Exception as exc:
            raise ModelUnavailableError(f"Failed to access anti-spoofing model: {exc}") from exc

        try:
            # Prepare Wav2Vec2 inputs
            inputs = feature_extractor(
                audio_data,
                sampling_rate=16000,
                return_tensors="pt"
            )
            device = self.loader.device
            inputs = {k: v.to(device) for k, v in inputs.items()}

            with torch.no_grad():
                outputs = model(**inputs)
                logits = outputs.logits

            # Validate tensor properties
            if logits is None or logits.ndim != 2 or logits.shape[-1] < 2:
                raise SpoofInferenceError(f"Unexpected model logits shape: {getattr(logits, 'shape', None)}")

            logits_cpu = logits.detach().cpu().squeeze(0)

            # Check for non-finite values (NaN / Inf)
            if torch.isnan(logits_cpu).any() or torch.isinf(logits_cpu).any():
                raise SpoofInferenceError("Model inference produced NaN or Infinite logit values.")

            probs = torch.softmax(logits_cpu, dim=-1)

            # Map based on config id2label: 0 is 'fake', 1 is 'real'
            fake_logit = float(logits_cpu[0].item())
            real_logit = float(logits_cpu[1].item())
            fake_prob = float(probs[0].item())
            real_prob = float(probs[1].item())

            # Numerical safety clamps [0.0, 1.0]
            fake_prob = max(0.0, min(1.0, fake_prob))
            real_prob = max(0.0, min(1.0, real_prob))

            return ModelInferenceOutput(
                fake_logit=round(fake_logit, 4),
                real_logit=round(real_logit, 4),
                fake_probability=round(fake_prob, 4),
                real_probability=round(real_prob, 4),
                model_name=self.loader.model_name,
                model_version=self.loader.model_version
            )

        except (SpoofInferenceError, ModelUnavailableError):
            raise
        except Exception as exc:
            logger.error(f"Inference exception in SpoofDetectionService: {exc}", exc_info=True)
            raise SpoofInferenceError(f"Tensor inference failed: {str(exc)}") from exc


# Global service instance
spoof_detection_service = SpoofDetectionService()

