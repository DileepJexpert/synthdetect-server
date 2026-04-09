from fastapi import APIRouter, HTTPException, status
from app.models.schemas import DetectionResult, ImageDetectionRequest, TextDetectionRequest
from app.services import image_detector, text_detector
from app.core.logging import logger

router = APIRouter(prefix="/v1/detect", tags=["Detection"])


@router.post("/image", response_model=DetectionResult)
async def detect_image(request: ImageDetectionRequest):
    try:
        result = image_detector.detect_image(request.image_url)
        return result
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))
    except Exception as e:
        logger.error("image_detection_error", error=str(e))
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Detection failed: {str(e)}"
        )


@router.post("/text", response_model=DetectionResult)
async def detect_text(request: TextDetectionRequest):
    try:
        result = text_detector.detect_text(request.text, request.language or "en")
        return result
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))
    except Exception as e:
        logger.error("text_detection_error", error=str(e))
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Detection failed: {str(e)}"
        )
