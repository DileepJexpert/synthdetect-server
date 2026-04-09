from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "SynthDetect ML Engine"
    app_version: str = "1.0.0"
    debug: bool = False

    # Model settings
    image_model_name: str = "umm-maybe/AI-image-detector"
    text_model_name: str = "roberta-base-openai-detector"
    device: str = "cpu"  # "cuda" if GPU available
    model_cache_dir: str = "/app/model_cache"

    # Inference settings
    image_max_size_px: int = 1024
    text_max_tokens: int = 512
    confidence_threshold_synthetic: float = 0.7
    confidence_threshold_authentic: float = 0.4

    # Supported languages for text detection
    supported_languages: list[str] = ["en", "hi", "ta", "te", "bn", "mr", "kn", "gu"]

    class Config:
        env_file = ".env"


settings = Settings()
