"""
EchoShield — WebRTC Calling & Security Signalling Data Models
=============================================================
Defines Pydantic schemas and enums for internet-based voice calling,
WebRTC signalling, call sessions, and post-call security audit reports.
"""

from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class CallStatus(str, Enum):
    """Lifecycle states of an EchoShield WebRTC voice call."""
    INITIATING = "INITIATING"
    RINGING = "RINGING"
    CONNECTING = "CONNECTING"
    CONNECTED = "CONNECTED"
    ENDED = "ENDED"
    REJECTED = "REJECTED"
    MISSED = "MISSED"
    FAILED = "FAILED"
    BUSY = "BUSY"


class SignalingMessageType(str, Enum):
    """Types of WebRTC signalling and call orchestration messages."""
    CALL_INITIATE = "call_initiate"
    CALL_RINGING = "call_ringing"
    CALL_ACCEPTED = "call_accepted"
    CALL_REJECTED = "call_rejected"
    CALL_ENDED = "call_ended"
    CALL_FAILED = "call_failed"
    CALL_BUSY = "call_busy"
    OFFER = "offer"
    ANSWER = "answer"
    ICE_CANDIDATE = "ice_candidate"
    PING = "ping"
    PONG = "pong"
    ERROR = "error"


class CallSecurityOutcome(str, Enum):
    """
    Directly reflects Phase 7 Combined Security Engine outcomes
    for call auditing and live HUD alerts.
    """
    IDENTITY_CONFIRMED = "IDENTITY_CONFIRMED"
    SUSPICIOUS_AUDIO = "SUSPICIOUS_AUDIO"
    IDENTITY_NOT_CONFIRMED = "IDENTITY_NOT_CONFIRMED"
    INCONCLUSIVE = "INCONCLUSIVE"
    ANALYSIS_UNAVAILABLE = "ANALYSIS_UNAVAILABLE"
    AUDIO_QUALITY_FAILED = "AUDIO_QUALITY_FAILED"
    PROFILE_NOT_ENROLLED = "PROFILE_NOT_ENROLLED"


class SignalingMessage(BaseModel):
    """Envelope for WebSocket WebRTC signalling packets."""
    type: SignalingMessageType = Field(..., description="Signalling event type")
    call_id: Optional[str] = Field(None, description="Active call session identifier")
    sender_id: Optional[str] = Field(None, description="Sender UID")
    sender_name: Optional[str] = Field(None, description="Sender display name")
    receiver_id: Optional[str] = Field(None, description="Recipient UID")
    payload: Optional[Dict[str, Any]] = Field(default_factory=dict, description="SDP, ICE, or event details")
    timestamp: Optional[str] = Field(None, description="ISO timestamp")


class CallSession(BaseModel):
    """In-progress or recorded call session record."""
    call_id: str = Field(..., description="Unique call ID")
    caller_id: str = Field(..., description="Initiator UID")
    caller_name: str = Field(..., description="Initiator display name")
    callee_id: str = Field(..., description="Target family member UID")
    callee_name: str = Field(..., description="Target family member display name")
    status: CallStatus = Field(default=CallStatus.INITIATING, description="Current call status")
    created_at: str = Field(..., description="Call initiation timestamp")
    started_at: Optional[str] = Field(None, description="Call answered/connected timestamp")
    ended_at: Optional[str] = Field(None, description="Call termination timestamp")
    duration_seconds: int = Field(0, description="Duration of call in seconds")
    end_reason: Optional[str] = Field(None, description="Reason for call termination")


class CallReportCreate(BaseModel):
    """Request payload for storing a post-call security audit report."""
    call_id: str = Field(..., description="Associated call identifier")
    caller_id: str = Field(..., description="Caller UID")
    caller_name: str = Field(..., description="Caller display name")
    callee_id: str = Field(..., description="Callee UID")
    callee_name: str = Field(..., description="Callee display name")
    overall_security_outcome: CallSecurityOutcome = Field(..., description="Synthesized Phase 7 security outcome")
    security_verdict_headline: str = Field(..., description="High-visibility status headline")
    security_explanation: str = Field(..., description="Forensic rationale")
    analyses_count: int = Field(1, description="Number of periodic samples analyzed during call")
    speaker_verification_summary: Optional[Dict[str, Any]] = Field(None, description="Phase 5 summary")
    antispoof_summary: Optional[Dict[str, Any]] = Field(None, description="Phase 6 summary")
    audio_quality_summary: Optional[Dict[str, Any]] = Field(None, description="Acoustic quality summary")
    risk_level: str = Field("LOW", description="LOW, MEDIUM, HIGH, CRITICAL")
    is_threat_detected: bool = Field(False, description="Whether spoofing or impersonation was flagged")
    duration_seconds: int = Field(0, description="Duration in seconds")
    key_findings: List[str] = Field(default_factory=list, description="Key security highlights")
    recommendations: List[str] = Field(default_factory=list, description="Safety recommendations")


class CallReportResponse(BaseModel):
    """Detailed post-call security audit report."""
    report_id: str = Field(..., description="Unique report ID")
    call_id: str = Field(..., description="Associated call ID")
    caller_id: str = Field(..., description="Caller UID")
    caller_name: str = Field(..., description="Caller display name")
    callee_id: str = Field(..., description="Callee UID")
    callee_name: str = Field(..., description="Callee display name")
    overall_security_outcome: CallSecurityOutcome = Field(..., description="Synthesized security verdict")
    security_verdict_headline: str = Field(..., description="Status headline")
    security_explanation: str = Field(..., description="Explanation of security analysis")
    analyses_count: int = Field(1, description="Number of audio windows analyzed")
    speaker_verification_summary: Optional[Dict[str, Any]] = None
    antispoof_summary: Optional[Dict[str, Any]] = None
    audio_quality_summary: Optional[Dict[str, Any]] = None
    risk_level: str = Field("LOW", description="Risk level")
    is_threat_detected: bool = Field(False, description="Threat flag")
    duration_seconds: int = Field(0, description="Duration in seconds")
    key_findings: List[str] = Field(default_factory=list)
    recommendations: List[str] = Field(default_factory=list)
    disclaimer: str = Field(
        "EchoShield Voice Security Engine provides calibrated probabilistic defense against voice impersonation. "
        "Audio analysis is performed asynchronously on remote caller streams without audio storage. "
        "Never use voice alone to authorize irrevocable financial transactions."
    )
    created_at: str = Field(..., description="Report creation timestamp")


class CallHistoryItem(BaseModel):
    """Summary item for call history listings."""
    call_id: str
    caller_id: str
    caller_name: str
    callee_id: str
    callee_name: str
    direction: str = Field(..., description="'incoming' or 'outgoing' relative to authenticated user")
    other_party_name: str
    other_party_id: str
    status: CallStatus
    duration_seconds: int
    security_outcome: Optional[CallSecurityOutcome] = None
    security_headline: Optional[str] = None
    has_report: bool = False
    created_at: str


class CallHistoryResponse(BaseModel):
    """Paginated list of call history items."""
    calls: List[CallHistoryItem]
    total_count: int

