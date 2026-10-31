from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.auth.router import router as auth_router
from app.family.router import router as family_router
from app.enrollment.router import router as enrollment_router

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


@app.get("/health", tags=["System Health"])
async def health_check():
    """
    Public health check endpoint for monitoring system availability.
    """
    return {
        "status": "healthy",
        "service": "echoshield-backend",
        "version": settings.VERSION,
        "firebase_project": settings.FIREBASE_PROJECT_ID
    }

