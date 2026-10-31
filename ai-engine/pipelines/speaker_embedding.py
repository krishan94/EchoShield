"""
EchoShield — AI Speaker Embedding Pipeline & Audio Quality Validation
=====================================================================
Uses SpeechBrain ECAPA-TDNN (VoxCeleb pretrained, Apache 2.0 license)
to extract genuine 192-dimensional speaker embeddings from 16kHz audio.
Provides acoustic quality validation and AES-256-GCM biometric encryption.
"""

import io
import logging
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import soundfile as sf
import torch
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

logger = logging.getLogger("echoshield.ai.speaker")

# Standard audio requirements for SpeechBrain ECAPA-TDNN
EXPECTED_SAMPLE_RATE = 16000
EXPECTED_CHANNELS = 1  # Mono
MIN_DURATION_SECONDS = 2.5
MAX_DURATION_SECONDS = 15.0
MIN_RMS_ENERGY = 0.01  # Silence floor threshold (-40 dBFS)
MAX_SILENCE_RATIO = 0.65  # Max 65% silence allowed
MAX_CLIPPING_RATIO = 0.01  # Max 1% clipped samples allowed

# Model constants
MODEL_SOURCE = "models/ecapa_tdnn"
EMBEDDING_DIM = 192
MODEL_NAME = "SpeechBrain ECAPA-TDNN"
MODEL_VERSION = "voxceleb-v1.1"

# AES-256 key configuration (32 bytes = 256 bits)
DEFAULT_KEY = b"EchoShieldBiometricKey2026Secure"  # 32-byte default for development


@dataclass
class AudioQualityReport:
    is_valid: bool
    duration_seconds: float
    sample_rate: int
    channels: int
    rms_energy: float
    silence_ratio: float
    clipping_ratio: float
    snr_db: float
    error_message: Optional[str] = None


