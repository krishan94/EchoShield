"""
EchoShield — Speaker Similarity Service
=======================================
Calculates genuine cosine similarity between newly extracted speaker embeddings
and enrolled biometric signatures.

Cosine similarity for L2-normalized embeddings:
    S = dot(u, v) / (||u|| * ||v||) = dot(u, v)

Embeddings in EchoShield are strictly 192-dimensional vectors generated
by SpeechBrain ECAPA-TDNN. The similarity score represents mathematical
vector proximity in the VoxCeleb hypersphere, not a percentage identity probability.
"""

import logging
from typing import Dict, List, Union

import numpy as np

from app.services.speaker_recognition.exceptions import IncompatibleEmbeddingError
from app.services.speaker_recognition.model_loader import EXPECTED_EMBEDDING_DIM

logger = logging.getLogger("echoshield.ai.similarity")


class SimilarityService:
    """
    Computes mathematical cosine similarity between voice embeddings.
    """

    def compute_cosine_similarity(
        self,
        test_embedding: np.ndarray,
        enrolled_embedding: np.ndarray
    ) -> float:
        """
        Computes cosine similarity between two 192-dim vectors.
        Validates dimensions, normalizes, and bounds output to [-1.0, 1.0].
        """
        if test_embedding is None or enrolled_embedding is None:
            raise IncompatibleEmbeddingError("Cannot compute similarity on null embeddings.")

        if test_embedding.shape[0] != EXPECTED_EMBEDDING_DIM:
            raise IncompatibleEmbeddingError(
                f"Test embedding dimension {test_embedding.shape[0]} does not match expected {EXPECTED_EMBEDDING_DIM}."
            )

        if enrolled_embedding.shape[0] != EXPECTED_EMBEDDING_DIM:
            raise IncompatibleEmbeddingError(
                f"Enrolled embedding dimension {enrolled_embedding.shape[0]} does not match expected {EXPECTED_EMBEDDING_DIM}."
            )

        # L2-normalize vectors to ensure unit norm
        norm_test = float(np.linalg.norm(test_embedding))
        norm_enrolled = float(np.linalg.norm(enrolled_embedding))

        if norm_test <= 0 or norm_enrolled <= 0:
            raise IncompatibleEmbeddingError("Zero-magnitude embedding encountered.")

        u = test_embedding / norm_test
        v = enrolled_embedding / norm_enrolled

        # Dot product of unit vectors equals cosine similarity
        score = float(np.dot(u, v))
        # Clamp to [-1.0, 1.0] to guard against tiny float precision drift
        score = max(-1.0, min(1.0, score))
        return round(score, 4)

    def compare_multi_sample(
        self,
        test_embedding: np.ndarray,
        enrolled_samples: List[np.ndarray],
        aggregation: str = "centroid_first"
    ) -> Dict[str, Union[float, List[float]]]:
        """
        Compares test embedding against multiple enrolled samples or centroid.
        Supported aggregations:
        - "centroid_first": Computes centroid of enrolled samples and matches against it (primary in EchoShield).
        - "max": Maximum pairwise score across samples.
        - "mean": Mean pairwise score across all enrolled samples.
        """
        if not enrolled_samples:
            raise IncompatibleEmbeddingError("No enrolled sample embeddings provided for comparison.")

        pairwise_scores: List[float] = [
            self.compute_cosine_similarity(test_embedding, sample)
            for sample in enrolled_samples
        ]

        # Centroid calculation
        stacked = np.stack(enrolled_samples, axis=0)
        centroid = np.mean(stacked, axis=0)
        centroid_score = self.compute_cosine_similarity(test_embedding, centroid)

        return {
            "primary_score": centroid_score,
            "max_score": round(max(pairwise_scores), 4),
            "mean_score": round(float(np.mean(pairwise_scores)), 4),
            "pairwise_scores": pairwise_scores,
            "sample_count": len(enrolled_samples)
        }


# Global instance
similarity_service = SimilarityService()
