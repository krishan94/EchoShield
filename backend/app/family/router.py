from typing import List
from fastapi import APIRouter, Depends, Query, status

from app.auth.models import AuthenticatedUser
from app.auth.security import get_current_user
from app.family.models import (
    FamilyMemberResponse,
    CreateInvitationRequest,
    InvitationResponse,
    InvitationsListResponse,
    ActionResponse,
    UserSearchResponse
)
from app.family.service import family_service

router = APIRouter(prefix="/family", tags=["Family Management"])


@router.get(
    "",
    response_model=List[FamilyMemberResponse],
    summary="List Connected Family Members",
    description="Returns all verified family members currently linked to the authenticated user."
)
async def get_family_circle(
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> List[FamilyMemberResponse]:
    return family_service.get_family_members(current_user.uid)


@router.get(
    "/search",
    response_model=UserSearchResponse,
    summary="Safely Search User By Email",
    description="Confirms if an email belongs to an existing user without leaking private biometric data or account metadata."
)
async def search_family_member(
    email: str = Query(..., description="Registered email to query"),
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> UserSearchResponse:
    return family_service.search_user_by_email(email, current_user)


@router.post(
    "/invitations",
    response_model=InvitationResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Send Family Circle Invitation",
    description="Dispatches a new connection invitation. Prevents duplicate and self-invitations."
)
async def create_family_invitation(
    request: CreateInvitationRequest,
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> InvitationResponse:
    return family_service.send_invitation(
        sender=current_user,
        recipient_email=request.recipient_email,
        relationship=request.relationship
    )


@router.get(
    "/invitations",
    response_model=InvitationsListResponse,
    summary="List Family Invitations",
    description="Retrieves active incoming and outgoing invitations for the authenticated user."
)
async def list_family_invitations(
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> InvitationsListResponse:
    return family_service.get_invitations(current_user)


@router.post(
    "/invitations/{invitation_id}/accept",
    response_model=ActionResponse,
    summary="Accept Family Invitation",
    description="Accepts a pending invitation and creates an authorized family relationship."
)
async def accept_invitation(
    invitation_id: str,
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> ActionResponse:
    return family_service.accept_invitation(invitation_id, current_user)


@router.post(
    "/invitations/{invitation_id}/reject",
    response_model=ActionResponse,
    summary="Decline Family Invitation",
    description="Declines a pending incoming invitation."
)
async def reject_invitation(
    invitation_id: str,
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> ActionResponse:
    return family_service.reject_invitation(invitation_id, current_user)


@router.delete(
    "/invitations/{invitation_id}",
    response_model=ActionResponse,
    summary="Cancel Outgoing Invitation",
    description="Allows the sender to cancel an active pending invitation."
)
async def cancel_invitation(
    invitation_id: str,
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> ActionResponse:
    return family_service.cancel_invitation(invitation_id, current_user)


@router.delete(
    "/{relationship_id}",
    response_model=ActionResponse,
    summary="Remove Family Member Relationship",
    description="Removes a family member from the caller's circle. Does not delete any user accounts."
)
async def remove_family_member(
    relationship_id: str,
    current_user: AuthenticatedUser = Depends(get_current_user)
) -> ActionResponse:
    return family_service.remove_family_relationship(relationship_id, current_user)

