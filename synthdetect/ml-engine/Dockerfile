FROM python:3.11-slim

WORKDIR /app

# System dependencies for OpenCV + Pillow
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download models at build time (baked into image for faster cold start)
COPY app/ ./app/
RUN python -c "from app.services.image_detector import _load_model; _load_model()" || true
RUN python -c "from app.services.text_detector import _load_model; _load_model()" || true

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
