import logging
import os
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from fastapi import HTTPException, status
import firebase_admin
from firebase_admin import credentials, firestore

from app.core.config import settings

logger = logging.getLogger("echoshield.core.firebase")

_firebase_initialized = False
_using_service_account = False
_credentials_file_path: Optional[str] = None


def initialize_firebase_admin() -> Optional[firebase_admin.App]:
    """
    Initializes the Firebase Admin SDK securely.
    Resolves the service account JSON path from settings/environment,
    falling back to Application Default Credentials if no explicit key is found.
    NEVER logs or exposes private key contents.
    """
    global _firebase_initialized, _using_service_account, _credentials_file_path

    if _firebase_initialized and firebase_admin._apps:
        return firebase_admin.get_app()

    cred_path = settings.get_firebase_credentials_path()

    if cred_path and os.path.exists(cred_path):
        try:
            cred = credentials.Certificate(cred_path)
            app = firebase_admin.initialize_app(cred, {
                "projectId": settings.FIREBASE_PROJECT_ID
            })
            _firebase_initialized = True
            _using_service_account = True
            _credentials_file_path = cred_path
            logger.info("Firebase Admin initialized successfully using service account: %s", cred_path)
            return app
        except Exception as e:
            logger.error(
                "Failed to initialize Firebase Admin with certificate at '%s': %s",
                cred_path, str(e)
            )
            raise

    # Fall back to default application credentials or project ID options
    try:
        app = firebase_admin.initialize_app(options={
            "projectId": settings.FIREBASE_PROJECT_ID
        })
        _firebase_initialized = True
        _using_service_account = False
        logger.info("Firebase Admin initialized with default credentials / project: %s", settings.FIREBASE_PROJECT_ID)
        return app
    except Exception as e:
        logger.warning("Firebase Admin default credential initialization note: %s", str(e))
        _firebase_initialized = True
        return None


def is_service_account_configured() -> bool:
    """Returns True if Firebase Admin was initialized with a valid service account JSON."""
    return _using_service_account


def get_credentials_path() -> Optional[str]:
    """Returns the path to the active service account credentials file if loaded."""
    return _credentials_file_path


def get_firestore_client() -> firestore.Client:
    """
    Obtains the authenticated Google Cloud Firestore client.
    Raises HTTP 503 if Firebase Admin or Firestore is unavailable.
    """
    initialize_firebase_admin()
    try:
        client = firestore.client()
        return client
    except Exception as e:
        logger.error("Firestore client acquisition failed: %s", str(e))
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=(
                "Firestore database service is unavailable. "
                "Ensure a valid Firebase Admin service account JSON is placed at "
                "'backend/secrets/serviceAccountKey.json' or set via the FIREBASE_CREDENTIALS_PATH environment variable."
            )
        )


def verify_firestore_connection() -> Dict[str, Any]:
    """
    Performs a real read-write-delete roundtrip in Firestore to verify
    service account permissions and end-to-end database connectivity.
    """
    db = get_firestore_client()
    now_iso = datetime.now(timezone.utc).isoformat()
    test_doc_ref = db.collection("_connection_test").document("ping")

    try:
        # 1. Write harmless test document
        test_doc_ref.set({
            "test": True,
            "service": "EchoShield Backend",
            "verified_at": now_iso,
            "project_id": settings.FIREBASE_PROJECT_ID
        })

        # 2. Read it back
        snapshot = test_doc_ref.get()
        if not snapshot.exists:
            raise RuntimeError("Test document was written but could not be read back from Firestore.")

        read_data = snapshot.to_dict()

        # 3. Clean up by deleting the test document
        test_doc_ref.delete()

        return {
            "status": "success",
            "message": "Firestore read, write, and delete operations verified successfully.",
            "project_id": settings.FIREBASE_PROJECT_ID,
            "timestamp": now_iso,
            "written_data": read_data
        }
    except Exception as e:
        # Try to delete if left behind
        try:
            test_doc_ref.delete()
        except Exception:
            pass
        raise RuntimeError(f"Firestore verification failed: {str(e)}")

