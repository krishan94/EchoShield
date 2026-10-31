# EchoShield — Phase 5: Real AI Speaker Recognition & Voice Identity Matching Report

**Milestone**: Phase 5 – Real AI Speaker Recognition & Voice Identity Matching  
**Status**: **COMPLETED**  
**Date**: 2026-11-01  
**Project**: EchoShield — Native Android Voice Authentication & Deepfake Scam Defense System for Families  

---

## 1. Executive Summary

Phase 5 delivers genuine **AI Speaker Recognition and Voice Identity Verification** for EchoShield. Building directly upon the consent-based biometric voice enrollment foundation established in Phase 4, Phase 5 introduces an end-to-end speaker verification engine connecting native Android Jetpack Compose recording and review studios with a modular, high-performance Python FastAPI verification service powered by the **SpeechBrain ECAPA-TDNN** deep learning architecture (`speechbrain/spkrec-ecapa-voxceleb`, Apache 2.0 license) and **AES-256-GCM** biometric decryption at rest.

Strict architectural guardrails were rigorously enforced:
- **Phase 5 Only**: Zero synthetic voice / deepfake detection (Phase 6), anti-spoofing models, or WebRTC call tapping were implemented. Phase boundaries were strictly respected.
- **Zero Fake AI & Zero Random Scores**: Every verification score is computed from genuine 192-dimensional embeddings extracted by the ECAPA-TDNN neural network and evaluated via mathematical cosine similarity against decrypted enrolled centroids.
- **Zero Fake Waveforms**: Microphone dynamics in the Android studio are powered live by hardware amplitude samples captured through Android's low-latency `AudioRecord` API.
- **Calibrated Operating System**: Implemented a 5-tier calibrated decision engine: `VOICE_MATCHED`, `NO_MATCH`, `INCONCLUSIVE`, `AUDIO_QUALITY_FAILED`, `PROFILE_NOT_ENROLLED`.
- **Privacy & Security First**: Strict server-side family relationship authorization, mandatory active biometric consent enforcement, zero raw audio retention, cryptographic vector confidentiality, and immutable verification history auditing.
- **100% Hermetic Tests Passing**: **44 backend tests** (`pytest -v`) and **49 Android unit tests** (`.\gradlew testDebugUnitTest`) pass cleanly with zero failures.
- **Installable APK**: Built successfully (`app-debug.apk`, 24 MB).

---

## 2. Features Implemented

### 2.1 Backend Speaker Recognition Engine (`backend/app/services/speaker_recognition/`)
1. **Modular Architecture**:
   - `model_loader.py`: Singleton memory caching of the pretrained SpeechBrain ECAPA-TDNN model with device dispatch (CPU/CUDA) and tensor dimension validation.
   - `audio_preprocessor.py`: Decodes WAV streams, checks sample rate (16,000 Hz PCM), downmixes stereo to mono, validates duration ($2.5\text{s} \le t \le 15.0\text{s}$), enforces RMS energy floor ($\ge 0.01$), detects silence ratio ($< 65\%$), detects clipping ($< 1\%$), and estimates SNR in dB.
   - `embedding_service.py`: Runs forward inference in PyTorch evaluation mode (`torch.no_grad()`), extracts 192-dimensional tensors, validates finiteness (no NaNs/Infs), and applies length normalization ($L_2$).
   - `similarity_service.py`: Computes genuine cosine similarity between $L_2$-normalized test and reference vectors with bounds checking ($[-1.0, 1.0]$). Supports multi-sample centroid and pairwise comparisons.
   - `decision_engine.py`: Evaluates similarity scores and acoustic noise against calibrated operating bands (`T_match = 0.72`, `T_non_match = 0.58`, Uncertain region $[0.58, 0.72)$).
   - `verification_service.py`: High-level coordinator enforcing family circle authorization, consent verification, AES-256-GCM biometric decryption, audit logging, and temporary audio purging.
   - `exceptions.py`: Granular domain exceptions (`UnauthorizedAccessError`, `BiometricConsentMissingError`, `ProfileNotEnrolledError`, `AudioQualityError`, `AudioPreprocessingError`, `IncompatibleEmbeddingError`, `ModelLoadError`).

