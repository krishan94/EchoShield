# EchoShield — Phase 4: Consent-Based Voice Enrollment System Implementation Report

**Milestone**: Phase 4 – Consent-Based Voice Enrollment System  
**Status**: **COMPLETED**  
**Date**: 2026-10-31  
**Project**: EchoShield — Native Android Voice Authentication & Deepfake Scam Defense System for Families  

---

## 1. Executive Summary

Phase 4 delivers the complete, production-grade **Consent-Based Voice Enrollment System** spanning both the native Kotlin/Jetpack Compose Android application and the Python FastAPI backend, integrated with genuine **SpeechBrain ECAPA-TDNN** deep learning embeddings and **AES-256-GCM biometric encryption at rest**.

All strict implementation rules and constraints were honored:
- **Phase 4 Only**: No Phase 5 (speaker similarity matching), Phase 6 (anti-spoofing / deepfake detection), or WebRTC calling (Phase 7/8) were implemented.
- **Zero Fake AI & Zero Mock Embeddings**: Voice embeddings are extracted using the genuine pretrained `speechbrain/spkrec-ecapa-voxceleb` model loaded locally on Python 3.13 (`shape: torch.Size([1, 1, 192])`).
- **Zero Fake Waveforms**: Waveform dynamics in the Android studio are driven by live amplitude samples captured from the hardware microphone using Android's low-latency `AudioRecord` API.
- **Biometric Security & Privacy**: Explicit informed consent is mandatory prior to recording. Embeddings are encrypted at rest with AES-256-GCM (with unique 96-bit nonces). Temporary raw audio files are purged immediately from disk after feature extraction.
- **100% Hermetic Tests Passing**: 27 backend tests (`pytest -v`) and 41 Android unit tests (`.\gradlew.bat testDebugUnitTest`) pass cleanly.
- **Installable APK**: Built successfully (`app-debug.apk`, 24 MB).

---

## 2. Voice Enrollment User Flow (Screen Integration)

The complete 12-step flow has been implemented and connected in `EchoNavGraph`:

1. **Family Circle / Dashboard Profile**: User initiates biometric setup.
2. **Screen 12: Voice Enrollment Intro (`VoiceEnrollmentIntroScreen.kt`)**: Displays biometric disclosure disclaimers (why data is collected, 192-dim vector extraction, AES-256-GCM encryption, optional participation, right to delete).
3. **Consent Agreement Dialog (`EchoConsentDialog`)**: User must explicitly click "I Agree & Consent". Triggers `POST /api/v1/voice/consent` with timestamp and version `1.0`.
4. **Screen 13: Microphone Permission (`MicrophonePermissionScreen.kt`)**: Requests Android runtime `RECORD_AUDIO` permission. Handles permission granted, permission denied, and permanent denial with deep-link redirection to Android Application Details Settings.
5. **Recording Instructions & Prompt Studio**: Presents 3 varied speech prompts to prevent identical repeated recordings.
6. **Screen 14: Voice Recording Studio (`VoiceRecordingStudioScreen.kt`)**: Captures 16kHz 16-bit Mono PCM audio via `AudioRecordManager`. Streams real microphone amplitudes to the Compose waveform visualizer. Real recording timer counts elapsed seconds.
7. **Client-Side Acoustic Validation (`AudioQualityValidator.kt`)**: Validates WAV header, duration ($\ge 2.5\text{s}, \le 15.0\text{s}$), RMS volume floor, background silence ratio ($< 65\%$), and audio clipping ($< 1.5\%$).
8. **Screen 15: Recording Review (`RecordingReviewScreen.kt`)**: Native audio playback via `AudioPlaybackManager` (MediaPlayer) with active waveform animation, signal-to-noise ratio (SNR in dB), duration, and acceptance controls.
9. **Multi-Sample Progression**: Guides user through Sample 1, Sample 2, and Sample 3. Users can re-record any sample prior to final submission.
10. **Backend Upload & Embedding Generation (`POST /api/v1/voice/enroll`)**: Uploads the 3 distinct WAV samples over HTTP with Bearer token authentication. Backend re-validates acoustic quality, extracts 192-dim vectors via ECAPA-TDNN, computes centroid embedding, calculates quality score, encrypts with AES-256-GCM, and persists to Firestore.
11. **Screen 16: Voice Profile Status (`VoiceProfileStatusScreen.kt`)**: Displays activated Green Shield certificate, optimal quality percentage (e.g. 98%), vector dimension (192-dim), model details, re-enroll option, and profile deletion dialog.
12. **Cryptographic Purge (`DELETE /api/v1/voice/profile`)**: Permanently wipes the encrypted vector from `voice_profiles`, revokes consent in `voice_consents`, and resets user enrollment flag to `false`.

