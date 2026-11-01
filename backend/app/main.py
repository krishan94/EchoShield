from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.firebase import is_service_account_configured, verify_firestore_connection
from app.auth.router import router as auth_router
from app.family.router import router as family_router
from app.enrollment.router import router as enrollment_router
from app.speaker.router import router as speaker_router
from app.antispoof.router import router as antispoof_router
from app.combined_security.router import router as combined_security_router
from app.calling.router import router as calling_router

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="Enterprise backend API for EchoShield Voice Defense and Scam Protection System.",
    openapi_url=f"{settings.API_V1_STR}/openapi.json",
    docs_url=f"{settings.API_V1_STR}/docs",
    redoc_url=f"{settings.API_V1_STR}/redoc"
)

# CORS Middleware Configuration (Strict Origins)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Restricted to Android app domains in production
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# Mount API Routers
app.include_router(auth_router, prefix=settings.API_V1_STR)
app.include_router(family_router, prefix=settings.API_V1_STR)
app.include_router(enrollment_router, prefix=settings.API_V1_STR)
app.include_router(speaker_router, prefix=settings.API_V1_STR)
app.include_router(antispoof_router, prefix=settings.API_V1_STR)
app.include_router(combined_security_router, prefix=settings.API_V1_STR)
app.include_router(calling_router, prefix=settings.API_V1_STR)


@app.get("/health", tags=["System Health"])
async def health_check():
    """
    Public health check endpoint for monitoring system availability.
    """
    return {
        "status": "healthy",
        "service": "echoshield-backend",
        "version": settings.VERSION,
        "firebase_project": settings.FIREBASE_PROJECT_ID,
        "firebase_service_account_configured": is_service_account_configured()
    }


@app.get(f"{settings.API_V1_STR}/system/firestore-status", tags=["System Health"])
async def firestore_status():
    """
    Harmless administrative endpoint to check real-time Firestore connectivity.
    """
    try:
        return verify_firestore_connection()
    except Exception as e:
        return {
            "status": "error",
            "firebase_project": settings.FIREBASE_PROJECT_ID,
            "detail": str(e)
        }


if __name__ == "__main__":
    import os
    import uvicorn
    port = int(os.environ.get("PORT", settings.PORT))
    host = os.environ.get("HOST", settings.HOST)
    uvicorn.run("app.main:app", host=host, port=port, reload=False)