### 2.2 Backend APIs (`backend/app/speaker/router.py`)
Mounted under `/api/v1/voice`:
- `POST /api/v1/voice/verify`: Multi-part endpoint accepting `audio_file` (WAV) and `member_id`. Returns calibrated `VerificationResponse`.
- `GET /api/v1/voice/verification-history`: Returns chronological verification audit records for the requesting user (no raw audio, no vectors).
- `DELETE /api/v1/voice/verification-history`: Clears audit records for the authenticated user.

### 2.3 Android Application (`android-app/`)
1. **Domain & Data Layer**:
   - Added `VoiceVerificationResult`, `VerificationOutcome`, and `VerificationHistoryRecord` to `com.echoshield.domain.model.VoiceModels.kt`.
   - Extended `VoiceRepository` and `VoiceRepositoryImpl` with `verifyVoiceSample()`, `getVerificationHistory()`, and `clearVerificationHistory()`.
   - Extended `VoiceRemoteDataSource` with multipart HTTP requests and Firestore dual-mode fallbacks.
2. **ViewModel Layer**:
   - Implemented `VoiceVerificationViewModel` in `com.echoshield.verification`, managing family member selection, recording states, live audio waveforms, review playback, backend verification dispatch, and audit logs.
3. **Jetpack Compose UI Screens**:
   - `VoiceVerificationScreen.kt`: Complete verification interface with family member picker, enrollment badge, microphone permission gate, real-time waveform studio, audio review player, AI radar loading state, and calibrated result display.
   - `VerificationHistoryScreen.kt`: Audit trail viewing screen with outcome badges, similarity scores, acoustic metadata, and purge dialog.
   - Connected "Verify Voice Identity" action button directly on `FamilyMemberProfileScreen.kt`.
   - Wired routes `Screen.VoiceVerification` and `Screen.VerificationHistory` into `EchoNavGraph.kt`.

---

## 3. Pretrained AI Model & Inference Pipeline

### 3.1 Model Specification
- **Model Architecture**: ECAPA-TDNN (Emphasized Channel Attention, Propagation and Aggregation in TDNN).
- **Model Checkpoint**: `speechbrain/spkrec-ecapa-voxceleb` (trained on VoxCeleb 1 & 2).
- **License**: **Apache License 2.0** (Open Source, verified permissive for commercial and private use).
- **Local Cache Path**: `models/ecapa_tdnn/` (`embedding_model.ckpt` [83.3 MB], `classifier.ckpt` [5.5 MB], `hyperparams.yaml`).
- **Framework Compatibility**: Verified with PyTorch 2.15.0+cpu and SpeechBrain 1.1.2 on Python 3.13 Windows x64.
- **Input Requirements**:
  - Sample Rate: Exactly 16,000 Hz.
  - Channels: 1 (Mono).
  - Format: 16-bit Linear PCM.
  - Duration: $2.5\text{s} \le t \le 15.0\text{s}$.
- **Output Tensor**: 192-dimensional speaker embedding vector ($L_2$-normalized).

### 3.2 Feature Preprocessing Pipeline
1. **RIFF WAV Header Verification**: Validates 44-byte canonical WAV header and PCM encoding.
2. **Channel Downmixing**: If stereo audio is detected, channels are averaged: $x_{\text{mono}}[n] = \frac{x_{\text{left}}[n] + x_{\text{right}}[n]}{2}$.
3. **RMS Energy Check**: Computes root-mean-square amplitude $\text{RMS} = \sqrt{\frac{1}{N} \sum_{n=1}^N x[n]^2}$. Rejects audio if $\text{RMS} < 0.01$ (microphone muted or silence floor).
4. **Energy VAD (Silence Ratio)**: Computes 30ms windowed frame energies. If silent frames exceed $65\%$, rejects with `EXCESSIVE_SILENCE`.
5. **Clipping Ratio**: Counts samples where $|x[n]| \ge 0.99$. If clipping exceeds $1.0\%$, rejects with `AUDIO_CLIPPING`.
6. **SNR Estimation**: Computes dynamic range between 95th percentile peak and 10th percentile noise floor in dB: $\text{SNR} = 20 \log_{10} \frac{P_{95}}{P_{10}}$.

