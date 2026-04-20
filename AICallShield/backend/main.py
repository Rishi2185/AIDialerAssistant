"""
AICallShield Backend – FastAPI Entry Point

An AI-powered call screening backend providing:
- Speech-to-Text (Whisper)
- AI Reply Generation (GPT/Gemini)
- Text-to-Speech
- Spam Detection & Sentiment Analysis
- Call Record Management
- Real-time WebSocket Communication
"""

import logging
from contextlib import asynccontextmanager
from datetime import datetime

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from models.schemas import HealthResponse
from routers import calls, ai_processing, websocket, twilio

# ── Logging ───────────────────────────────────────────────────────────

logging.basicConfig(
    level=logging.DEBUG if settings.DEBUG else logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


# ── Lifespan ──────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown events."""
    logger.info(f"🛡️  {settings.APP_NAME} v{settings.APP_VERSION} starting...")
    logger.info(f"   OpenAI API: {'configured ✅' if settings.OPENAI_API_KEY else 'not set ❌'}")
    logger.info(f"   Gemini API: {'configured ✅' if settings.GEMINI_API_KEY else 'not set ❌'}")
    yield
    logger.info("🛡️  AICallShield Backend shutting down...")


# ── App ───────────────────────────────────────────────────────────────

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="AI-powered call screening backend for the AICallShield Android app.",
    lifespan=lifespan,
)

# ── CORS ──────────────────────────────────────────────────────────────

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Restrict in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Routers ───────────────────────────────────────────────────────────

app.include_router(calls.router, prefix="/api/v1")
app.include_router(ai_processing.router, prefix="/api/v1")
app.include_router(twilio.router, prefix="/api/v1")
app.include_router(websocket.router)


# ── Root & Health ─────────────────────────────────────────────────────

@app.get("/", tags=["Root"])
async def root():
    return {
        "app": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "docs": "/docs",
        "status": "running",
    }


@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health_check():
    return HealthResponse(
        status="healthy",
        version=settings.APP_VERSION,
        timestamp=datetime.utcnow(),
    )


# ── Run ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
    )
