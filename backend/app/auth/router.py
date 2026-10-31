from fastapi import APIRouter, Depends

from app.auth.models import (
    AuthenticatedUser,
    TokenVerificationRequest,
    TokenVerificationResponse,
    UserProfileResponse
)
from app.auth import security
from app.auth.security import get_current_user, get_verified_user

router = APIRouter(prefix="/auth", tags=["Authentication & Identity"])


@router.get(
    "/me",
    response_model=UserProfileResponse,
    summary="Retrieve Authenticated User Profile",
    description="Returns verified user identity derived exclusively from the validated Firebase ID token."
)
async def get_my_profile(
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> UserProfileResponse:
    return UserProfileResponse(
        uid=current_user.uid,
        email=current_user.email,
        email_verified=current_user.email_verified,
        name=current_user.name,
        role="Family Member"
    )


@router.post(
    "/verify-token",
    response_model=TokenVerificationResponse,
    summary="Explicit Token Validation",
    description="Validates a raw Firebase ID token and returns extracted identity details."
)
async def verify_token_endpoint(
    request: TokenVerificationRequest
) -> TokenVerificationResponse:
    claims = security.verify_firebase_token(request.token)
    uid = claims.get("uid") or claims.get("sub", "")
    return TokenVerificationResponse(
        status="valid",
        uid=uid,
        email=claims.get("email"),
        email_verified=claims.get("email_verified", False),
        message="Cryptographic token verified successfully."
    )


@router.get(
    "/protected-check",
    response_model=UserProfileResponse,
    summary="Check Email Verification Gating",
    description="Confirms user is not only authenticated but also possesses verified email status."
)
async def check_email_verified_access(
    verified_user: AuthenticatedUser = Depends(get_verified_user)
) -> UserProfileResponse:
    return UserProfileResponse(
        uid=verified_user.uid,
        email=verified_user.email,
        email_verified=verified_user.email_verified,
        name=verified_user.name,
        role="Verified Family Member"
    )
