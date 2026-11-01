"""
EchoShield — Phase 8 Calling & WebRTC Signalling Backend Unit Tests
===================================================================
Tests call session management, WebRTC signalling message routing,
family circle call authorization, post-call security audit reports,
and caller/callee access control.
"""

import sys
import os
import json
import pytest
from unittest.mock import patch
from fastapi.testclient import TestClient
from fastapi import status

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../../backend")))

from app.main import app
from app.calling.service import calling_service
from app.calling.signaling import signaling_manager
from app.calling.models import CallStatus, CallSecurityOutcome

client = TestClient(app)

USER_CALLER = {
    "uid": "uid_caller_111",
    "email": "caller@echoshield.family",
    "name": "Sarah Henderson",
    "email_verified": True
}

USER_CALLEE = {
    "uid": "uid_callee_222",
    "email": "callee@echoshield.family",
    "name": "David Henderson",
    "email_verified": True
}

USER_ATTACKER = {
    "uid": "uid_attacker_999",
    "email": "intruder@malicious.org",
    "name": "Intruder Hacker",
    "email_verified": True
}


@pytest.fixture(autouse=True)
def isolated_calling_service():
    """Configures in-memory isolated service state for calling tests."""
    calling_service.use_in_memory = True
    calling_service._mem_calls.clear()
    calling_service._mem_reports.clear()
    calling_service._mem_relationships.clear()

    # Establish an authorized family relationship between caller and callee
    calling_service._mem_relationships["rel_001"] = {
        "id": "rel_001",
        "members": [USER_CALLER["uid"], USER_CALLEE["uid"]],
        "created_at": "2026-11-01T00:00:00Z"
    }

    # Clear active websocket connections
    signaling_manager._active_connections.clear()
    signaling_manager._socket_to_user.clear()

    yield calling_service


def test_unauthenticated_call_history_rejected():
    """Unauthenticated requests to calling history must be rejected with 401."""
    res = client.get("/api/v1/calling/history")
    assert res.status_code == status.HTTP_401_UNAUTHORIZED


def test_websocket_signaling_requires_token():
    """WebSocket connection without token query parameter is closed with 1008."""
    with pytest.raises(Exception):
        with client.websocket_connect("/api/v1/calling/ws"):
            pass


