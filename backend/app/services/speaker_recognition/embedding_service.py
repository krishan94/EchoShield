"""
EchoShield — Speaker Embedding Extraction Service
==================================================
Runs SpeechBrain ECAPA-TDNN inference in PyTorch evaluation mode
to extract genuine, length-normalized 192-dimensional speaker embeddings.
"""

import logging

import numpy as np
import torch

from app.services.speaker_recognition.exceptions import (
    IncompatibleEmbeddingError,
    ModelLoadError,
)
from app.services.speaker_recognition.model_loader import (
    EXPECTED_EMBEDDING_DIM,
    model_loader,
)

logger = logging.getLogger("echoshield.ai.embedding_service")


class EmbeddingService:
    """
    Extracts L2-normalized 192-dim speaker embeddings using ECAPA-TDNN.
    """

    def __init__(self, loader=None):
        self.loader = loader or model_loader

    def extract_embedding(self, audio_data: np.ndarray) -> np.ndarray:
        """
        Extracts a normalized 192-dimensional vector from float32 audio data.
        """
        if audio_data is None or len(audio_data) == 0:
            raise ValueError("Audio data cannot be empty.")

        try:
            classifier = self.loader.get_model()
        except Exception as e:
            raise ModelLoadError(f"Cannot obtain speaker model: {str(e)}")

        device = self.loader.device
        tensor_audio = torch.from_numpy(audio_data).unsqueeze(0).to(device)

        with torch.no_grad():
            emb_tensor = classifier.encode_batch(tensor_audio)
            emb_np = emb_tensor.squeeze().cpu().numpy().astype(np.float32)

        # Validate dimensional integrity
        if emb_np.ndim != 1 or emb_np.shape[0] != EXPECTED_EMBEDDING_DIM:
            raise IncompatibleEmbeddingError(
                f"Model produced invalid embedding dimension: {emb_np.shape}, expected ({EXPECTED_EMBEDDING_DIM},)."
            )

        if not np.all(np.isfinite(emb_np)):
            raise IncompatibleEmbeddingError("Extracted embedding contains NaN or Inf values.")

        # L2-normalization
        norm = float(np.linalg.norm(emb_np))
        if norm > 0:
            emb_np = emb_np / norm

        return emb_np


# Global instance
embedding_service = EmbeddingService()