---

## 4. Voice Comparison & Calibrated Decision System

### 4.1 Embedding Comparison Method
Given test embedding $e_{\text{test}} \in \mathbb{R}^{192}$ and enrolled reference centroid $e_{\text{enrolled}} \in \mathbb{R}^{192}$, where both vectors are $L_2$-normalized ($\|e\|_2 = 1.0$):
$$\text{Cosine Similarity } S(e_{\text{test}}, e_{\text{enrolled}}) = \frac{e_{\text{test}} \cdot e_{\text{enrolled}}}{\|e_{\text{test}}\|_2 \|e_{\text{enrolled}}\|_2} = \sum_{k=1}^{192} e_{\text{test}, k} \cdot e_{\text{enrolled}, k}$$

The result $S \in [-1.0, 1.0]$ is clamped to protect against tiny floating-point precision drift and rounded to 4 decimal places.

### 4.2 Calibrated Operating Thresholds
The raw score is a geometric measurement of vector proximity in the 192-dimensional VoxCeleb feature space, **not a mathematical percentage probability of identity**.

Based on empirical ECAPA-TDNN VoxCeleb cosine similarity distributions:

| Decision Outcome | Condition / Score Range | Operating Meaning & Guidance |
| :--- | :---: | :--- |
| **`VOICE_MATCHED`** | $S \ge 0.72$ and $\text{SNR} \ge 10\text{dB}$ | Significant acoustic similarity to enrolled baseline. Low False Acceptance Rate (FAR). Does not guarantee absolute identity. |
| **`NO_MATCH`** | $S < 0.58$ | Substantial acoustic divergence from enrolled baseline. Acoustic features consistent with a distinct individual. Does not automatically imply fraud. |
| **`INCONCLUSIVE`** | $0.58 \le S < 0.72$ OR $\text{SNR} < 10\text{dB}$ | Ambiguous separation or excessive ambient noise. Biometric evidence cannot definitively assert or refute identity. User is advised to re-record in quieter conditions. |
| **`AUDIO_QUALITY_FAILED`** | Failed acoustic validation | Recording too short ($< 2.5\text{s}$), muted/silent, high clipping distortion, or corrupted WAV stream. |
| **`PROFILE_NOT_ENROLLED`** | No active voice profile | Target member has not enrolled or revoked consent. Reference signature absent. |

### 4.3 Validation Dataset & Experimental Notice
- **Evaluation Status**: **EXPERIMENTAL**.
- The decision boundaries ($0.72$ match, $0.58$ non-match) are derived from published SpeechBrain VoxCeleb benchmark distributions under controlled acoustic conditions.
- True production Equal Error Rate (EER), FAR, and FRR calibration across diverse real-world mobile microphones, varied vocal effort, illnesses, and family age groups requires empirical field trials with consented family cohorts.
- Accordingly, when borderline scores occur or noise is elevated, the system strictly outputs **`INCONCLUSIVE`** rather than attempting to guess or simulate false certainty.

---

## 5. Security, Authorization & Privacy Architecture

1. **Server-Side Identity Authorization**:
   - Client-supplied identities are never trusted. The requesting user UID is derived exclusively from the cryptographically verified Firebase ID token.
   - For family member verifications, the backend queries `family_relationships` to confirm that the requesting user and target member share an active mutual connection. Unauthorized requests are rejected with `HTTP 403 Forbidden`.
