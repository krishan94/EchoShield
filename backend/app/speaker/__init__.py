"""
EchoShield — Speaker Recognition API Package
============================================
"""

from app.speaker.models import (
    ClearHistoryResponse,
    VerificationHistoryItem,
    VerificationHistoryResponse,
    VerificationResponse,
)
from app.speaker.router import router

__all__ = [
    "router",
    "VerificationResponse",
    "VerificationHistoryItem",
    "VerificationHistoryResponse",
    "ClearHistoryResponse",
]

