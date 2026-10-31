"""
EchoShield — Anti-Spoofing Audio Preprocessing & Quality Verification
=====================================================================
Decodes, normalizes, resamples, and acoustically screens input audio
prior to Wav2Vec2 deepfake classification.
"""

import io
import logging
from typing import Optional, Tuple

import numpy as np
import soundfile as sf
import torch
import torchaudio

from app.services.anti_spoofing.exceptions import (
    AudioPreprocessingError,
    AudioQualityError,
)
from app.services.anti_spoofing.schemas import AudioQualityReport

logger = logging.getLogger("echoshield.antispoof.preprocessor")

TARGET_SAMPLE_RATE = 16000
MIN_DURATION_SECONDS = 2.0
MAX_DURATION_SECONDS = 15.0
MIN_RMS_ENERGY = 0.005  # -46 dBFS volume threshold
MAX_SILENCE_RATIO = 0.70
MAX_CLIPPING_RATIO = 0.02
FRAME_SIZE = 512
HOP_SIZE = 256


class AntiSpoofAudioPreprocessor:
    """
    Decodes raw audio bytes, downmixes to mono, standardizes to 16 kHz PCM float32,
    and performs automated signal-to-noise, silence, and clipping safety checks.
    """

    def preprocess_and_validate(
        self, audio_bytes: bytes, filename: str = "audio.wav"
    ) -> Tuple[np.ndarray, AudioQualityReport]:
        """
        Processes audio bytes into a validated (N,) float32 16kHz numpy array.

        Raises:
            AudioPreprocessingError: If data is empty or corrupt.
            AudioQualityError: If audio fails duration, volume, or signal quality checks.
        """
        if not audio_bytes or len(audio_bytes) < 44:
            raise AudioPreprocessingError("Audio payload is empty or too small to contain valid audio data.")

        # Decode via soundfile or torchaudio
        data, sample_rate, channels = self._decode_audio(audio_bytes, filename)

        # Downmix to mono if multi-channel
        if channels > 1:
            if data.ndim == 2:
                data = np.mean(data, axis=1)
            channels = 1

        # Flatten 1D array
        data = np.squeeze(data).astype(np.float32)
        if data.ndim != 1 or len(data) == 0:
            raise AudioPreprocessingError("Audio signal has invalid shape or contains no samples.")

        # Resample to 16,000 Hz if necessary
        if sample_rate != TARGET_SAMPLE_RATE:
            data = self._resample(data, sample_rate, TARGET_SAMPLE_RATE)
            sample_rate = TARGET_SAMPLE_RATE

        duration_seconds = round(float(len(data)) / sample_rate, 3)

        # 1. Duration check
        if duration_seconds < MIN_DURATION_SECONDS:
            report = AudioQualityReport(
                is_valid=False,
                duration_seconds=duration_seconds,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=0.0,
                silence_ratio=0.0,
                clipping_ratio=0.0,
                snr_db=0.0,
                status_label="TOO_SHORT",
                error_message=(
                    f"Audio duration ({duration_seconds:.2f}s) is shorter than required minimum "
                    f"({MIN_DURATION_SECONDS:.1f}s) for reliable acoustic spoofing analysis."
                )
            )
            raise AudioQualityError(report.error_message, quality_detail="DURATION_TOO_SHORT")

        if duration_seconds > MAX_DURATION_SECONDS:
            report = AudioQualityReport(
                is_valid=False,
                duration_seconds=duration_seconds,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=0.0,
                silence_ratio=0.0,
                clipping_ratio=0.0,
                snr_db=0.0,
                status_label="TOO_LONG",
                error_message=(
                    f"Audio duration ({duration_seconds:.2f}s) exceeds maximum allowed limit "
                    f"({MAX_DURATION_SECONDS:.1f}s)."
                )
            )
            raise AudioQualityError(report.error_message, quality_detail="DURATION_TOO_LONG")

        # 2. RMS Energy calculation
        rms = float(np.sqrt(np.mean(data ** 2) + 1e-12))
        if rms < MIN_RMS_ENERGY:
            report = AudioQualityReport(
                is_valid=False,
                duration_seconds=duration_seconds,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=rms,
                silence_ratio=1.0,
                clipping_ratio=0.0,
                snr_db=0.0,
                status_label="TOO_QUIET",
                error_message=f"Audio energy ({rms:.4f}) is below minimum audible floor ({MIN_RMS_ENERGY})."
            )
            raise AudioQualityError(report.error_message, quality_detail="ENERGY_TOO_LOW")

        # 3. Clipping ratio
        clipping_threshold = 0.99
        clipped_samples = int(np.sum(np.abs(data) >= clipping_threshold))
        clipping_ratio = round(float(clipped_samples) / len(data), 4)
        if clipping_ratio > MAX_CLIPPING_RATIO:
            report = AudioQualityReport(
                is_valid=False,
                duration_seconds=duration_seconds,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=rms,
                silence_ratio=0.0,
                clipping_ratio=clipping_ratio,
                snr_db=0.0,
                status_label="CLIPPING_DETECTED",
                error_message=f"Audio signal is distorted by clipping ({clipping_ratio * 100:.1f}% samples clipped)."
            )
            raise AudioQualityError(report.error_message, quality_detail="CLIPPING_EXCEEDED")

        # 4. Silence ratio
        silence_threshold = 0.008
        num_frames = (len(data) - FRAME_SIZE) // HOP_SIZE + 1
        silent_frames = 0
        if num_frames > 0:
            for i in range(num_frames):
                start = i * HOP_SIZE
                frame = data[start : start + FRAME_SIZE]
                frame_rms = np.sqrt(np.mean(frame ** 2) + 1e-12)
                if frame_rms < silence_threshold:
                    silent_frames += 1
            silence_ratio = round(float(silent_frames) / num_frames, 4)
        else:
            silence_ratio = 0.0

        if silence_ratio > MAX_SILENCE_RATIO:
            report = AudioQualityReport(
                is_valid=False,
                duration_seconds=duration_seconds,
                sample_rate=sample_rate,
                channels=channels,
                rms_energy=rms,
                silence_ratio=silence_ratio,
                clipping_ratio=clipping_ratio,
                snr_db=0.0,
                status_label="TOO_MUCH_SILENCE",
                error_message=f"Audio contains excessive silence ({silence_ratio * 100:.1f}% silent frames)."
            )
            raise AudioQualityError(report.error_message, quality_detail="SILENCE_EXCEEDED")

        # 5. Estimated SNR in dB
        sorted_sq = np.sort(data ** 2)
        noise_floor = float(np.mean(sorted_sq[: max(1, int(len(sorted_sq) * 0.15))]) + 1e-10)
        signal_level = float(np.mean(sorted_sq[int(len(sorted_sq) * 0.15) :]) + 1e-10)
        snr_db = round(float(10.0 * np.log10(signal_level / noise_floor)), 2)

        report = AudioQualityReport(
            is_valid=True,
            duration_seconds=duration_seconds,
            sample_rate=sample_rate,
            channels=channels,
            rms_energy=round(rms, 4),
            silence_ratio=silence_ratio,
            clipping_ratio=clipping_ratio,
            snr_db=snr_db,
            status_label="EXCELLENT" if snr_db >= 15.0 else ("FAIR" if snr_db >= 8.0 else "NOISY"),
            error_message=None
        )

        return data, report

    def _decode_audio(self, audio_bytes: bytes, filename: str) -> Tuple[np.ndarray, int, int]:
        """Attempts soundfile decoding with torchaudio fallback."""
        try:
            with sf.SoundFile(io.BytesIO(audio_bytes)) as sfile:
                sample_rate = sfile.samplerate
                channels = sfile.channels
                data = sfile.read(dtype="float32")
                return data, sample_rate, channels
        except Exception as sf_err:
            logger.debug(f"soundfile decode failed for '{filename}', trying torchaudio: {sf_err}")
            try:
                tensor, sample_rate = torchaudio.load(io.BytesIO(audio_bytes))
                channels = tensor.shape[0]
                data = tensor.numpy().T  # (samples, channels)
                return data, sample_rate, channels
            except Exception as ta_err:
                logger.error(f"Failed to decode audio bytes: {ta_err}")
                raise AudioPreprocessingError(
                    f"Unsupported or corrupted audio format in '{filename}'. Please provide standard WAV/PCM audio."
                ) from ta_err

    def _resample(self, data: np.ndarray, orig_sr: int, target_sr: int) -> np.ndarray:
        """Resamples 1D float32 array using torchaudio resampler."""
        tensor = torch.from_numpy(data).unsqueeze(0)
        resampler = torchaudio.transforms.Resample(orig_freq=orig_sr, new_freq=target_sr)
        resampled_tensor = resampler(tensor)
        return resampled_tensor.squeeze(0).numpy().astype(np.float32)


# Global preprocessor instance
anti_spoof_preprocessor = AntiSpoofAudioPreprocessor()
