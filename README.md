# EchoShield

<p align="center">
  <strong>Native Android Voice Authentication & Deepfake Scam Defense System for Families</strong>
</p>

---

## Overview

**EchoShield** is a real-time defense application designed to shield families from AI voice cloning scams, emergency impersonation fraud, and synthetic audio deepfakes. When a family member calls through EchoShield's encrypted WebRTC channel, their live audio is evaluated against consent-based biometric voice profiles (SpeechBrain ECAPA-TDNN) and a neural anti-spoofing engine to deliver real-time in-call trust indicators.

---

## Key Features

- **Informed Consent Biometric Registration**: Guided voice enrollment storing mathematical vector embeddings (192-dim floats), encrypted with AES-256-GCM. Raw audio is never kept permanently.
- **Dual-Engine AI Verification**:
  - *Speaker Recognition*: Pretrained ECAPA-TDNN computes biometric similarity against registered family profiles.
  - *Anti-Spoofing*: Pretrained classifier spots neural vocoder artifacts, Text-to-Speech (TTS), Voice Conversion (VC), and replay characteristics.
- **Real-Time In-Call Security HUD**: Unobtrusive live color-coded status gauge (Green: Authentic, Yellow: Unregistered, Red: Deepfake/Spoof Alert).
- **Encrypted In-App Audio Calling**: WebRTC peer-to-peer audio with authenticated WebSocket signalling. No silent recording or SIM call interception.
- **Family Accounts & Shared Circles**: Manage family circles, invite members, inspect verification audit logs, and trigger instant security alerts.

---

## Project Structure

```
d:\EchoShield\
├── .gitignore                      # Security and build ignores
├── README.md                       # Main project documentation
├── docs/                           # Architecture, compatibility, dependencies, setups
│   ├── ARCHITECTURE.md             # System & layer architecture specifications
│   ├── TECH_COMPATIBILITY_REPORT.md# Host tooling & dependency compatibility report
│   ├── DEPENDENCY_PLAN.md          # Android, Backend, and AI dependency plans
│   ├── ENVIRONMENT_SETUP.md        # Windows 11 developer setup guide
│   ├── PHASE_TRACKING.md           # Master roadmap and phase execution tracker
│   └── API_SPECIFICATION.md        # REST and WebSocket signalling specifications
├── android-app/                    # Android Native Application (Kotlin + Jetpack Compose)
│   └── app/src/main/java/com/echoshield/
│       ├── core/                   # Network, crypto, audio records, permissions
│       ├── navigation/             # Compose navigation graph & routing
│       ├── theme/                  # Material 3 styling and color systems
│       ├── authentication/         # Login, register, MFA screens & ViewModels
│       ├── dashboard/              # Home view, family status, quick dial
│       ├── family/                 # Family management & invites
│       ├── voice/                  # Consent & audio enrollment recorder
│       ├── calling/                # WebRTC calling UI & call state machines
│       ├── verification/           # Live in-call verification HUD & radar
│       ├── reports/                # Verification audit trail & forensic reports
│       ├── notifications/          # Push alerts & foreground call notifications
│       ├── settings/               # Biometric purge & privacy management
│       ├── data/                   # Repositories, DTOs, Firestore, Retrofit
│       └── domain/                 # Domain entities, Repository interfaces, UseCases
├── backend/                        # Backend Services (FastAPI + Pydantic)
│   ├── app/
│   │   ├── auth/                   # Firebase JWT verification
│   │   ├── users/                  # User profile operations
│   │   ├── family/                 # Family circles and invitations
│   │   ├── enrollment/             # Voice registration endpoints
│   │   ├── speaker/                # Biometric embedding services
│   │   ├── antispoof/              # Anti-spoofing inference services
│   │   ├── calling/                # WebRTC WebSocket signalling
│   │   ├── verification/           # Scoring and decision fusion engine
│   │   ├── reports/                # Forensic history and reports
│   │   └── common/                 # Models, errors, shared schemas
│   └── requirements.txt
├── ai-engine/                      # AI Pipelines & Model Definitions
│   ├── models/                     # Checkpoints & weights (git-ignored)
│   ├── pipelines/                  # SpeechBrain & Anti-spoofing pipelines
│   └── requirements.txt
└── tests/                          # Tests hierarchy
    ├── unit/                       # Unit tests
    ├── integration/                # Integration test suites
    └── e2e/                        # End-to-end verification flows
```

---

## Documentation Links

- [System Architecture](docs/ARCHITECTURE.md)
- [Technology Compatibility Report](docs/TECH_COMPATIBILITY_REPORT.md)
- [Dependency Plan](docs/DEPENDENCY_PLAN.md)
- [Environment Setup Guide](docs/ENVIRONMENT_SETUP.md)
- [Phase Tracking Roadmap](docs/PHASE_TRACKING.md)
- [API Specification](docs/API_SPECIFICATION.md)

---


