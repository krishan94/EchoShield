import logging
from typing import Dict, Any
import os

from fastapi import Depends, HTTPException, Security, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import firebase_admin
from firebase_admin import auth, credentials

from app.core.config import settings
from app.auth.models import AuthenticatedUser

logger = logging.getLogger("echoshield.auth")

# HTTP Bearer authentication scheme
security_scheme = HTTPBearer(
    scheme_name="Firebase ID Token",
    description="Pass Firebase JWT ID token obtained via Android Firebase SDK: 'Bearer <token>'"
)

# Firebase Admin SDK Initialization
_firebase_initialized = False


def initialize_firebase_admin() -> None:
    """
    Initializes the Firebase Admin SDK securely.
    Credentials are loaded from the environment variable or ADC.
    Service account keys are never stored in source control.
    """
    global _firebase_initialized
    if _firebase_initialized or firebase_admin._apps:
        _firebase_initialized = True
        return

    try:
        cred_path = settings.FIREBASE_CREDENTIALS_PATH
        if cred_path and os.path.exists(cred_path):
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred, {
                "projectId": settings.FIREBASE_PROJECT_ID
            })
            logger.info("Firebase Admin initialized with service account certificate.")
        else:
            # Fall back to Google Application Default Credentials or Project ID options
            firebase_admin.initialize_app(options={
                "projectId": settings.FIREBASE_PROJECT_ID
            })
            logger.info("Firebase Admin initialized with default credentials / projectId: %s", settings.FIREBASE_PROJECT_ID)
        _firebase_initialized = True
    except Exception as e:
        logger.warning("Firebase Admin initialization note: %s", str(e))
        _firebase_initialized = True


# Initialize on import
initialize_firebase_admin()


def verify_firebase_token(token: str, check_revoked: bool = True) -> Dict[str, Any]:
    """
    Cryptographically verifies the Firebase ID token and returns decoded claims.
    Enforces expiration and revocation checks.
    """
    initialize_firebase_admin()
    try:
        decoded_claims = auth.verify_id_token(token, check_revoked=check_revoked)
        return decoded_claims
    except auth.ExpiredIdTokenError:
        logger.warning("Rejected expired Firebase ID token.")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication token has expired. Please refresh your session on Android client.",
            headers={"WWW-Authenticate": "Bearer error=\"invalid_token\", error_description=\"token_expired\""}
        )
    except auth.RevokedIdTokenError:
        logger.warning("Rejected revoked Firebase ID token.")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication token has been revoked. Please sign in again.",
            headers={"WWW-Authenticate": "Bearer error=\"invalid_token\", error_description=\"token_revoked\""}
        )
    except auth.InvalidIdTokenError as e:
        logger.warning("Rejected malformed Firebase ID token: %s", str(e))
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication token format or cryptographic signature.",
            headers={"WWW-Authenticate": "Bearer error=\"invalid_token\""}
        )
    except auth.CertificateFetchError as e:
        logger.error("Failed to fetch Google public key certificates: %s", str(e))
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Unable to verify token with identity provider. Please try again shortly."
        )
    except Exception as e:
        logger.error("Unexpected token verification error: %s", str(e))
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication failed. Invalid or unrecognized security token.",
            headers={"WWW-Authenticate": "Bearer"}
        )


async def get_current_user(
    auth_credentials: HTTPAuthorizationCredentials = Security(security_scheme)
) -> AuthenticatedUser:
    """
    FastAPI dependency extracting and verifying user identity strictly from Firebase token.
    Never accepts or trusts client-supplied user IDs in headers, path, or request body.
    """
    token = auth_credentials.credentials
    if not token or not token.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Bearer token missing from Authorization header.",
            headers={"WWW-Authenticate": "Bearer"}
        )

    claims = verify_firebase_token(token.strip())

    uid = claims.get("uid") or claims.get("sub")
    if not uid:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Malformed token: Subject UID claim absent.",
            headers={"WWW-Authenticate": "Bearer"}
        )

    return AuthenticatedUser(
        uid=uid,
        email=claims.get("email"),
        email_verified=claims.get("email_verified", False),
        name=claims.get("name"),
        auth_time=claims.get("auth_time")
    )


async def get_verified_user(
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> AuthenticatedUser:
    """
    Security gate dependency ensuring that the authenticated user has confirmed their email.
    """
    if not current_user.email_verified:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Email address not yet confirmed. Please verify your email before accessing EchoShield protected services."
        )
    return current_user
