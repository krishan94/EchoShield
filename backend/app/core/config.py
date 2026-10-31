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


settings = Settings()
