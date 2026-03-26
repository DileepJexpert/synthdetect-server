import time
import io
import httpx
from PIL import Image
import numpy as np
from typing import Optional

from app.core.config import settings
from app.core.logging import logger
from app.models.schemas import DetectionResult, Signal

# Lazy-loaded model globals
_image_model = None
_image_processor = None
_model_version = "v1.0-mock"


def _load_model():
    global _image_model, _image_processor, _model_version
    if _image_model is not None:
        return

    try:
        from transformers import AutoFeatureExtractor, AutoModelForImageClassification
        import torch

        logger.info("loading_image_model", model=settings.image_model_name)
        _image_processor = AutoFeatureExtractor.from_pretrained(
            settings.image_model_name,
            cache_dir=settings.model_cache_dir,
        )
        _image_model = AutoModelForImageClassification.from_pretrained(
            settings.image_model_name,
            cache_dir=settings.model_cache_dir,
        )
        _image_model.eval()
        _model_version = f"transformers-{settings.image_model_name.split('/')[-1]}"
        logger.info("image_model_loaded", version=_model_version)

    except Exception as e:
        logger.warning("image_model_load_failed", error=str(e), fallback="heuristic")
        _image_model = None
        _image_processor = None
        _model_version = "v1.0-heuristic"


def _download_image(url: str) -> Image.Image:
    with httpx.Client(timeout=15.0, follow_redirects=True) as client:
        response = client.get(url)
        response.raise_for_status()
        content_type = response.headers.get("content-type", "")
        if "image" not in content_type:
            raise ValueError(f"URL does not point to an image (content-type: {content_type})")
        return Image.open(io.BytesIO(response.content)).convert("RGB")


def _heuristic_image_analysis(image: Image.Image) -> dict:
    """
    Fallback heuristic analysis when ML model is unavailable.
    Uses statistical image properties as proxy signals.
    """
    import cv2
    img_array = np.array(image)

    # Signal 1: Noise analysis — AI images tend to have uniform noise patterns
    gray = cv2.cvtColor(img_array, cv2.COLOR_RGB2GRAY)
    laplacian_var = cv2.Laplacian(gray, cv2.CV_64F).var()
    noise_score = min(1.0, laplacian_var / 500.0)

    # Signal 2: Color distribution uniformity
    r_std = float(img_array[:, :, 0].std())
    g_std = float(img_array[:, :, 1].std())
    b_std = float(img_array[:, :, 2].std())
    color_uniformity = 1.0 - min(1.0, (abs(r_std - g_std) + abs(g_std - b_std)) / 100.0)

    # Signal 3: High-frequency detail (AI images often lack fine detail)
    freq = np.fft.fft2(gray)
    freq_shift = np.fft.fftshift(freq)
    magnitude = np.abs(freq_shift)
    h, w = magnitude.shape
    center_ratio = magnitude[h//4:3*h//4, w//4:3*w//4].sum() / magnitude.sum()
    hf_score = float(1.0 - center_ratio)

    # Signal 4: Texture regularity (AI images tend to have regular textures)
    texture_score = min(1.0, noise_score * 0.5 + hf_score * 0.5)

    composite = (color_uniformity * 0.3 + (1 - noise_score) * 0.4 + (1 - hf_score) * 0.3)

    return {
        "composite": composite,
        "signals": [
            Signal(name="noise_pattern", value=round(1 - noise_score, 4), weight=0.40,
                   description="Uniform noise typical of AI-generated images"),
            Signal(name="color_distribution", value=round(color_uniformity, 4), weight=0.30,
                   description="Color channel distribution uniformity"),
            Signal(name="high_frequency_detail", value=round(1 - hf_score, 4), weight=0.30,
                   description="High-frequency detail presence (AI images lack fine detail)"),
        ]
    }


def detect_image(image_url: str) -> DetectionResult:
    start_ms = int(time.time() * 1000)
    _load_model()

    image = _download_image(image_url)

    # Resize for performance
    max_px = settings.image_max_size_px
    if max(image.size) > max_px:
        image.thumbnail((max_px, max_px), Image.LANCZOS)

    if _image_model is not None and _image_processor is not None:
        # Use transformer model
        import torch
        inputs = _image_processor(images=image, return_tensors="pt")
        with torch.no_grad():
            outputs = _image_model(**inputs)
            probs = torch.softmax(outputs.logits, dim=-1)[0]

        labels = _image_model.config.id2label
        synthetic_prob = 0.0
        for idx, label in labels.items():
            if "fake" in label.lower() or "ai" in label.lower() or "generated" in label.lower():
                synthetic_prob = float(probs[idx])
                break
        if synthetic_prob == 0.0:
            synthetic_prob = float(probs[1]) if len(probs) > 1 else float(probs[0])

        confidence = round(synthetic_prob, 4)
        signals = [
            Signal(name="model_confidence", value=confidence, weight=1.0,
                   description=f"Direct model output probability ({_model_version})"),
        ]
    else:
        # Fallback to heuristics
        result = _heuristic_image_analysis(image)
        confidence = round(float(result["composite"]), 4)
        signals = result["signals"]

    is_synthetic = confidence >= settings.confidence_threshold_synthetic
    elapsed = int(time.time() * 1000) - start_ms

    logger.info("image_detection_complete",
                url=image_url[:60],
                confidence=confidence,
                is_synthetic=is_synthetic,
                elapsed_ms=elapsed)

    return DetectionResult(
        isSynthetic=is_synthetic,
        confidenceScore=confidence,
        modelVersion=_model_version,
        processingMs=elapsed,
        signals=signals,
    )
