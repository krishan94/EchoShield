"""
EchoShield — Real-Time WebRTC Calling & Signalling Router
=========================================================
Provides:
- WebSocket endpoint `/api/v1/calling/ws` for real-time WebRTC signalling
  (offer, answer, ICE candidates, call states) secured by Firebase ID token.
- REST endpoints for post-call security audit reports and call history.
"""

import json
import logging
from datetime import datetime, timezone
from typing import Optional

from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    Query,
    WebSocket,
    WebSocketDisconnect,
    status
)

from app.auth.models import AuthenticatedUser
from app.auth.security import get_current_user, verify_firebase_token
from app.calling.models import (
    CallHistoryResponse,
    CallReportCreate,
    CallReportResponse,
    CallSession,
    CallStatus,
    SignalingMessage,
    SignalingMessageType,
)
from app.calling.service import calling_service
from app.calling.signaling import signaling_manager

logger = logging.getLogger("echoshield.api.calling")

router = APIRouter(prefix="/calling", tags=["Real-Time Calling & WebRTC Signalling"])


@router.websocket("/ws")
async def calling_websocket_endpoint(
    websocket: WebSocket,
    token: Optional[str] = Query(None)
):
    """
    WebSocket endpoint for real-time WebRTC call signalling between family members.
    Requires a valid Firebase ID token passed via query parameter '?token=<JWT>'.
    """
    if not token or not token.strip():
        logger.warning("WebSocket connection rejected: token missing.")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    # Authenticate user via Firebase token
    try:
        claims = verify_firebase_token(token.strip())
        user_id = claims.get("uid") or claims.get("sub")
        user_name = claims.get("name") or claims.get("email") or "Family Member"
        if not user_id:
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
    except Exception as e:
        logger.warning("WebSocket authentication failed: %s", str(e))
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    # Accept connection and register in signalling manager
    await websocket.accept()
    await signaling_manager.connect(websocket, user_id)

    # Send initial connection confirmation
    await websocket.send_text(json.dumps({
        "type": "connected",
        "user_id": user_id,
        "message": "WebRTC signalling channel established."
    }))

    active_call_id: Optional[str] = None
    peer_user_id: Optional[str] = None

    try:
        while True:
            raw_text = await websocket.receive_text()
            try:
                data = json.loads(raw_text)
                msg_type = data.get("type")
            except Exception:
                await websocket.send_text(json.dumps({
                    "type": SignalingMessageType.ERROR.value,
                    "payload": {"error": "Invalid JSON format"}
                }))
                continue

            # Heartbeat handling
            if msg_type == SignalingMessageType.PING.value:
                await websocket.send_text(json.dumps({"type": SignalingMessageType.PONG.value}))
                continue

            target_id = data.get("receiver_id")
            c_id = data.get("call_id")

            # 1. CALL INITIATE
            if msg_type == SignalingMessageType.CALL_INITIATE.value:
                if not target_id:
                    await websocket.send_text(json.dumps({
                        "type": SignalingMessageType.CALL_FAILED.value,
                        "payload": {"error": "Target recipient ID required"}
                    }))
                    continue

                # Verify both users are family members
                is_family = await calling_service.verify_family_relationship(user_id, target_id)
                if not is_family and not calling_service.use_in_memory:
                    await websocket.send_text(json.dumps({
                        "type": SignalingMessageType.CALL_FAILED.value,
                        "payload": {"error": "Security violation: Calls can only be established between verified family members."}
                    }))
                    continue

                # Check if target is online
                if not signaling_manager.is_user_online(target_id):
                    # Record missed call
                    await calling_service.create_call_session(
                        caller_id=user_id,
                        caller_name=user_name,
                        callee_id=target_id,
                        callee_name=data.get("receiver_name", "Family Member"),
                        call_id=c_id
                    )
                    await calling_service.update_call_status(
                        call_id=c_id or "call_unknown",
                        call_status=CallStatus.MISSED,
                        end_reason="Recipient offline"
                    )
                    await websocket.send_text(json.dumps({
                        "type": SignalingMessageType.CALL_FAILED.value,
                        "call_id": c_id,
                        "payload": {"error": "Family member is currently offline."}
                    }))
                    continue

                # Target is online: create call session
                session = await calling_service.create_call_session(
                    caller_id=user_id,
                    caller_name=user_name,
                    callee_id=target_id,
                    callee_name=data.get("receiver_name", "Family Member"),
                    call_id=c_id
                )
                active_call_id = session.call_id
                peer_user_id = target_id

                # Forward incoming call alert to callee
                delivered = await signaling_manager.send_to_user(target_id, {
                    "type": SignalingMessageType.CALL_INITIATE.value,
                    "call_id": session.call_id,
                    "sender_id": user_id,
                    "sender_name": user_name,
                    "receiver_id": target_id,
                    "payload": data.get("payload", {})
                })

                if not delivered:
                    await websocket.send_text(json.dumps({
                        "type": SignalingMessageType.CALL_FAILED.value,
                        "call_id": session.call_id,
                        "payload": {"error": "Failed to deliver call alert to recipient."}
                    }))
                else:
                    await websocket.send_text(json.dumps({
                        "type": SignalingMessageType.CALL_RINGING.value,
                        "call_id": session.call_id,
                        "receiver_id": target_id
                    }))

            # 2. CALL RINGING
            elif msg_type == SignalingMessageType.CALL_RINGING.value:
                if target_id:
                    await signaling_manager.send_to_user(target_id, {
                        "type": SignalingMessageType.CALL_RINGING.value,
                        "call_id": c_id,
                        "sender_id": user_id
                    })
                    if c_id:
                        await calling_service.update_call_status(c_id, CallStatus.RINGING)

            # 3. CALL ACCEPTED
            elif msg_type == SignalingMessageType.CALL_ACCEPTED.value:
                active_call_id = c_id
                peer_user_id = target_id
                now_iso = datetime.now(timezone.utc).isoformat()
                if c_id:
                    await calling_service.update_call_status(c_id, CallStatus.CONNECTED, started_at=now_iso)

                if target_id:
                    await signaling_manager.send_to_user(target_id, {
                        "type": SignalingMessageType.CALL_ACCEPTED.value,
                        "call_id": c_id,
                        "sender_id": user_id,
                        "payload": data.get("payload", {})
                    })

            # 4. CALL REJECTED
            elif msg_type == SignalingMessageType.CALL_REJECTED.value:
                now_iso = datetime.now(timezone.utc).isoformat()
                if c_id:
                    await calling_service.update_call_status(c_id, CallStatus.REJECTED, ended_at=now_iso, end_reason="Declined by user")

                if target_id:
                    await signaling_manager.send_to_user(target_id, {
                        "type": SignalingMessageType.CALL_REJECTED.value,
                        "call_id": c_id,
                        "sender_id": user_id
                    })
                active_call_id = None
                peer_user_id = None

            # 5. CALL ENDED
            elif msg_type == SignalingMessageType.CALL_ENDED.value:
                now_iso = datetime.now(timezone.utc).isoformat()
                duration = data.get("payload", {}).get("duration_seconds", 0)
                if c_id:
                    await calling_service.update_call_status(
                        c_id,
                        CallStatus.ENDED,
                        ended_at=now_iso,
                        duration_seconds=duration,
                        end_reason=data.get("payload", {}).get("reason", "Call hung up")
                    )

                if target_id:
                    await signaling_manager.send_to_user(target_id, {
                        "type": SignalingMessageType.CALL_ENDED.value,
                        "call_id": c_id,
                        "sender_id": user_id,
                        "payload": data.get("payload", {})
                    })
                active_call_id = None
                peer_user_id = None

            # 6. SDP OFFER / SDP ANSWER / ICE CANDIDATE
            elif msg_type in (
                SignalingMessageType.OFFER.value,
                SignalingMessageType.ANSWER.value,
                SignalingMessageType.ICE_CANDIDATE.value
            ):
                if target_id:
                    await signaling_manager.send_to_user(target_id, {
                        "type": msg_type,
                        "call_id": c_id,
                        "sender_id": user_id,
                        "payload": data.get("payload", {})
                    })

    except WebSocketDisconnect:
        logger.info("WebSocket disconnected for user: %s", user_id)
    except Exception as e:
        logger.error("WebSocket unexpected error for user %s: %s", user_id, str(e))
    finally:
        signaling_manager.disconnect(websocket)
        # If call was still active, inform the peer that call dropped
        if active_call_id and peer_user_id:
            now_iso = datetime.now(timezone.utc).isoformat()
            await calling_service.update_call_status(
                active_call_id,
                CallStatus.ENDED,
                ended_at=now_iso,
                end_reason="Peer disconnected"
            )
            await signaling_manager.send_to_user(peer_user_id, {
                "type": SignalingMessageType.CALL_ENDED.value,
                "call_id": active_call_id,
                "sender_id": user_id,
                "payload": {"reason": "Remote peer lost network connection."}
            })


