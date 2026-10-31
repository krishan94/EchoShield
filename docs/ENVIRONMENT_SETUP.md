# EchoShield Environment Setup & Developer Guide

This document details the complete step-by-step instructions for configuring and running the EchoShield development environment on Windows 11.

---

## 1. System Requirements & Verified Tooling

Ensure the following tools are present (as inspected in Phase 00):
- **OS**: Windows 10 or 11 (64-bit)
- **Git**: v2.40+ (Installed: `2.53.0.windows.2`)
- **Python**: v3.13.4 (Installed: `C:\Users\navi\AppData\Local\Programs\Python\Python313\`)
- **Java JDK**: OpenJDK 17 LTS (Installed: `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\`)
- **Android Studio**: Ladybug / Hedgehog+ (Installed: `C:\Program Files\Android\Android Studio`)
- **Android SDK**: API 34 & 35 (Installed: `C:\Users\navi\AppData\Local\Android\Sdk`)

---

## 2. Environment Variables Verification

Run the following in PowerShell to verify environment variable configuration:

```powershell
# Verify ANDROID_HOME
$env:ANDROID_HOME = "C:\Users\navi\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\navi\AppData\Local\Android\Sdk"

# Verify JAVA_HOME points to JDK 17
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"

# Verify PATH includes platform-tools (adb)
$env:Path += ";C:\Users\navi\AppData\Local\Android\Sdk\platform-tools"
```

To persist these globally on Windows:
```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Users\navi\AppData\Local\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot", "User")
```

---

## 3. Python Virtual Environment Setup

Isolate Python backend and AI dependencies using a local virtual environment:

```powershell
# Navigate to the project root
cd d:\EchoShield

# Create a unified virtual environment
python -m venv .venv

# Activate the virtual environment
.\.venv\Scripts\Activate.ps1

# Upgrade pip, wheel, and setuptools
python -m pip install --upgrade pip setuptools wheel

# Install Backend dependencies
pip install -r backend/requirements.txt

# Install AI Engine dependencies
pip install -r ai-engine/requirements.txt
```

---

## 4. Firebase Project Configuration

EchoShield utilizes Firebase for user identity and Cloud Firestore for family metadata and audit logs.

### 4.1 Firebase Console Setup
1. Open the [Firebase Console](https://console.firebase.google.com/) and create a new project named `EchoShield`.
2. Under **Build > Authentication**:
   - Enable the **Email/Password** sign-in provider.
3. Under **Build > Firestore Database**:
   - Create a database in your preferred region.
   - Start in Test or Locked mode (production rules will be deployed in Phase 02).

### 4.2 Android Client Credentials
1. In Firebase Project Settings, add an **Android Application**:
   - Package Name: `com.echoshield`
   - App Nickname: `EchoShield Android`
   - Debug signing certificate SHA-1: Generate via `./gradlew signingReport`
2. Download `google-services.json` and place it in:
   `d:\EchoShield\android-app\app\google-services.json`
   *(This file is safely ignored by `.gitignore`)*.

### 4.3 Backend Service Account Setup
1. In Firebase Project Settings, navigate to **Service Accounts**.
2. Click **Generate new private key** and download the JSON file.
3. Place this file in `d:\EchoShield\backend\secrets\serviceAccountKey.json` (do not commit!).
4. Create a `.env` file in `d:\EchoShield\backend\.env`:
```ini
ECHOSHIELD_ENV=development
PORT=8000
HOST=0.0.0.0
FIREBASE_CREDENTIALS_PATH=./secrets/serviceAccountKey.json
BIOMETRIC_ENCRYPTION_KEY=YOUR_32_BYTE_BASE64_AES_KEY
STUN_SERVER=stun:stun.l.google.com:19302
```

---

## 5. Android Studio & Emulator Configuration

1. **Launch Android Studio**.
2. Go to **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**:
   - Set **Gradle JDK** to: `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` (or Embedded JBR).
3. **Android Virtual Device (AVD)**:
   - Create an AVD targeting **API 35 (VanillaIceCream)** or **API 34 (UpsideDownCake)** with Google APIs.
   - In AVD Settings, ensure **Microphone** is set to **Virtual Microphone** or **Host Audio Input** to allow voice enrollment and in-app call audio testing.

---

## 6. Verification & Health Check

### 6.1 Check Android Tooling
```powershell
adb devices
```

### 6.2 Check Backend Server (Once implemented in Phase 01)
```powershell
cd d:\EchoShield\backend
uvicorn app.main:app --reload --port 8000
```
Navigate to `http://localhost:8000/docs` to view the interactive OpenAPI documentation.

### 6.3 Check SpeechBrain Model Loading
```powershell
python -c "import speechbrain; print('SpeechBrain version:', speechbrain.__version__)"
```
Expected output: `SpeechBrain version: 1.1.2`