def test_websocket_signaling_lifecycle_and_peer_routing():
    """
    Tests complete WebRTC signalling exchange between two connected family members:
    1. Caller and Callee connect to WebSocket signalling channel.
    2. Ping / Pong heartbeat works.
    3. Caller initiates call to Callee.
    4. Callee receives call_initiate and answers.
    5. SDP Offer, Answer, and ICE candidates are routed correctly between peers.
    6. Call end terminates the session cleanly.
    """
    call_id = "call_test_888"

    with patch("app.calling.router.verify_firebase_token", side_effect=lambda token, check_revoked=True: (
        USER_CALLER if "caller" in token else USER_CALLEE
    )):
        # Callee connects first
        with client.websocket_connect("/api/v1/calling/ws?token=token_callee") as ws_callee:
            conn_msg = json.loads(ws_callee.receive_text())
            assert conn_msg["type"] == "connected"
            assert conn_msg["user_id"] == USER_CALLEE["uid"]

            # Test Ping / Pong
            ws_callee.send_text(json.dumps({"type": "ping"}))
            pong_msg = json.loads(ws_callee.receive_text())
            assert pong_msg["type"] == "pong"

            # Caller connects
            with client.websocket_connect("/api/v1/calling/ws?token=token_caller") as ws_caller:
                conn_caller = json.loads(ws_caller.receive_text())
                assert conn_caller["type"] == "connected"
                assert conn_caller["user_id"] == USER_CALLER["uid"]

                # Step 1: Caller initiates call
                ws_caller.send_text(json.dumps({
                    "type": "call_initiate",
                    "call_id": call_id,
                    "receiver_id": USER_CALLEE["uid"],
                    "receiver_name": USER_CALLEE["name"],
                    "payload": {}
                }))

                # Callee receives incoming call alert
                incoming_call = json.loads(ws_callee.receive_text())
                assert incoming_call["type"] == "call_initiate"
                assert incoming_call["call_id"] == call_id
                assert incoming_call["sender_id"] == USER_CALLER["uid"]

                # Caller receives call_ringing
                caller_ringing = json.loads(ws_caller.receive_text())
                assert caller_ringing["type"] == "call_ringing"

                # Step 2: Callee accepts call
                ws_callee.send_text(json.dumps({
                    "type": "call_accepted",
                    "call_id": call_id,
                    "receiver_id": USER_CALLER["uid"],
                    "payload": {}
                }))

                # Caller receives call_accepted
                caller_accepted = json.loads(ws_caller.receive_text())
                assert caller_accepted["type"] == "call_accepted"
                assert caller_accepted["call_id"] == call_id

                # Step 3: SDP Offer exchange (Caller -> Callee)
                sdp_offer = {"type": "offer", "sdp": "v=0\r\no=- 123 2 IN IP4 127.0.0.1..."}
                ws_caller.send_text(json.dumps({
                    "type": "offer",
                    "call_id": call_id,
                    "receiver_id": USER_CALLEE["uid"],
                    "payload": sdp_offer
                }))

                callee_offer = json.loads(ws_callee.receive_text())
                assert callee_offer["type"] == "offer"
                assert callee_offer["payload"]["sdp"] == sdp_offer["sdp"]

                # Step 4: SDP Answer exchange (Callee -> Caller)
                sdp_answer = {"type": "answer", "sdp": "v=0\r\no=- 456 2 IN IP4 127.0.0.1..."}
                ws_callee.send_text(json.dumps({
                    "type": "answer",
                    "call_id": call_id,
                    "receiver_id": USER_CALLER["uid"],
                    "payload": sdp_answer
                }))

                caller_answer = json.loads(ws_caller.receive_text())
                assert caller_answer["type"] == "answer"
                assert caller_answer["payload"]["sdp"] == sdp_answer["sdp"]

                # Step 5: ICE Candidate exchange
                ice = {"candidate": "candidate:1 1 UDP 2130706431 192.168.1.1 50000 typ host", "sdpMid": "0"}
                ws_caller.send_text(json.dumps({
                    "type": "ice_candidate",
                    "call_id": call_id,
                    "receiver_id": USER_CALLEE["uid"],
                    "payload": ice
                }))

                callee_ice = json.loads(ws_callee.receive_text())
                assert callee_ice["type"] == "ice_candidate"
                assert callee_ice["payload"]["candidate"] == ice["candidate"]

                # Step 6: Hang up call
                ws_caller.send_text(json.dumps({
                    "type": "call_ended",
                    "call_id": call_id,
                    "receiver_id": USER_CALLEE["uid"],
                    "payload": {"duration_seconds": 42, "reason": "Normal hangup"}
                }))

                callee_ended = json.loads(ws_callee.receive_text())
                assert callee_ended["type"] == "call_ended"
                assert callee_ended["call_id"] == call_id


