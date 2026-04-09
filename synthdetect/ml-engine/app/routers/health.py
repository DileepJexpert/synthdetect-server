from fastapi import APIRouter
from app.models.schemas import HealthResponse
from app.core.config import settings
from app.services import image_detector, text_detector

router = APIRouter(tags=["Health"])


@router.get("/health", response_model=HealthResponse)
async def health():
    return HealthResponse(
        status="ok",
        version=settings.app_version,
        models_loaded={
            "image": image_detector._image_model is not None,
            "text": text_detector._text_model is not None,
        }
    )


@router.get("/")
async def root():
    return {"service": settings.app_name, "version": settings.app_version}