2. **Biometric Consent Enforcement**:
   - The backend checks `voice_consents` for the target member. If consent is absent or revoked, verification is blocked (`HTTP 403 Forbidden`).
3. **Biometric Encryption at Rest**:
   - Stored embeddings are encrypted with **AES-256-GCM** using unique 96-bit nonces.
   - Embeddings are decrypted only in backend memory for comparison.
   - Biometric vectors and encryption keys are **never** exposed in API responses or written to application logs.
4. **Temporary Audio Hygiene**:
   - Audio files uploaded for verification are processed in memory and purged immediately after feature extraction. No permanent raw audio files are stored in Firestore or cloud storage.
   - Local temporary recordings on Android are securely deleted immediately following upload or review cancellation.
5. **Verification Audit Trail**:
   - Stored in `verification_history` collection.
   - Contains only forensic metadata: verification ID, timestamp, member ID, result category, similarity score, SNR, and model version. Raw audio and biometric vectors are excluded.
   - Only the verifier can query or clear their history records.

---

## 6. Firestore Database & Security Rules

### 6.1 `verification_history` Collection
- **Document ID**: `verif_<uuid>`
- **Fields**:
  - `id` (string): Unique verification identifier.
  - `user_id` (string): Firebase UID of the verifying user.
  - `member_id` (string): Firebase UID of the evaluated family member.
  - `member_name` (string): Display name of the evaluated family member.
  - `result` (string): Verdict (`VOICE_MATCHED`, `NO_MATCH`, `INCONCLUSIVE`, `AUDIO_QUALITY_FAILED`, `PROFILE_NOT_ENROLLED`).
  - `similarity_score` (float / null): Raw cosine similarity score.
  - `match_threshold` (float): Threshold used (0.72).
  - `confidence_band` (string): `"high_match"`, `"non_match"`, `"uncertain_band"`, `"noise_degraded"`, `"quality_failed"`.
  - `audio_quality_status` (string): `"acceptable"` or `"rejected"`.
  - `duration_seconds` (float): Sample duration.
  - `snr_db` (float): Signal-to-noise ratio.
  - `model_name` (string): `"SpeechBrain ECAPA-TDNN"`.
  - `model_version` (string): `"voxceleb-v1.1"`.
  - `verified_at` (string): ISO-8601 UTC timestamp.

### 6.2 Security Rules Update (`firestore.rules`)
```javascript
// Verification History collection:
// - Forensic audit trail of voice verification events (no raw audio or biometric embeddings)
// - Only the verifier can read and clear their own audit log
match /verification_history/{historyId} {
  allow read, delete: if isAuthenticated() && resource.data.user_id == request.auth.uid;
  allow create: if isAuthenticated() && request.resource.data.user_id == request.auth.uid;
  allow update: if false; // History records are immutable audit logs
}
```

---

## 7. Automated Test Results

