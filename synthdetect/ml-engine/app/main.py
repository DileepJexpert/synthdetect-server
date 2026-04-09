import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.core.logging import setup_logging, logger
from app.routers import detect, health

setup_logging(debug=settings.debug)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Warm up models in background on startup
    logger.info("ml_engine_starting", version=settings.app_version)
    loop = asyncio.get_event_loop()
    loop.run_in_executor(None, _warm_up_models)
    yield
    logger.info("ml_engine_shutdown")


def _warm_up_models():
    from app.services import image_detector, text_detector
    image_detector._load_model()
    text_detector._load_model()


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="ML inference service for synthetic/AI-generated content detection",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Prometheus metrics
try:
    from prometheus_fastapi_instrumentator import Instrumentator
    Instrumentator().instrument(app).expose(app)
except ImportError:
    pass

app.include_router(health.router)
app.include_router(detect.router)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error("unhandled_exception", path=request.url.path, error=str(exc))
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"},
    )
