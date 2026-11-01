"""
EchoShield — Calling & Security Audit Service Layer
===================================================
Orchestrates voice call sessions, verifies family circle authorization,
persists call lifecycle events, and securely manages post-call security
reports in Google Cloud Firestore.
"""

import logging
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from fastapi import HTTPException, status
from app.core.firebase import get_firestore_client
from app.auth.models import AuthenticatedUser
from app.calling.models import (
    CallHistoryItem,
    CallHistoryResponse,
    CallReportCreate,
    CallReportResponse,
    CallSession,
    CallStatus,
    CallSecurityOutcome,
)

logger = logging.getLogger("echoshield.calling.service")


class CallingService:
    """
    Business logic and Firestore persistence layer for EchoShield calls
    and post-call security audit reports.
    """

    def __init__(self, use_in_memory: bool = False):
        self.use_in_memory = use_in_memory
        # In-memory stores used for hermetic unit testing
        self._mem_calls: Dict[str, Dict[str, Any]] = {}
        self._mem_reports: Dict[str, Dict[str, Any]] = {}
        self._mem_relationships: Dict[str, Dict[str, Any]] = {}

    def _get_firestore(self):
        """Retrieves Firestore client in production mode."""
        if self.use_in_memory:
            return None
        return get_firestore_client()

    async def verify_family_relationship(self, user_a_id: str, user_b_id: str) -> bool:
        """
        Verifies that both users belong to an active, accepted family circle.
        Calls are restricted to verified family members to prevent spoofed
        or spam unsolicited call attacks.
        """
        if user_a_id == user_b_id:
            return False

        if self.use_in_memory:
            for rel in self._mem_relationships.values():
                members = rel.get("members", [])
                if user_a_id in members and user_b_id in members:
                    return True
            return False

        # Production path: query Firestore family_relationships
        try:
            db = self._get_firestore()
            rel_ref = db.collection("family_relationships")
            query = rel_ref.where("members", "array_contains", user_a_id).stream()
            for doc in query:
                data = doc.to_dict()
                if user_b_id in data.get("members", []):
                    return True
            return False
        except Exception as e:
            logger.error("Failed to verify family relationship between %s and %s: %s", user_a_id, user_b_id, str(e))
            # On firestore error, do not allow unverified connections
            return False

    async def create_call_session(
        self,
        caller_id: str,
        caller_name: str,
        callee_id: str,
        callee_name: str,
        call_id: Optional[str] = None
    ) -> CallSession:
        """Creates and stores a new call session record with INITIATING status."""
        active_call_id = call_id or f"call_{uuid.uuid4().hex[:12]}"
        now_iso = datetime.now(timezone.utc).isoformat()

        session = CallSession(
            call_id=active_call_id,
            caller_id=caller_id,
            caller_name=caller_name,
            callee_id=callee_id,
            callee_name=callee_name,
            status=CallStatus.INITIATING,
            created_at=now_iso,
            duration_seconds=0
        )

        doc_data = session.model_dump()

        if self.use_in_memory:
            self._mem_calls[active_call_id] = doc_data
            return session

        try:
            db = self._get_firestore()
            db.collection("calls").document(active_call_id).set(doc_data)
            return session
        except Exception as e:
            logger.error("Failed to persist call session %s in Firestore: %s", active_call_id, str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to record call session: {str(e)}"
            )

    async def update_call_status(
        self,
        call_id: str,
        call_status: CallStatus,
        started_at: Optional[str] = None,
        ended_at: Optional[str] = None,
        duration_seconds: Optional[int] = None,
        end_reason: Optional[str] = None
    ) -> Optional[CallSession]:
        """Updates the status and lifecycle timestamps of an active call."""
        update_data: Dict[str, Any] = {"status": call_status.value}
        if started_at:
            update_data["started_at"] = started_at
        if ended_at:
            update_data["ended_at"] = ended_at
        if duration_seconds is not None:
            update_data["duration_seconds"] = duration_seconds
        if end_reason:
            update_data["end_reason"] = end_reason

        if self.use_in_memory:
            data = self._mem_calls.get(call_id)
            if not data:
                return None
            data.update(update_data)
            return CallSession(**data)

        try:
            db = self._get_firestore()
            doc_ref = db.collection("calls").document(call_id)
            doc = doc_ref.get()
            if not doc.exists:
                return None
            doc_ref.update(update_data)
            full_data = doc.to_dict()
            full_data.update(update_data)
            return CallSession(**full_data)
        except Exception as e:
            logger.error("Failed to update call status for %s: %s", call_id, str(e))
            return None

    async def get_call_session(self, call_id: str, user_id: str) -> CallSession:
        """
        Retrieves a call session record.
        Strictly enforces that requesting user is either the caller or callee.
        """
        data: Optional[Dict[str, Any]] = None

        if self.use_in_memory:
            data = self._mem_calls.get(call_id)
        else:
            try:
                db = self._get_firestore()
                doc = db.collection("calls").document(call_id).get()
                if doc.exists:
                    data = doc.to_dict()
            except Exception as e:
                logger.error("Firestore read error for call %s: %s", call_id, str(e))
                raise HTTPException(
                    status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                    detail=f"Database error retrieving call: {str(e)}"
                )

        if not data:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Call session '{call_id}' not found."
            )

        # Authorization check
        if user_id != data.get("caller_id") and user_id != data.get("callee_id"):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Security violation: You are not authorized to view this call session."
            )

        return CallSession(**data)

    async def save_call_report(
        self,
        report_in: CallReportCreate,
        current_user: AuthenticatedUser
    ) -> CallReportResponse:
        """
        Persists a post-call security audit report generated from Phase 7 Combined Security Engine.
        Enforces that the submitting user was an authorized participant in the call.
        """
        # Authorization check
        if current_user.uid != report_in.caller_id and current_user.uid != report_in.callee_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Security violation: Only call participants may submit security audit reports."
            )

        report_id = f"rep_{uuid.uuid4().hex[:12]}"
        now_iso = datetime.now(timezone.utc).isoformat()

        report_dict = report_in.model_dump()
        report_dict["report_id"] = report_id
        report_dict["created_at"] = now_iso
        report_dict["disclaimer"] = (
            "EchoShield Voice Security Engine provides calibrated probabilistic defense against voice impersonation. "
            "Audio analysis is performed asynchronously on remote caller streams without raw audio storage. "
            "Never use voice alone to authorize irrevocable financial transactions."
        )

        response = CallReportResponse(**report_dict)

        if self.use_in_memory:
            self._mem_reports[report_in.call_id] = report_dict
            return response

        try:
            db = self._get_firestore()
            # Store in call_reports collection keyed by call_id for fast lookup
            db.collection("call_reports").document(report_in.call_id).set(report_dict)
            logger.info("Saved post-call security report %s for call %s", report_id, report_in.call_id)
            return response
        except Exception as e:
            logger.error("Failed to persist call report in Firestore: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to record call report in Firestore: {str(e)}"
            )

    async def get_call_report(self, call_id: str, current_user: AuthenticatedUser) -> CallReportResponse:
        """
        Retrieves the post-call security audit report for a specific call.
        Strictly enforces that the user was either caller or callee.
        """
        data: Optional[Dict[str, Any]] = None

        if self.use_in_memory:
            data = self._mem_reports.get(call_id)
        else:
            try:
                db = self._get_firestore()
                doc = db.collection("call_reports").document(call_id).get()
                if doc.exists:
                    data = doc.to_dict()
            except Exception as e:
                logger.error("Firestore read error for report of call %s: %s", call_id, str(e))
                raise HTTPException(
                    status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                    detail=f"Database error retrieving call report: {str(e)}"
                )

        if not data:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"No security audit report exists for call '{call_id}'."
            )

        # Authorization: caller or callee
        if current_user.uid != data.get("caller_id") and current_user.uid != data.get("callee_id"):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Security violation: You are not authorized to view this security audit report."
            )

        return CallReportResponse(**data)

    async def get_call_history(
        self,
        current_user: AuthenticatedUser,
        limit: int = 50
    ) -> CallHistoryResponse:
        """
        Retrieves call history for the authenticated user, including post-call
        security verdicts where available.
        """
        items: List[CallHistoryItem] = []

        if self.use_in_memory:
            for call in self._mem_calls.values():
                if call["caller_id"] == current_user.uid or call["callee_id"] == current_user.uid:
                    is_outgoing = call["caller_id"] == current_user.uid
                    direction = "outgoing" if is_outgoing else "incoming"
                    other_name = call["callee_name"] if is_outgoing else call["caller_name"]
                    other_id = call["callee_id"] if is_outgoing else call["caller_id"]

                    report = self._mem_reports.get(call["call_id"])
                    items.append(CallHistoryItem(
                        call_id=call["call_id"],
                        caller_id=call["caller_id"],
                        caller_name=call["caller_name"],
                        callee_id=call["callee_id"],
                        callee_name=call["callee_name"],
                        direction=direction,
                        other_party_name=other_name,
                        other_party_id=other_id,
                        status=CallStatus(call["status"]),
                        duration_seconds=call.get("duration_seconds", 0),
                        security_outcome=report.get("overall_security_outcome") if report else None,
                        security_headline=report.get("security_verdict_headline") if report else None,
                        has_report=report is not None,
                        created_at=call["created_at"]
                    ))
            # Sort descending by creation
            items.sort(key=lambda x: x.created_at, reverse=True)
            return CallHistoryResponse(calls=items[:limit], total_count=len(items))

        # Production path: Firestore queries
        try:
            db = self._get_firestore()
            calls_ref = db.collection("calls")

            # Outgoing calls
            out_docs = calls_ref.where("caller_id", "==", current_user.uid).limit(limit).stream()
            # Incoming calls
            in_docs = calls_ref.where("callee_id", "==", current_user.uid).limit(limit).stream()

            combined_docs = {}
            for doc in out_docs:
                combined_docs[doc.id] = doc.to_dict()
            for doc in in_docs:
                combined_docs[doc.id] = doc.to_dict()

            # Batch query or check reports for these calls
            for call_id, call in combined_docs.items():
                is_outgoing = call.get("caller_id") == current_user.uid
                direction = "outgoing" if is_outgoing else "incoming"
                other_name = call.get("callee_name", "Family Member") if is_outgoing else call.get("caller_name", "Family Member")
                other_id = call.get("callee_id", "") if is_outgoing else call.get("caller_id", "")

                # Fetch report if present
                report_doc = db.collection("call_reports").document(call_id).get()
                report_data = report_doc.to_dict() if report_doc.exists else None

                items.append(CallHistoryItem(
                    call_id=call_id,
                    caller_id=call.get("caller_id", ""),
                    caller_name=call.get("caller_name", ""),
                    callee_id=call.get("callee_id", ""),
                    callee_name=call.get("callee_name", ""),
                    direction=direction,
                    other_party_name=other_name,
                    other_party_id=other_id,
                    status=CallStatus(call.get("status", CallStatus.INITIATING.value)),
                    duration_seconds=call.get("duration_seconds", 0),
                    security_outcome=report_data.get("overall_security_outcome") if report_data else None,
                    security_headline=report_data.get("security_verdict_headline") if report_data else None,
                    has_report=report_data is not None,
                    created_at=call.get("created_at", "")
                ))

            items.sort(key=lambda x: x.created_at, reverse=True)
            return CallHistoryResponse(calls=items[:limit], total_count=len(items))

        except Exception as e:
            logger.error("Failed to query call history from Firestore: %s", str(e))
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to query call history: {str(e)}"
            )


# Default calling service singleton
calling_service = CallingService()

