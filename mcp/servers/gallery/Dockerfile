FROM python:3.13-slim
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 \
    OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=2 MKL_NUM_THREADS=2 \
    GALLERY_DATA=/data GALLERY_ASSETS=/models GALLERY_BIND=0.0.0.0
WORKDIR /app
COPY requirements.txt requirements-models.txt ./
RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.14.1 torchvision==0.29.1 \
    && pip install --no-cache-dir -r requirements-models.txt \
    && python -c "import cv2, torch, torchvision, pillow_heif" \
    && useradd --uid 10001 --create-home gallery \
    && mkdir /data && chown gallery:gallery /data
COPY gallery_mcp ./gallery_mcp
COPY pipeline ./pipeline
USER 10001:10001
EXPOSE 8818
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8818/healthz',timeout=2)"
CMD ["python", "-m", "gallery_mcp.server"]
