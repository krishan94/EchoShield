import os
from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    PROJECT_NAME: str = "EchoShield Voice Defense Backend"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"

    # Firebase Admin Configuration
    FIREBASE_PROJECT_ID: str = "echoshield-59d63"
    FIREBASE_CREDENTIALS_PATH: Optional[str] = None

    # Server Configuration
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    ENV: str = "development"
    DEBUG: bool = False

    model_config = {
        "env_file": ".env",
        "case_sensitive": True,
        "extra": "ignore"
    }

    def get_firebase_credentials_path(self) -> Optional[str]:
        """
        Locates the Firebase Admin service account JSON file securely.
        Order of resolution:
        1. Explicit FIREBASE_CREDENTIALS_PATH from environment or .env
        2. GOOGLE_APPLICATION_CREDENTIALS from system environment
        3. Standard secure server-side path: backend/secrets/serviceAccountKey.json
        """
        # 1. Configured setting
        if self.FIREBASE_CREDENTIALS_PATH:
            p = Path(self.FIREBASE_CREDENTIALS_PATH).resolve()
            if p.exists() and p.is_file():
                return str(p)

        # 2. Standard Google environment variable
        env_gac = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
        if env_gac:
            p = Path(env_gac).resolve()
            if p.exists() and p.is_file():
                return str(p)

        # 3. Dedicated server-side secrets directory
        repo_root = Path(__file__).resolve().parents[2]
        candidate_paths = [
            repo_root / "backend" / "secrets" / "serviceAccountKey.json",
            repo_root / "backend" / "secrets" / "firebase-service-account.json",
            repo_root / "secrets" / "serviceAccountKey.json",
            Path.cwd() / "backend" / "secrets" / "serviceAccountKey.json",
            Path.cwd() / "secrets" / "serviceAccountKey.json",
        ]

        for candidate in candidate_paths:
            if candidate.exists() and candidate.is_file():
                return str(candidate.resolve())

        return None


settings = Settings()
