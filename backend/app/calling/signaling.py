"""
EchoShield — WebRTC Calling WebSocket Connection & Signalling Manager
=====================================================================
Manages authenticated real-time WebSocket connections, route signalling
payloads (SDP offer/answer, ICE candidates, call status transitions)
between family members, and cleanly handles peer disconnections.
"""

import json
import logging
from typing import Any, Dict, Optional, Set
from fastapi import WebSocket

logger = logging.getLogger("echoshield.calling.signaling")


class SignalingConnectionManager:
    """
    In-memory registry of active WebSocket connections for WebRTC signalling.
    Maps authenticated user IDs to their active WebSocket instances.
    Thread-safe within asyncio event loop.
    """

    def __init__(self):
        # Maps user_id -> set of active WebSockets (supports multiple device endpoints)
        self._active_connections: Dict[str, Set[WebSocket]] = {}
        # Reverse mapping: websocket -> user_id
        self._socket_to_user: Dict[WebSocket, str] = {}

    def is_user_online(self, user_id: str) -> bool:
        """Checks if a user currently has at least one active signalling socket."""
        return user_id in self._active_connections and len(self._active_connections[user_id]) > 0

    async def connect(self, websocket: WebSocket, user_id: str):
        """Registers an accepted WebSocket connection for the given user."""
        if user_id not in self._active_connections:
            self._active_connections[user_id] = set()
        self._active_connections[user_id].add(websocket)
        self._socket_to_user[websocket] = user_id
        logger.info("Signalling connected: user '%s' (active sockets: %d)", user_id, len(self._active_connections[user_id]))

    def disconnect(self, websocket: WebSocket):
        """Removes a disconnected WebSocket from the registry."""
        user_id = self._socket_to_user.pop(websocket, None)
        if user_id and user_id in self._active_connections:
            self._active_connections[user_id].discard(websocket)
            if not self._active_connections[user_id]:
                del self._active_connections[user_id]
            logger.info("Signalling disconnected: user '%s'", user_id)

    async def send_to_user(self, user_id: str, message: Dict[str, Any]) -> bool:
        """
        Sends a JSON signalling message to all active sockets for the specified user.
        Returns True if delivered to at least one socket, False otherwise.
        """
        sockets = self._active_connections.get(user_id)
        if not sockets:
            logger.debug("Cannot send signalling message: user '%s' is offline", user_id)
            return False

        dead_sockets = set()
        delivered = False
        payload_text = json.dumps(message)

        for ws in list(sockets):
            try:
                await ws.send_text(payload_text)
                delivered = True
            except Exception as e:
                logger.warning("Failed sending signalling payload to user '%s': %s", user_id, str(e))
                dead_sockets.add(ws)

        for dead_ws in dead_sockets:
            self.disconnect(dead_ws)

        return delivered

    def get_online_users(self) -> Set[str]:
        """Returns set of all user IDs with at least one active connection."""
        return set(self._active_connections.keys())


# Singleton signalling manager instance
signaling_manager = SignalingConnectionManager()

