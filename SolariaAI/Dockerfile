# CPU-only inference + teacher-console validation, not a classroom service.
# Includes review/override tests; no student dataset, camera or real model required.
FROM python:3.12-slim-bookworm
LABEL org.opencontainers.image.title="SolariaAI synthetic test runner" \
      org.opencontainers.image.description="Lint, schema, inference, teacher review and override validation; no classroom UI port"

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    RUFF_CACHE_DIR=/tmp/ruff \
    OMP_NUM_THREADS=1 \
    MKL_NUM_THREADS=1 \
    OPENBLAS_NUM_THREADS=1

WORKDIR /app

# Install CPU wheels explicitly before resolving the project's test extras.
RUN python -m pip install --no-cache-dir torch torchvision \
        --index-url https://download.pytorch.org/whl/cpu
COPY pyproject.toml ./
COPY solaria_classifier/ ./solaria_classifier/
COPY solaria_vision/ ./solaria_vision/
RUN python -m pip install --no-cache-dir '.[dev,service,export]' \
    && python -m pip check

COPY tests/ ./tests/
COPY docs/schemas/ ./docs/schemas/
# Windows build contexts may mark every file executable; Python modules are not scripts.
RUN find /app -type f -exec chmod 0644 {} + \
    && useradd --create-home --uid 10001 solaria
USER solaria

# Deliberately no EXPOSE: smoke and service communicate on container loopback.
CMD ["python", "-m", "solaria_classifier.testing"]
