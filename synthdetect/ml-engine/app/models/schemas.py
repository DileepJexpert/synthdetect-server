from pydantic import BaseModel, HttpUrl, field_validator
from typing import Optional


class Signal(BaseModel):
    name: str
    value: float
    weight: float
    description: str


class DetectionResult(BaseModel):
    isSynthetic: bool
    confidenceScore: float
    modelVersion: str
    processingMs: int
    signals: list[Signal]


class ImageDetectionRequest(BaseModel):
    image_url: str

    @field_validator("image_url")
    @classmethod
    def validate_url(cls, v: str) -> str:
        if not v.startswith(("http://", "https://")):
            raise ValueError("image_url must be a valid HTTP/HTTPS URL")
        return v


class TextDetectionRequest(BaseModel):
    text: str
    language: Optional[str] = "en"

    @field_validator("text")
    @classmethod
    def validate_text(cls, v: str) -> str:
        if len(v.strip()) < 50:
            raise ValueError("text must be at least 50 characters")
        if len(v) > 100_000:
            raise ValueError("text must not exceed 100,000 characters")
        return v


class HealthResponse(BaseModel):
    status: str
    version: str
    models_loaded: dict[str, bool]