---

## 3. Pretrained AI Model & Biometric Security Specification

### 3.1 Model Evaluation & Verification
- **Model Architecture**: ECAPA-TDNN (Emphasized Channel Attention, Propagation and Aggregation in TDNN).
- **Model Checkpoint**: `speechbrain/spkrec-ecapa-voxceleb` (trained on VoxCeleb 1 & 2).
- **License**: **Apache License 2.0** (Open Source, verified permissive for commercial and private use).
- **Local Cache Path**: `models/ecapa_tdnn/` (downloaded weights: `embedding_model.ckpt` [83.3 MB], `classifier.ckpt` [5.5 MB], `hyperparams.yaml`).
- **Framework Compatibility**: Verified with PyTorch 2.15.0+cpu and SpeechBrain 1.1.2 on Python 3.13 Windows x64.
- **Input Requirements**:
  - Sample Rate: Exactly 16,000 Hz.
  - Channels: 1 (Mono).
  - Audio Format: 16-bit Linear PCM.
  - Duration: $2.5\text{s} \le t \le 15.0\text{s}$.
- **Output Tensor**: 192-dimensional speaker embedding vector ($L_2$-normalized).

### 3.2 Centroid Multi-Sample Aggregation
For enrollment samples $s_1, s_2, s_3$:
1. Individual embeddings: $e_1, e_2, e_3 \in \mathbb{R}^{192}$, where $\|e_i\|_2 = 1.0$.
2. Centroid calculation: $e_{\text{raw}} = \frac{1}{3} \sum_{i=1}^3 e_i$.
3. Length-normalized profile signature: $e_{\text{profile}} = \frac{e_{\text{raw}}}{\|e_{\text{raw}}\|_2}$.
4. Acoustic Quality Metric: Derived from pairwise cosine consistency $\cos(e_i, e_j)$ and estimated SNR.

### 3.3 Biometric Encryption at Rest (AES-256-GCM)
- Embeddings are serialized to IEEE-754 32-bit float bytes ($192 \times 4 = 768$ bytes).
- Encrypted using **AES-256-GCM** via Python's `cryptography` library (`AESGCM`).
- A unique 96-bit (12-byte) cryptographically secure random nonce (`os.urandom(12)`) is generated per encryption operation.
- Authenticated with associated data `b"EchoShieldVoiceEmbeddingV1"`.
- Output stored as base64-encoded ciphertext, nonce, algorithm identifier, and vector dimension.
- Decryption roundtrip tests verify exact bit-for-bit numerical parity ($< 10^{-6}$ tolerance).

---

## 4. Backend Endpoints (FastAPI)

All endpoints are mounted under `/api/v1/voice` and require Firebase ID token Bearer authentication:

| Method | Endpoint | Description | Security & Validation |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/voice/consent` | Records informed biometric consent. | Validates status ("granted" / "revoked") and agreement version ("1.0"). Stores audit timestamp. |
| `POST` | `/api/v1/voice/enroll` | Enrolls biometric voice profile. | Requires consent. Accepts 3 multipart WAV files ($< 10\text{MB}$ each). Enforces 16kHz mono audio, duration, RMS, silence, and clipping checks. Extracts 192-dim ECAPA-TDNN embeddings, encrypts with AES-256-GCM, and purges temp audio. |
| `GET` | `/api/v1/voice/profile` | Retrieves voice certificate metadata. | Never exposes raw vectors or encryption keys. Returns enrollment status, quality score, and model metadata. |
| `PUT` | `/api/v1/voice/profile` | Re-enrolls voice profile. | Accepts 3 new WAV samples and updates encrypted baseline signature. |
| `DELETE` | `/api/v1/voice/profile` | Purges biometric profile and embeddings. | Cryptographically erases stored ciphertext, revokes consent, and sets `users.is_voice_enrolled = false`. |

---

## 5. Firestore Database Schema & Security Rules

### 5.1 `voice_consents` Collection
- **Document ID**: User UID (`uid`)
- **Fields**:
  - `user_id` (string): Firebase UID.
  - `consent_status` (string): `"granted"` or `"revoked"`.
  - `consent_version` (string): `"1.0"`.
  - `consent_timestamp` (string): ISO-8601 audit timestamp.
  - `updated_at` (string): ISO-8601 update timestamp.

### 5.2 `voice_profiles` Collection
- **Document ID**: User UID (`uid`)
- **Fields**:
  - `user_id` (string): Firebase UID.
  - `enrollment_status` (string): `"enrolled"`.
  - `is_voice_enrolled` (boolean): `true`.
  - `voice_quality_score` (float): Derived acoustic score (e.g. 0.96).
  - `sample_count` (integer): `3`.
  - `embedding_model` (string): `"SpeechBrain ECAPA-TDNN"`.
  - `model_version` (string): `"voxceleb-v1.1"`.
  - `embedding_dim` (integer): `192`.
  - `encrypted_embedding` (map):
    - `ciphertext` (string): Base64-encoded AES-256-GCM ciphertext + 16-byte tag.
    - `nonce` (string): Base64-encoded 96-bit GCM nonce.
    - `algorithm` (string): `"AES-256-GCM"`.
  - `enrolled_at` (string): ISO-8601 creation timestamp.
  - `updated_at` (string): ISO-8601 update timestamp.

### 5.3 `firestore.rules` Security Constraints
```javascript
// Biometric Voice Consents collection:
match /voice_consents/{userId} {
  allow read, create, update: if isAuthenticated() && request.auth.uid == userId;
  allow delete: if false; // Consents are updated to 'revoked', never deleted for compliance auditing
}