### 7.1 Backend Test Suite (Pytest)
Command: `pytest -v` across all 4 unit test modules:
```
tests/unit/test_backend_auth.py:     10 passed
tests/unit/test_backend_family.py:    7 passed
tests/unit/test_backend_speaker.py:  17 passed
tests/unit/test_backend_voice.py:    10 passed
======================= 44 passed, 5 warnings in 12.99s =======================
```
**Phase 5 Specific Tests (`test_backend_speaker.py`)**:
- `test_cosine_similarity_identical_vectors`: Verified $S = 1.0000$ for identical vectors.
- `test_cosine_similarity_orthogonal_vectors`: Verified $S \approx 0.0$ for orthogonal vectors.
- `test_cosine_similarity_dimension_mismatch`: Rejection of mismatched embedding dimensions.
- `test_calibrated_decision_bands`: Verified `VOICE_MATCHED` ($\ge 0.72$), `NO_MATCH` ($< 0.58$), `INCONCLUSIVE` ($[0.58, 0.72)$), and noise degradation downgrade.
- `test_verify_enrolled_voice_match_self`: End-to-end self-verification returning `VOICE_MATCHED` with $S \ge 0.72$.
- `test_verify_different_speaker_no_match`: Different acoustic frequency returning non-match ($S < 0.72$).
- `test_verify_family_member_authorized`: Verified linked family member verification.
- `test_verify_unauthorized_non_family_member`: HTTP 403 when verifying unrelated user.
- `test_verify_member_missing_consent`: HTTP 403 when consent is revoked.
- `test_verify_member_not_enrolled`: Returns `PROFILE_NOT_ENROLLED` for unenrolled family member.
- `test_verify_empty_audio_file`: HTTP 400 when empty audio file is uploaded.
- `test_verify_unsupported_audio_format`: HTTP 415 when non-WAV format is uploaded.
- `test_verify_corrupted_wav_file`: HTTP 422 when corrupted WAV headers are sent.
- `test_verify_audio_too_short`: Rejection of audio duration $< 2.5\text{s}$ (`AUDIO_QUALITY_FAILED`).
- `test_verify_audio_near_silent`: Rejection of near-silent audio (`AUDIO_QUALITY_FAILED`).
- `test_verify_requires_authentication`: HTTP 401 without valid Bearer token.
- `test_verification_history_lifecycle`: Log creation, history query without vector leakage, and audit clear.

### 7.2 Android Test Suite (JUnit / Robolectric)
Command: `.\gradlew.bat testDebugUnitTest`:
```
BUILD SUCCESSFUL in 33s
23 actionable tasks: 3 executed, 20 up-to-date
```
- **Total Tests Passing**: **49 tests** (100% pass rate).
- **New Verification Tests (`VoiceVerificationUnitTest.kt`)**: Initial state, family member switching, audio capture state, success verification transition to `VOICE_MATCHED`, service error handling, file state cleanup, history logging and clearing, and outcome enum parsing.

### 7.3 Android Debug APK Assembly
Command: `.\gradlew.bat assembleDebug`:
```
BUILD SUCCESSFUL in 26s
36 actionable tasks: 4 executed, 32 up-to-date
```
- **Output Artifact**: `android-app/app/build/outputs/apk/debug/app-debug.apk` (23,995,014 bytes).

---

## 8. Modified & Created Files Inventory

### Backend (Python / FastAPI):
1. `backend/app/services/speaker_recognition/__init__.py` — Package exports.
2. `backend/app/services/speaker_recognition/exceptions.py` — Domain exceptions.
3. `backend/app/services/speaker_recognition/model_loader.py` — ECAPA-TDNN singleton cache & device manager.
4. `backend/app/services/speaker_recognition/audio_preprocessor.py` — 16kHz PCM audio inspector and quality validator.
5. `backend/app/services/speaker_recognition/embedding_service.py` — 192-dim normalized vector extractor.
6. `backend/app/services/speaker_recognition/similarity_service.py` — Cosine similarity calculation & aggregation.
7. `backend/app/services/speaker_recognition/decision_engine.py` — Calibrated 5-tier verification decision system.
8. `backend/app/services/speaker_recognition/verification_service.py` — Authorization, AES-256-GCM decryption, and audit logging.
9. `backend/app/speaker/models.py` — Pydantic schemas for verification requests and history responses.
10. `backend/app/speaker/router.py` — FastAPI endpoints mounted under `/api/v1/voice`.
11. `backend/app/speaker/__init__.py` — Module exports.
12. `backend/app/main.py` — Mounted `speaker_router`.
13. `firestore.rules` — Added security rules for `verification_history`.
14. `tests/unit/test_backend_speaker.py` — 17 unit and integration tests.