class SpeakerEmbeddingEngine:
    """
    Manages SpeechBrain ECAPA-TDNN model inference, audio quality validation,
    and AES-256-GCM biometric encryption for voice profiles.
    """

    def __init__(self, model_dir: Optional[str] = None):
        self.model_dir = model_dir or MODEL_SOURCE
        self._classifier = None
        self._device = "cuda" if torch.cuda.is_available() else "cpu"
        self._encryption_key = os.environ.get("ECHOSHIELD_VOICE_KEY", "").encode("utf-8")
        if len(self._encryption_key) != 32:
            self._encryption_key = DEFAULT_KEY

    def load_model(self):
        """
        Loads the pretrained ECAPA-TDNN EncoderClassifier.
        """
        if self._classifier is not None:
            return self._classifier

        try:
            from speechbrain.inference.speaker import EncoderClassifier
            from speechbrain.utils.fetching import LocalStrategy

            model_path = Path(self.model_dir)
            if not model_path.exists() or not (model_path / "hyperparams.yaml").exists():
                logger.info("ECAPA-TDNN local directory missing. Fetching from HuggingFace...")
                self._classifier = EncoderClassifier.from_hparams(
                    source="speechbrain/spkrec-ecapa-voxceleb",
                    savedir=str(model_path),
                    run_opts={"device": self._device},
                    local_strategy=LocalStrategy.COPY
                )
            else:
                logger.info("Loading ECAPA-TDNN from local path: %s", self.model_dir)
                self._classifier = EncoderClassifier.from_hparams(
                    source=str(model_path),
                    savedir=str(model_path),
                    run_opts={"device": self._device}
                )
            logger.info("ECAPA-TDNN model initialized successfully on %s", self._device)
            return self._classifier
        except Exception as e:
            logger.error("Failed to load ECAPA-TDNN model: %s", str(e))
            raise RuntimeError(f"Failed to load speaker embedding model: {str(e)}")

    def validate_audio(self, audio_bytes: bytes) -> Tuple[AudioQualityReport, Optional[np.ndarray]]:
        """
        Inspects raw audio bytes (WAV), checks duration, sample rate, channels,
        RMS energy, silence percentage, and clipping.
        Returns a detailed AudioQualityReport and decoded float32 numpy array.
        """
        if not audio_bytes or len(audio_bytes) < 44:
            return AudioQualityReport(
                is_valid=False,
                duration_seconds=0.0,
                sample_rate=0,
                channels=0,
                rms_energy=0.0,
                silence_ratio=1.0,
                clipping_ratio=0.0,
                snr_db=0.0,
                error_message="Audio recording is empty or missing valid WAV header."
            ), None

        try:
            with io.BytesIO(audio_bytes) as bio:
                with sf.SoundFile(bio) as sound_file:
                    sample_rate = sound_file.samplerate
                    channels = sound_file.channels
                    frames = sound_file.frames
                    duration = frames / float(sample_rate)
                    audio_data = sound_file.read(dtype="float32")
        except Exception as e:
            return AudioQualityReport(
                is_valid=False,
                duration_seconds=0.0,
                sample_rate=0,
                channels=0,
                rms_energy=0.0,
                silence_ratio=1.0,
                clipping_ratio=0.0,
                snr_db=0.0,
                error_message=f"Corrupted or unsupported audio format: {str(e)}"
            ), None

        # Convert stereo to mono if needed
        if channels > 1:
            audio_data = np.mean(audio_data, axis=1)
            channels = 1

        # Check sample rate
        if sample_rate != EXPECTED_SAMPLE_RATE:
            return AudioQualityReport(
                is_valid=False,
                duration_seconds=duration,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=0.0,
                silence_ratio=0.0,
                clipping_ratio=0.0,
                snr_db=0.0,
                error_message=f"Invalid sample rate: {sample_rate}Hz. SpeechBrain requires exactly 16,000Hz PCM."
            ), None

        # Check duration
        if duration < MIN_DURATION_SECONDS:
            return AudioQualityReport(
                is_valid=False,
                duration_seconds=duration,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=0.0,
                silence_ratio=0.0,
                clipping_ratio=0.0,
                snr_db=0.0,
                error_message=f"Recording too short ({duration:.1f}s). Minimum required duration is {MIN_DURATION_SECONDS}s."
            ), None

        if duration > MAX_DURATION_SECONDS:
            return AudioQualityReport(
                is_valid=False,
                duration_seconds=duration,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=0.0,
                silence_ratio=0.0,
                clipping_ratio=0.0,
                snr_db=0.0,
                error_message=f"Recording too long ({duration:.1f}s). Maximum duration is {MAX_DURATION_SECONDS}s."
            ), None

        # RMS Energy check (volume floor)
        rms = float(np.sqrt(np.mean(np.square(audio_data))))
        if rms < MIN_RMS_ENERGY:
            return AudioQualityReport(
                is_valid=False,
                duration_seconds=duration,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=rms,
                silence_ratio=1.0,
                clipping_ratio=0.0,
                snr_db=0.0,
                error_message="Microphone input too low. Audio is near silent or microphone is muted."
            ), None

        # Silence ratio using frame energy VAD (30ms windows)
        frame_len = int(sample_rate * 0.030)
        num_frames = len(audio_data) // frame_len
        if num_frames > 0:
            frames_matrix = audio_data[:num_frames * frame_len].reshape((num_frames, frame_len))
            frame_energies = np.sqrt(np.mean(np.square(frames_matrix), axis=1))
            silent_frames = np.sum(frame_energies < (rms * 0.25))
            silence_ratio = float(silent_frames / num_frames)
        else:
            silence_ratio = 0.0

        if silence_ratio > MAX_SILENCE_RATIO:
            return AudioQualityReport(
                is_valid=False,
                duration_seconds=duration,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=rms,
                silence_ratio=silence_ratio,
                clipping_ratio=0.0,
                snr_db=0.0,
                error_message=f"Too much silence ({silence_ratio * 100:.0f}%). Please speak clearly into the microphone."
            ), None

        # Clipping detection (samples near +/- 1.0)
        clipping_samples = np.sum(np.abs(audio_data) >= 0.99)
        clipping_ratio = float(clipping_samples / len(audio_data))
        if clipping_ratio > MAX_CLIPPING_RATIO:
            return AudioQualityReport(
                is_valid=False,
                duration_seconds=duration,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=rms,
                silence_ratio=silence_ratio,
                clipping_ratio=clipping_ratio,
                snr_db=0.0,
                error_message=f"Audio clipping detected ({clipping_ratio * 100:.1f}%). Please move slightly further from the microphone."
            ), None

        # Estimate Signal-to-Noise Ratio (SNR) in dB
        noise_floor = np.percentile(np.abs(audio_data), 10) + 1e-6
        signal_peak = np.percentile(np.abs(audio_data), 95) + 1e-6
        snr_db = float(20 * np.log10(signal_peak / noise_floor))

        report = AudioQualityReport(
            is_valid=True,
            duration_seconds=duration,
            sample_rate=sample_rate,
            channels=channels,
            rms_energy=rms,
            silence_ratio=silence_ratio,
            clipping_ratio=clipping_ratio,
            snr_db=max(0.0, snr_db),
            error_message=None
        )
        return report, audio_data

    def extract_embedding(self, audio_data: np.ndarray) -> np.ndarray:
        """
        Extracts a length-normalized 192-dimensional ECAPA-TDNN embedding vector.
        """
        classifier = self.load_model()
        tensor_audio = torch.from_numpy(audio_data).unsqueeze(0).to(self._device)
        with torch.no_grad():
            emb_tensor = classifier.encode_batch(tensor_audio)
            # Squeeze to shape [192]
            emb_np = emb_tensor.squeeze().cpu().numpy().astype(np.float32)

        # Ensure L2 normalization
        norm = np.linalg.norm(emb_np)
        if norm > 0:
            emb_np = emb_np / norm
        return emb_np

    def process_enrollment_samples(
        self, samples_bytes: List[bytes]
    ) -> Tuple[np.ndarray, float, List[AudioQualityReport]]:
        """
        Validates multiple independent speech samples, extracts embeddings,
        computes the centroid voice signature, and calculates acoustic quality score.
        """
        if len(samples_bytes) < 3:
            raise ValueError(f"At least 3 distinct audio samples required for enrollment, got {len(samples_bytes)}.")

        reports: List[AudioQualityReport] = []
        embeddings: List[np.ndarray] = []

        for idx, sample_bytes in enumerate(samples_bytes):
            report, audio_data = self.validate_audio(sample_bytes)
            reports.append(report)
            if not report.is_valid:
                raise ValueError(f"Sample #{idx + 1} quality validation failed: {report.error_message}")
            emb = self.extract_embedding(audio_data)
            embeddings.append(emb)

        # Compute centroid (average embedding across all 3 samples)
        stacked = np.stack(embeddings, axis=0)  # [3, 192]
        centroid = np.mean(stacked, axis=0)
        norm = np.linalg.norm(centroid)
        if norm > 0:
            centroid = centroid / norm

        # Inter-sample consistency (cosine similarity between samples)
        pairwise_similarities = []
        for i in range(len(embeddings)):
            for j in range(i + 1, len(embeddings)):
                sim = float(np.dot(embeddings[i], embeddings[j]))
                pairwise_similarities.append(sim)

        avg_consistency = float(np.mean(pairwise_similarities)) if pairwise_similarities else 0.8
        # Overall quality score: combination of consistency and acoustic SNR
        avg_snr = float(np.mean([r.snr_db for r in reports]))
        snr_factor = min(1.0, max(0.5, avg_snr / 30.0))
        consistency_factor = min(1.0, max(0.5, (avg_consistency + 1.0) / 2.0))
        overall_quality = round(float(0.7 * consistency_factor + 0.3 * snr_factor), 2)
        overall_quality = min(0.99, max(0.60, overall_quality))

        return centroid, overall_quality, reports

    def encrypt_embedding(self, embedding: np.ndarray) -> Dict[str, str]:
        """
        Encrypts the 192-dimensional floating-point embedding vector using AES-256-GCM.
        Returns a dict containing hex-encoded ciphertext, nonce, and tag.
        """
        aesgcm = AESGCM(self._encryption_key)
        nonce = os.urandom(12)  # Standard 96-bit GCM nonce
        payload_bytes = embedding.tobytes()

        # AES-GCM encrypt produces ciphertext + 16-byte authentication tag
        encrypted_data = aesgcm.encrypt(nonce, payload_bytes, associated_data=b"EchoShieldVoiceEmbeddingV1")

        import base64
        return {
            "ciphertext": base64.b64encode(encrypted_data).decode("ascii"),
            "nonce": base64.b64encode(nonce).decode("ascii"),
            "algorithm": "AES-256-GCM",
            "dim": str(len(embedding))
        }

    def decrypt_embedding(self, encrypted_record: Dict[str, str]) -> np.ndarray:
        """
        Decrypts an AES-256-GCM encrypted embedding record back into a 192-dim numpy array.
        """
        import base64
        aesgcm = AESGCM(self._encryption_key)
        ciphertext = base64.b64decode(encrypted_record["ciphertext"])
        nonce = base64.b64decode(encrypted_record["nonce"])

        decrypted_bytes = aesgcm.decrypt(nonce, ciphertext, associated_data=b"EchoShieldVoiceEmbeddingV1")
        return np.frombuffer(decrypted_bytes, dtype=np.float32)


# Global singleton instance
speaker_engine = SpeakerEmbeddingEngine()
