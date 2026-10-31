from typing import Optional
from pydantic import BaseModel, Field


class AuthenticatedUser(BaseModel):
    """
    Cryptographically verified user identity derived strictly from Firebase ID token.
    Never relies on client-supplied user IDs or untrusted request headers.
    """
    uid: str = Field(..., description="Firebase unique user identifier")
    email: Optional[str] = Field(None, description="Family account email address")
    email_verified: bool = Field(False, description="Cryptographic email verification status")
    name: Optional[str] = Field(None, description="Family member display name")
    auth_time: Optional[int] = Field(None, description="Unix timestamp of authentication")


class TokenVerificationRequest(BaseModel):
    token: str = Field(..., description="Firebase ID token to verify")


class TokenVerificationResponse(BaseModel):
    status: str = Field(..., description="Verification outcome: 'valid' or 'revoked'")
    uid: str = Field(..., description="Verified user identifier")
    email: Optional[str] = None
    email_verified: bool = False
    message: str = "Token verified successfully."


class UserProfileResponse(BaseModel):
    uid: str
    email: Optional[str] = None
    email_verified: bool = False
    name: Optional[str] = None
    role: str = "Family Member"

