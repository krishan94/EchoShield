# EchoShield REST & WebSocket API Specification

## 1. Overview
EchoShield exposes a RESTful API and WebSocket signalling service via FastAPI for Android client communication, identity verification, voice enrollment, and real-time audio chunk verification.

- **Base URL**: `https://api.echoshield.internal/v1` (or `http://localhost:8000/v1` in local development)
- **Signalling WebSocket**: `wss://api.echoshield.internal/v1/calling/ws`
- **Authentication**: Bearer Token in `Authorization` header containing Firebase ID Token:
  `Authorization: Bearer <Firebase_ID_Token>`

---

## 2. Authentication & User Profile Endpoints

### `POST /v1/auth/verify-token`
Verifies client Firebase ID token and provisions user session.
- **Request Headers**: `Authorization: Bearer <token>`
- **Response `200 OK`**:
```json
{
  "status": "success",
  "userId": "firebase_uid_123",
  "email": "user@example.com",
  "familyId": "fam_abc_456",
  "role": "admin",
  "isEnrolled": true
}
```

---

## 3. Family Management Endpoints

### `POST /v1/family/create`
Creates a new family circle.
- **Request Body**:
```json
{
  "familyName": "The Henderson Family"
}
```
- **Response `201 Created`**:
```json
{
  "familyId": "fam_abc_456",
  "familyName": "The Henderson Family",
  "inviteCode": "HEND-7821",
  "adminUserId": "firebase_uid_123",
  "createdAt": "2026-10-31T20:00:00Z"
}
```

### `POST /v1/family/join`
Joins an existing family circle via invite code.
- **Request Body**:
```json
{
  "inviteCode": "HEND-7821"
}
```
- **Response `200 OK`**:
```json
{
  "status": "joined",
  "familyId": "fam_abc_456",
  "role": "member"
}
```

### `GET /v1/family/members`
Lists all members in the user's family circle and their voice enrollment statuses.
- **Response `200 OK`**:
```json
{
  "members": [
    {
      "userId": "uid_1",
      "displayName": "Sarah Henderson",
      "role": "admin",
      "voiceEnrolled": true,
      "enrolledAt": "2026-10-31T20:30:00Z"
    },
    {
      "userId": "uid_2",
      "displayName": "Mark Henderson",
      "role": "member",
      "voiceEnrolled": false,
      "enrolledAt": null
    }
  ]
}
```

---

## 4. Voice Enrollment Endpoints

### `POST /v1/enrollment/submit`
Submits raw 16kHz PCM audio samples along with cryptographically recorded user consent.
- **Content-Type**: `multipart/form-data`
- **Fields**:
  - `consentGranted`: `true` (Boolean, mandatory)
  - `consentTimestamp`: ISO 8601 string
  - `audioSamples`: Binary PCM 16kHz mono audio files (3 required passes)
- **Processing**:
  - Validates audio duration (5–10s), SNR, and clipping.
  - Extracts 192-dim ECAPA-TDNN embedding for each pass and computes average normalized vector.
  - Encrypts vector with AES-256-GCM.
  - Deletes raw audio from disk immediately.
- **Response `200 OK`**:
```json
{
  "status": "enrolled",
  "userId": "firebase_uid_123",
  "vectorDimensions": 192,
  "qualityScore": 0.94,
  "enrolledAt": "2026-10-31T21:00:00Z"
}
```

---

## 5. WebRTC Signalling & Verification WebSocket

### `WebSocket /v1/calling/ws?token=<Firebase_Token>`
Authenticated full-duplex connection for WebRTC session negotiation and real-time audio analysis.

#### Messages (Client -> Server):
```json
{
  "type": "CALL_OFFER",
  "targetUserId": "uid_2",
  "sdp": "v=0\r\no=..."
}
```
```json
{
  "type": "CALL_ANSWER",
  "targetUserId": "uid_1",
  "sdp": "v=0\r\no=..."
}
```
```json
{
  "type": "ICE_CANDIDATE",
  "targetUserId": "uid_2",
  "candidate": "candidate:..."
}
```
```json
{
  "type": "AUDIO_VERIFICATION_CHUNK",
  "callId": "call_987",
  "claimedCallerId": "uid_1",
  "audioChunkBase64": "UklGRi...",
  "sequenceNumber": 14
}
```

#### Messages (Server -> Client):
```json
{
  "type": "VERIFICATION_UPDATE",
  "callId": "call_987",
  "sequenceNumber": 14,
  "verdict": "AUTHENTIC",
  "speakerSimilarityScore": 0.88,
  "spoofProbability": 0.04,
  "confidence": 0.96,
  "colorCode": "GREEN",
  "explanation": "Voice closely matches registered biometric profile. No synthetic or replay artifacts detected."
}
```

---

## 6. Verification History & Reports

### `GET /v1/reports/history`
Returns historical call verification records for the authenticated user and family.
- **Response `200 OK`**:
```json
{
  "verifications": [
    {
      "callId": "call_987",
      "timestamp": "2026-10-31T21:15:00Z",
      "callerName": "Sarah Henderson",
      "durationSeconds": 142,
      "verdict": "AUTHENTIC",
      "avgSimilarityScore": 0.87,
      "maxSpoofScore": 0.06
    }
  ]
}
```
