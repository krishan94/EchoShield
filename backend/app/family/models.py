from typing import Optional, List
from pydantic import BaseModel, Field, field_validator


class FamilyMemberResponse(BaseModel):
    id: str = Field(..., description="Unique family relationship document ID")
    member_id: str = Field(..., description="Firebase UID of the connected family member")
    name: str = Field(..., description="Display name of the family member")
    email: str = Field(..., description="Registered email of the family member")
    relationship: str = Field(..., description="Family role / relationship classification")
    is_online: bool = Field(False, description="Presence status")
    is_voice_enrolled: bool = Field(False, description="Biometric voice profile enrollment state")
    voice_quality_score: float = Field(0.0, description="Biometric voice enrollment quality score (0.0 to 1.0)")
    last_active: str = Field("Recently", description="Human-readable last active indicator")
    photo_url: Optional[str] = None


class CreateInvitationRequest(BaseModel):
    recipient_email: str = Field(..., description="Registered email of the family member to invite")
    relationship: str = Field("Other Relative", description="Proposed relationship type (e.g. Spouse, Parent, Child, Sibling)")

    @field_validator("recipient_email")
    @classmethod
    def validate_recipient_email(cls, v: str) -> str:
        clean = v.strip().lower()
        if not clean or "@" not in clean or "." not in clean.split("@")[-1]:
            raise ValueError("Invalid email address format.")
        return clean


class InvitationResponse(BaseModel):
    id: str = Field(..., description="Unique invitation ID")
    sender_id: str
    sender_name: str
    sender_email: str
    recipient_email: str
    recipient_id: Optional[str] = None
    relationship: str
    status: str = Field(..., description="'pending', 'accepted', 'rejected', or 'cancelled'")
    created_at: str


class InvitationsListResponse(BaseModel):
    incoming: List[InvitationResponse] = []
    outgoing: List[InvitationResponse] = []


class ActionResponse(BaseModel):
    status: str
    message: str
    relationship_id: Optional[str] = None


class UserSearchResponse(BaseModel):
    exists: bool
    email: str
    name: Optional[str] = None