def test_call_report_creation_and_authorization():
    """
    Tests saving and retrieving a Phase 7 Combined Security Engine post-call report:
    1. Authorized participant (Caller) submits post-call report with IDENTITY_CONFIRMED.
    2. Both Caller and Callee can read the report.
    3. Unauthorized third-party (Attacker) is rejected with 403 Forbidden.
    """
    call_id = "call_audit_777"

    # Pre-populate call session
    calling_service._mem_calls[call_id] = {
        "call_id": call_id,
        "caller_id": USER_CALLER["uid"],
        "caller_name": USER_CALLER["name"],
        "callee_id": USER_CALLEE["uid"],
        "callee_name": USER_CALLEE["name"],
        "status": CallStatus.ENDED.value,
        "created_at": "2026-11-01T01:00:00Z",
        "duration_seconds": 65
    }

    report_payload = {
        "call_id": call_id,
        "caller_id": USER_CALLER["uid"],
        "caller_name": USER_CALLER["name"],
        "callee_id": USER_CALLEE["uid"],
        "callee_name": USER_CALLEE["name"],
        "overall_security_outcome": CallSecurityOutcome.IDENTITY_CONFIRMED.value,
        "security_verdict_headline": "Voice Identity Cryptographically Confirmed",
        "security_explanation": "Acoustic biometric parameters matched enrolled voice profile with no synthetic anomalies.",
        "analyses_count": 3,
        "speaker_verification_summary": {
            "result": "VOICE_MATCHED",
            "similarity_score": 0.88,
            "decision_status": "high_match"
        },
        "antispoof_summary": {
            "result_category": "NO_SPOOF_SIGNAL_DETECTED",
            "spoof_score": 0.05
        },
        "risk_level": "LOW",
        "is_threat_detected": False,
        "duration_seconds": 65,
        "key_findings": ["ECAPA-TDNN high similarity (0.88)", "Wav2Vec2 no synthetic artefacts detected"],
        "recommendations": ["Call verified as authentic."]
    }

    # Step 1: Caller submits report
    with patch("app.auth.security.verify_firebase_token", return_value=USER_CALLER):
        res = client.post(
            "/api/v1/calling/reports",
            json=report_payload,
            headers={"Authorization": "Bearer token_caller"}
        )
        assert res.status_code == status.HTTP_201_CREATED
        data = res.json()
        assert data["overall_security_outcome"] == CallSecurityOutcome.IDENTITY_CONFIRMED.value
        assert data["risk_level"] == "LOW"
        assert not data["is_threat_detected"]

    # Step 2: Callee retrieves report (allowed)
    with patch("app.auth.security.verify_firebase_token", return_value=USER_CALLEE):
        res = client.get(
            f"/api/v1/calling/reports/{call_id}",
            headers={"Authorization": "Bearer token_callee"}
        )
        assert res.status_code == status.HTTP_200_OK
        assert res.json()["call_id"] == call_id

    # Step 3: Attacker attempts to retrieve report (403 Forbidden)
    with patch("app.auth.security.verify_firebase_token", return_value=USER_ATTACKER):
        res = client.get(
            f"/api/v1/calling/reports/{call_id}",
            headers={"Authorization": "Bearer token_attacker"}
        )
        assert res.status_code == status.HTTP_403_FORBIDDEN


def test_call_history_endpoint_and_directions():
    """
    Tests call history returns proper direction and attached security outcome.
    """
    call_id = "call_hist_1"
    calling_service._mem_calls[call_id] = {
        "call_id": call_id,
        "caller_id": USER_CALLER["uid"],
        "caller_name": USER_CALLER["name"],
        "callee_id": USER_CALLEE["uid"],
        "callee_name": USER_CALLEE["name"],
        "status": CallStatus.ENDED.value,
        "created_at": "2026-11-01T02:00:00Z",
        "duration_seconds": 120
    }
    calling_service._mem_reports[call_id] = {
        "overall_security_outcome": CallSecurityOutcome.SUSPICIOUS_AUDIO.value,
        "security_verdict_headline": "Suspicious Audio Signal Detected"
    }

    # Check from Caller's perspective (direction = outgoing)
    with patch("app.auth.security.verify_firebase_token", return_value=USER_CALLER):
        res = client.get(
            "/api/v1/calling/history",
            headers={"Authorization": "Bearer token_caller"}
        )
        assert res.status_code == status.HTTP_200_OK
        data = res.json()
        assert data["total_count"] == 1
        item = data["calls"][0]
        assert item["direction"] == "outgoing"
        assert item["other_party_name"] == USER_CALLEE["name"]
        assert item["security_outcome"] == CallSecurityOutcome.SUSPICIOUS_AUDIO.value
        assert item["has_report"] is True

    # Check from Callee's perspective (direction = incoming)
    with patch("app.auth.security.verify_firebase_token", return_value=USER_CALLEE):
        res = client.get(
            "/api/v1/calling/history",
            headers={"Authorization": "Bearer token_callee"}
        )
        assert res.status_code == status.HTTP_200_OK
        item = res.json()["calls"][0]
        assert item["direction"] == "incoming"
        assert item["other_party_name"] == USER_CALLER["name"]