// Biometric Voice Profiles collection:
match /voice_profiles/{userId} {
  allow read, create, update, delete: if isAuthenticated() && request.auth.uid == userId;
}
```
*Embeddings are strictly private to the authenticated account owner and cannot be accessed by family members or third parties.*

---

## 6. Test Suite Execution & Results

### 6.1 Backend Test Suite (Pytest)
Executed via `pytest -v` across all modules:
```
tests/unit/test_backend_auth.py: 10 tests PASSED
tests/unit/test_backend_family.py: 7 tests PASSED
tests/unit/test_backend_voice.py: 10 tests PASSED
======================= 27 passed, 4 warnings in 8.58s ========================
```
**Voice test coverage**:
- `test_consent_recording_granted`: Consent recording and status check.
- `test_consent_recording_invalid_status`: Rejection of malformed consent statuses.
- `test_enrollment_rejected_when_consent_missing`: HTTP 403 Forbidden when enrolling without prior consent.
- `test_enrollment_rejected_unsupported_format`: HTTP 415 when non-WAV formats are submitted.
- `test_enrollment_rejected_empty_audio_file`: HTTP 400 when empty files are sent.
- `test_enrollment_rejected_corrupted_wav`: HTTP 422 when corrupt WAV headers are sent.
- `test_enrollment_rejected_audio_too_short`: HTTP 422 when audio duration is $< 2.5\text{s}$.
- `test_enrollment_rejected_audio_near_silent`: HTTP 422 when audio is muted or near silent.
- `test_valid_voice_enrollment_and_profile_lifecycle`: Full 3-sample upload, real ECAPA-TDNN embedding extraction, profile retrieval without vector exposure, and cryptographic deletion.
- `test_aes_256_gcm_biometric_encryption_roundtrip`: Exact numerical vector recovery after encryption and decryption.

### 6.2 Android Test Suite (JUnit / Robolectric / Coroutines)
Executed via `.\gradlew.bat testDebugUnitTest`:
```
BUILD SUCCESSFUL in 28s
23 actionable tasks: 2 executed, 21 up-to-date
```
- **Total Tests Passing**: **41 tests** (`VoiceUnitTest`, `FamilyUnitTest`, `AuthUnitTest`, `EchoShieldUnitTest`).
- **Voice Unit Tests**: Initial state, consent granted/failed, permission transitions, 3-sample progression, invalid sample rejection, final sample upload trigger, profile deletion, and `AudioQualityValidator` header/duration validation.

### 6.3 Android Debug APK Assembly
Executed via `.\gradlew.bat assembleDebug`:
```
BUILD SUCCESSFUL in 28s
36 actionable tasks: 4 executed, 32 up-to-date
```
- **Output Artifact**: `android-app/app/build/outputs/apk/debug/app-debug.apk` (23,995,014 bytes).

---

## 7. Modified & Created Files Inventory

### AI Engine & Machine Learning:
1. `ai-engine/pipelines/speaker_embedding.py` — SpeechBrain ECAPA-TDNN feature extractor, audio quality inspector, and AES-256-GCM encryptor/decryptor.
2. `models/ecapa_tdnn/` — Pretrained model weights cache (`embedding_model.ckpt`, `classifier.ckpt`, `hyperparams.yaml`).

### Backend (FastAPI):
3. `backend/app/enrollment/models.py` — Pydantic schemas for consent, enrollment, and profile responses.
4. `backend/app/enrollment/service.py` — Business logic, Firestore persistence, and error handling.
5. `backend/app/enrollment/router.py` — FastAPI endpoints under `/api/v1/voice`.
6. `backend/app/enrollment/__init__.py` — Package exports.
7. `backend/app/main.py` — Mounted enrollment router.
8. `firestore.rules` — Added security rules for `voice_consents` and `voice_profiles`.
9. `tests/unit/test_backend_voice.py` — Comprehensive backend voice enrollment test suite.

### Android Application (Kotlin / Jetpack Compose):
10. `android-app/app/src/main/java/com/echoshield/voice/audio/AudioRecordManager.kt` — Low-latency 16kHz PCM audio recorder streaming live microphone amplitudes.
11. `android-app/app/src/main/java/com/echoshield/voice/audio/AudioQualityValidator.kt` — Client-side WAV duration, silence, volume, and clipping inspector.
12. `android-app/app/src/main/java/com/echoshield/voice/audio/AudioPlaybackManager.kt` — MediaPlayer wrapper for sample review with progress tracking.
13. `android-app/app/src/main/java/com/echoshield/domain/model/VoiceModels.kt` — Domain models (`VoiceConsentRecord`, `VoiceProfileInfo`, `RecordedSample`, `VoiceEnrollmentResult`).
14. `android-app/app/src/main/java/com/echoshield/domain/repository/VoiceRepository.kt` — Voice repository interface.
15. `android-app/app/src/main/java/com/echoshield/data/repository/VoiceRepositoryImpl.kt` — Voice repository implementation.
16. `android-app/app/src/main/java/com/echoshield/data/remote/VoiceRemoteDataSource.kt` — Dual-mode remote data source (Multipart HTTP + Firestore SDK fallback).
17. `android-app/app/src/main/java/com/echoshield/core/di/AppContainer.kt` — Dependency injection wiring for `VoiceRepository`.
18. `android-app/app/src/main/java/com/echoshield/voice/VoiceViewModel.kt` — Voice enrollment state machine ViewModel.
19. `android-app/app/src/main/java/com/echoshield/voice/VoiceEnrollmentIntroScreen.kt` — Disclosure review and consent agreement.
20. `android-app/app/src/main/java/com/echoshield/voice/MicrophonePermissionScreen.kt` — Runtime `RECORD_AUDIO` permission request and settings redirection.
21. `android-app/app/src/main/java/com/echoshield/voice/VoiceRecordingStudioScreen.kt` — Guided 3-prompt studio with live mic-driven waveform.
22. `android-app/app/src/main/java/com/echoshield/voice/RecordingReviewScreen.kt` — Sample playback and acoustic quality assessment.
23. `android-app/app/src/main/java/com/echoshield/voice/VoiceProfileStatusScreen.kt` — Active certificate status, quality metrics, and secure deletion dialog.
24. `android-app/app/src/main/java/com/echoshield/family/FamilyMemberProfileScreen.kt` — Removed `PreviewData` fallback, added empty state.
25. `android-app/app/src/main/java/com/echoshield/navigation/EchoNavGraph.kt` — Connected `voiceViewModel` and wired the 12-step navigation flow.
26. `android-app/app/src/test/java/com/echoshield/voice/VoiceUnitTest.kt` — Complete Android voice enrollment unit test suite.

### Documentation & Tracking:
27. `.gitignore` — Added `models/` directory.
28. `docs/PHASE_4_REPORT.md` — This milestone report.
29. `docs/PHASE_TRACKING.md` — Updated master roadmap matrix.

---

## 8. Verification of Development Rules & Constraints

- [x] **No Phase 5 Implemented**: Speaker similarity scoring between incoming audio and registered profiles was NOT implemented (deferred to Phase 5).
- [x] **No Phase 6 Implemented**: Deepfake anti-spoofing was NOT implemented (deferred to Phase 6).
- [x] **No WebRTC Implemented**: WebRTC peer connections were NOT touched.
- [x] **Zero Fake AI**: SpeechBrain ECAPA-TDNN was loaded and verified.
- [x] **Zero Fake Waveforms**: Waveform dynamics are driven by live PCM microphone amplitudes.
- [x] **Zero Fake Recordings**: Only real WAV captures or explicitly synthesized test sine waves are processed.
- [x] **Zero Simulated Scores**: All quality scores and embeddings are computed from audio signals.
- [x] **Zero Regression**: All Phase 1, Phase 2, and Phase 3 features remain intact and tested.

---

## 9. Next Steps

Phase 4 is complete, verified, and ready. Awaiting user review and authorization before proceeding to **Phase 5 (Pretrained Speaker Recognition & Similarity Matching)**.
