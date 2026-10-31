"""
EchoShield — Audio Preprocessing & Quality Inspection
=====================================================
Validates incoming audio files against standard acoustic parameters
required for SpeechBrain ECAPA-TDNN feature extraction:
- Format: 16-bit Linear PCM WAV
- Sampling Rate: Exactly 16,000 Hz
- Channels: Mono (auto-downmixes stereo if needed)
- Duration: 2.5s - 15.0s
- Energy Floor: RMS >= 0.01 (-40 dBFS)
- Silence Ceiling: <= 65% silent frames
- Clipping Ceiling: <= 1.0% clipped samples
"""

import io
import logging
from dataclasses import dataclass
from typing import Optional, Tuple

import numpy as np
import soundfile as sf

from app.services.speaker_recognition.exceptions import (
    AudioPreprocessingError,
    AudioQualityError,
)

logger = logging.getLogger("echoshield.ai.audio_preprocessor")

EXPECTED_SAMPLE_RATE = 16000
MIN_DURATION_SECONDS = 2.5
MAX_DURATION_SECONDS = 15.0
MIN_RMS_ENERGY = 0.01
MAX_SILENCE_RATIO = 0.65
MAX_CLIPPING_RATIO = 0.01


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
    status_label: str
    error_message: Optional[str] = None


class AudioPreprocessor:
    """
    Decodes and validates raw audio bytes prior to embedding extraction.
    """

    def preprocess_and_validate(
        self, audio_bytes: bytes, filename: str = "audio.wav"
    ) -> Tuple[np.ndarray, AudioQualityReport]:
        """
        Decodes WAV audio bytes into a float32 numpy array, checking acoustic quality.
        Raises AudioPreprocessingError on corrupt data or format mismatch.
        Raises AudioQualityError on poor acoustic quality.
        """
        if not audio_bytes or len(audio_bytes) < 44:
            raise AudioPreprocessingError("Audio file is empty or missing standard 44-byte WAV header.")

        # Decode using SoundFile
        try:
            with io.BytesIO(audio_bytes) as bio:
                with sf.SoundFile(bio) as sound_file:
                    sample_rate = sound_file.samplerate
                    channels = sound_file.channels
                    frames = sound_file.frames
                    duration = frames / float(sample_rate) if sample_rate > 0 else 0.0
                    audio_data = sound_file.read(dtype="float32")
        except Exception as e:
            raise AudioPreprocessingError(f"Corrupted or unsupported audio format: {str(e)}")

        # Convert stereo to mono
        if channels > 1:
            audio_data = np.mean(audio_data, axis=1)
            channels = 1

        # Check sample rate
        if sample_rate != EXPECTED_SAMPLE_RATE:
            raise AudioPreprocessingError(
                f"Invalid sample rate: {sample_rate}Hz. SpeechBrain ECAPA-TDNN requires exactly 16,000Hz PCM audio."
            )

        # Check duration
        if duration < MIN_DURATION_SECONDS:
            raise AudioQualityError(
                f"Recording too short ({duration:.1f}s). Minimum required duration is {MIN_DURATION_SECONDS}s.",
                quality_detail="RECORDING_TOO_SHORT"
            )

        if duration > MAX_DURATION_SECONDS:
            raise AudioQualityError(
                f"Recording too long ({duration:.1f}s). Maximum allowed duration is {MAX_DURATION_SECONDS}s.",
                quality_detail="RECORDING_TOO_LONG"
            )

        # RMS Energy check
        rms = float(np.sqrt(np.mean(np.square(audio_data))))
        if rms < MIN_RMS_ENERGY:
            raise AudioQualityError(
                "Microphone input too low. Audio is near silent or microphone was muted.",
                quality_detail="AUDIO_TOO_SILENT"
            )

        # Silence ratio using frame energy VAD (30ms frames)
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
            raise AudioQualityError(
                f"Excessive silence detected ({silence_ratio * 100:.0f}%). Please speak clearly into the microphone.",
                quality_detail="EXCESSIVE_SILENCE"
            )

        # Clipping detection
        clipping_samples = np.sum(np.abs(audio_data) >= 0.99)
        clipping_ratio = float(clipping_samples / len(audio_data))
        if clipping_ratio > MAX_CLIPPING_RATIO:
            raise AudioQualityError(
                f"Audio clipping detected ({clipping_ratio * 100:.1f}%). High distortion levels.",
                quality_detail="AUDIO_CLIPPING"
            )

        # SNR calculation in dB
        noise_floor = np.percentile(np.abs(audio_data), 10) + 1e-6
        signal_peak = np.percentile(np.abs(audio_data), 95) + 1e-6
        snr_db = float(20 * np.log10(signal_peak / noise_floor))

        report = AudioQualityReport(
            is_valid=True,
            duration_seconds=round(duration, 2),
            sample_rate=sample_rate,
            channels=channels,
            rms_energy=round(rms, 4),
            silence_ratio=round(silence_ratio, 3),
            clipping_ratio=round(clipping_ratio, 4),
            snr_db=round(max(0.0, snr_db), 1),
            status_label="acceptable",
            error_message=None
        )

        return audio_data, report


# Global instance
audio_preprocessor = AudioPreprocessor()