### Android Application (Kotlin / Jetpack Compose):
15. `android-app/app/src/main/java/com/echoshield/domain/model/VoiceModels.kt` — Added `VoiceVerificationResult`, `VerificationOutcome`, `VerificationHistoryRecord`.
16. `android-app/app/src/main/java/com/echoshield/domain/repository/VoiceRepository.kt` — Added verification & history repository contracts.
17. `android-app/app/src/main/java/com/echoshield/data/remote/VoiceRemoteDataSource.kt` — Added HTTP multipart verification & history calls with Firestore fallback.
18. `android-app/app/src/main/java/com/echoshield/data/repository/VoiceRepositoryImpl.kt` — Implemented verification methods with automatic temp file cleanup.
19. `android-app/app/src/main/java/com/echoshield/verification/VoiceVerificationViewModel.kt` — Verification state machine & coroutine dispatcher.
20. `android-app/app/src/main/java/com/echoshield/verification/VoiceVerificationScreen.kt` — Native 16kHz Compose recording studio, review player, and result HUD.
21. `android-app/app/src/main/java/com/echoshield/verification/VerificationHistoryScreen.kt` — Forensic audit history screen.
22. `android-app/app/src/main/java/com/echoshield/family/FamilyMemberProfileScreen.kt` — Added "Verify Voice Identity" action trigger.
23. `android-app/app/src/main/java/com/echoshield/reports/CallHistoryScreen.kt` — Added action link to view voice verification audit log.
24. `android-app/app/src/main/java/com/echoshield/navigation/Screen.kt` — Added `VoiceVerification` and `VerificationHistory` destinations.
25. `android-app/app/src/main/java/com/echoshield/navigation/EchoNavGraph.kt` — Registered routes and ViewModel bindings.
26. `android-app/app/src/test/java/com/echoshield/voice/VoiceUnitTest.kt` — Updated fake repository with verification stubs.
27. `android-app/app/src/test/java/com/echoshield/verification/VoiceVerificationUnitTest.kt` — 8 unit tests for verification flow.

---

## 9. Manual Setup Steps for Users

1. **Deploy Updated Firestore Security Rules**:
   - If using the Firebase CLI:
     ```bash
     firebase deploy --only firestore:rules
     ```
   - Or paste the contents of [`firestore.rules`](file:///d:/EchoShield/firestore.rules) into the Firebase Console under **Firestore Database** > **Rules** and click **Publish**.
2. **Start Backend Server**:
   ```bash
   cd d:\EchoShield\backend
   .venv\Scripts\uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```
3. **Run Android App**:
   - Install the generated APK on an emulator or connected device:
     ```bash
     adb install -r android-app/app/build/outputs/apk/debug/app-debug.apk
     ```

---

## 10. Known Limitations & Strict Phase Boundaries

- **No Anti-Spoofing / Deepfake Detection in Phase 5**: Phase 5 measures biological speaker similarity only. Synthetic speech, text-to-speech (TTS), voice cloning, and replay attack detection belong strictly to **Phase 6: Audio Anti-Spoofing & Deepfake Detection**.
- **No Live In-Call WebRTC Tapping in Phase 5**: Audio is verified on recorded speech samples. Live in-call audio stream tapping and continuous verification belong to **Phase 7 & Phase 8**.
- **Threshold Calibration Labeled Experimental**: Decision thresholds are calibrated against published ECAPA-TDNN VoxCeleb distributions. Diverse real-world family testing across varying background acoustic noise is required before claiming production-certified certainty.

---

## 11. Remaining Work for Phase 6

Phase 6 will implement **Audio Anti-Spoofing & Deepfake Detection**:
1. Integration of a pretrained audio anti-spoofing / deepfake detection model (e.g. AASIST or RawNet2 trained on ASVspoof).
2. Bonafide vs. Spoof / Synthetic audio classification pipeline.
3. Feature extraction for synthetic artifacts (spectral phase incoherence, vocoder signatures, replay acoustic room impulses).
4. Calibrated spoof risk score computation ($[0.0, 1.0]$).
5. Anti-spoofing backend verification endpoints and Android UI indicators.

