# EchoShield Dependency Plan & Catalog Specification

This document details all third-party libraries, frameworks, SDKs, and build plugins across the Android client, Python backend, and AI verification engine.

---

## 1. Versioning & Licensing Strategy

- **Reproducibility**: All production dependencies are strictly version-pinned to prevent transitive drift and build breakage.
- **License Compliance**: Only permissive open-source licenses (Apache-2.0, MIT, BSD-2/3-Clause) are approved.
- **Biometric Security Integrity**: Cryptographic and biometric processing dependencies must have zero known high-severity CVEs.

---

## 2. Android Client Dependency Catalog

EchoShield uses Gradle Version Catalogs (`gradle/libs.versions.toml`). Below is the catalog plan:

### 2.1 Core & Kotlin
| Dependency | Version | Purpose | License |
| :--- | :--- | :--- | :--- |
| `androidx.core:core-ktx` | `1.15.0` | Kotlin extensions for Android framework APIs | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `2.8.7` | Coroutine lifecycle scopes and Flow observation | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.8.7` | ViewModel integration with Jetpack Compose | Apache-2.0 |
| `androidx.activity:activity-compose` | `1.9.3` | Compose activity bridge & back handler | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android`| `1.9.0` | Asynchronous programming and Dispatchers | Apache-2.0 |

### 2.2 Jetpack Compose & UI
| Dependency | Version | Purpose | License |
| :--- | :--- | :--- | :--- |
| `androidx.compose:compose-bom` | `2024.10.01` | Bill of Materials for Compose synchronization | Apache-2.0 |
| `androidx.compose.ui:ui` | BOM | Core UI layout primitives | Apache-2.0 |
| `androidx.compose.ui:ui-graphics` | BOM | Canvas drawing and biometric radar visualizations | Apache-2.0 |
| `androidx.compose.ui:ui-tooling-preview` | BOM | Android Studio UI component previews | Apache-2.0 |
| `androidx.compose.material3:material3` | `1.3.1` | Material 3 adaptive UI components | Apache-2.0 |
| `androidx.compose.material:material-icons-extended` | BOM | Comprehensive vector iconography | Apache-2.0 |
| `androidx.navigation:navigation-compose` | `2.8.3` | Type-safe declarative navigation routing | Apache-2.0 |
| `io.coil-kt:coil-compose` | `2.7.0` | Asynchronous image loading for avatars | Apache-2.0 |

### 2.3 Dependency Injection (Hilt)
| Dependency | Version | Purpose | License |
| :--- | :--- | :--- | :--- |
| `com.google.dagger:hilt-android` | `2.51.1` | Compile-time dependency injection | Apache-2.0 |
| `com.google.dagger:hilt-compiler` | `2.51.1` | Annotation processor (KSP / KAPT) | Apache-2.0 |
| `androidx.hilt:hilt-navigation-compose` | `1.2.0` | Hilt ViewModel injection directly in Composables | Apache-2.0 |

### 2.4 Networking, Real-Time WebRTC & Signalling
| Dependency | Version | Purpose | License |
| :--- | :--- | :--- | :--- |
| `com.squareup.retrofit2:retrofit` | `2.11.0` | Type-safe REST client for backend APIs | Apache-2.0 |
| `com.squareup.retrofit2:converter-moshi` | `2.11.0` | High-performance JSON serialization | Apache-2.0 |
| `com.squareup.okhttp3:okhttp` | `4.12.0` | HTTP/2, WebSocket client, token interceptors | Apache-2.0 |
| `com.squareup.okhttp3:logging-interceptor` | `4.12.0` | HTTP traffic inspection in debug builds | Apache-2.0 |
| `io.getstream:stream-webrtc-android` | `1.2.2` | Production WebRTC SDK with audio stream tap | Apache-2.0 |

### 2.5 Security, Crypto & Firebase
| Dependency | Version | Purpose | License |
| :--- | :--- | :--- | :--- |
| `androidx.security:security-crypto` | `1.1.0-alpha06` | Android Keystore backed EncryptedSharedPreferences | Apache-2.0 |
| `com.google.firebase:firebase-bom` | `33.5.1` | Firebase version synchronization | Apache-2.0 |
| `com.google.firebase:firebase-auth-ktx` | BOM | Firebase Authentication client | Apache-2.0 |
| `com.google.firebase:firebase-firestore-ktx`| BOM | Cloud Firestore realtime client | Apache-2.0 |
| `com.google.firebase:firebase-messaging-ktx`| BOM | Push alerts for incoming calls & threats | Apache-2.0 |

### 2.6 Testing
| Dependency | Version | Purpose | License |
| :--- | :--- | :--- | :--- |
| `junit:junit` | `4.13.2` | Core unit testing | EPL-1.0 |
| `org.junit.jupiter:junit-jupiter` | `5.11.3` | Modern JUnit 5 framework | EPL-2.0 |
| `io.mockk:mockk` | `1.13.13` | Kotlin-idiomatic mocking library | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.9.0` | Coroutine and Flow unit test dispatchers | Apache-2.0 |
| `app.cash.turbine:turbine` | `1.2.0` | Flow assertion testing library | Apache-2.0 |
| `androidx.test.ext:junit` | `1.2.1` | AndroidX test runners | Apache-2.0 |
| `androidx.test.espresso:espresso-core` | `3.6.1` | Instrumentation UI test automation | Apache-2.0 |

---

## 3. Python Backend Dependencies (`backend/requirements.txt`)

```text
# Web Framework & API Server
fastapi==0.162.0
uvicorn[standard]==0.54.0
pydantic==2.14.2
pydantic-settings==2.6.1

# Real-Time & Signalling
websockets==13.1
python-socketio==5.11.4

# Security & Firebase
firebase-admin==7.7.0
python-jose[cryptography]==3.3.0
cryptography==43.0.3
passlib[bcrypt]==1.7.4

# Networking & Utilities
httpx==0.28.1
python-multipart==0.0.17
aiofiles==24.1.0

# Testing & Quality
pytest==8.3.3
pytest-asyncio==0.24.0
pytest-cov==6.0.0
ruff==0.7.1
mypy==1.13.0
```

---

## 4. AI Engine Dependencies (`ai-engine/requirements.txt`)

```text
# Deep Learning & Neural Inference
torch==2.15.0
torchaudio==2.11.1
speechbrain==1.1.2
onnxruntime==1.20.0

# Audio Signal Processing & Feature Extraction
soundfile==0.14.0
scipy==1.18.1
numpy==2.5.4
librosa==0.10.2.post1

# Model Distribution & Serialization
huggingface-hub==0.26.2
safetensors==0.4.5

# Validation & Testing
pytest==8.3.3
scikit-learn==1.5.2
```