@router.get(
    "/history",
    response_model=CallHistoryResponse,
    status_code=status.HTTP_200_OK,
    summary="Retrieve Authenticated User's Voice Call History",
    description="Returns call history records for the current user including synthesized security verdicts."
)
async def get_call_history(
    limit: int = Query(50, ge=1, le=100),
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> CallHistoryResponse:
    """Retrieves authenticated call logs."""
    return await calling_service.get_call_history(current_user=current_user, limit=limit)


@router.post(
    "/reports",
    response_model=CallReportResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Record Post-Call Security Audit Report",
    description="Stores the post-call forensic security report generated from Phase 7 Combined Security Engine."
)
async def create_call_report(
    report_in: CallReportCreate,
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> CallReportResponse:
    """Stores a finalized call security report in Firestore."""
    return await calling_service.save_call_report(report_in=report_in, current_user=current_user)


@router.get(
    "/reports/{call_id}",
    response_model=CallReportResponse,
    status_code=status.HTTP_200_OK,
    summary="Retrieve Post-Call Security Audit Report",
    description="Returns full forensic analysis details for a call. Restricted to authorized participants."
)
async def get_call_report(
    call_id: str,
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> CallReportResponse:
    """Fetches post-call security audit report by call ID."""
    return await calling_service.get_call_report(call_id=call_id, current_user=current_user)


@router.get(
    "/status/{call_id}",
    response_model=CallSession,
    status_code=status.HTTP_200_OK,
    summary="Query Call Session Status",
    description="Retrieves live call state. Restricted to call participants."
)
async def get_call_status(
    call_id: str,
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> CallSession:
    """Retrieves call status."""
    return await calling_service.get_call_session(call_id=call_id, user_id=current_user.uid)

