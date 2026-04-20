"""
Twilio voice webhook routes integrated with AICallShield AI services.

Flow:
1. Incoming call webhook creates/loads a call session.
2. Twilio speech gather captures caller speech.
3. Existing AI engine generates screening response.
4. Existing TTS service is used for playback when available.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional
from uuid import uuid4
from xml.sax.saxutils import escape

from fastapi import APIRouter, Request
from fastapi.responses import Response

from database.local_storage import create_call_record, get_call_record, update_call_record
from models.schemas import CallStatus, ChatMessage, RiskLevel, SenderType
from services.ai_engine import ASSISTANT_GREETING_PREFIX, generate_ai_reply
from services.text_to_speech import text_to_speech

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/twilio", tags=["Twilio"])

TWILIO_USER_ID = "twilio"
SPEECH_LANGUAGE = "en-IN"
END_CALL_PHRASES = {
    "bye",
    "goodbye",
    "stop",
    "end call",
    "disconnect",
    "hang up",
    "hangup",
}
TERMINAL_CALL_STATES = {"completed", "busy", "failed", "no-answer", "canceled"}
_AUDIO_CACHE_TTL_SECONDS = 30 * 60


@dataclass
class TwilioCallSession:
    call_sid: str
    call_id: str
    caller_number: str
    caller_name: str = ""
    conversation_history: list[ChatMessage] = field(default_factory=list)
    started_at: datetime = field(default_factory=datetime.utcnow)


_ACTIVE_CALLS: dict[str, TwilioCallSession] = {}
_AUDIO_CACHE: dict[str, tuple[bytes, datetime]] = {}


def _xml_response(inner_xml: str) -> Response:
    payload = f"""<?xml version="1.0" encoding="UTF-8"?>
<Response>
{inner_xml}
</Response>"""
    return Response(content=payload, media_type="application/xml")


async def _extract_twilio_payload(request: Request) -> dict[str, str]:
    if request.method == "POST":
        form_data = await request.form()
        return {str(k): str(v) for k, v in form_data.items()}
    return {str(k): str(v) for k, v in request.query_params.items()}


def _cleanup_audio_cache() -> None:
    now = datetime.utcnow()
    stale_ids = [
        media_id
        for media_id, (_, created_at) in _AUDIO_CACHE.items()
        if (now - created_at).total_seconds() > _AUDIO_CACHE_TTL_SECONDS
    ]
    for media_id in stale_ids:
        _AUDIO_CACHE.pop(media_id, None)


async def _store_tts_audio_and_get_url(
    request: Request,
    text: str,
    voice_role: str = "assistant",
) -> Optional[str]:
    audio_bytes = await text_to_speech(text=text, voice_role=voice_role)
    if not audio_bytes:
        return None

    _cleanup_audio_cache()
    media_id = uuid4().hex
    _AUDIO_CACHE[media_id] = (audio_bytes, datetime.utcnow())
    return str(request.app.url_path_for("twilio_media", audio_id=media_id))


async def _build_voice_verb(
    request: Request,
    text: str,
    voice_role: str = "assistant",
) -> str:
    media_url = await _store_tts_audio_and_get_url(request, text, voice_role=voice_role)
    if media_url:
        return f"<Play>{escape(media_url)}</Play>"
    return f"<Say>{escape(text)}</Say>"


async def _append_transcript_message(
    call_id: str,
    sender: SenderType,
    text: str,
    confidence: Optional[float] = None,
) -> None:
    call_record = await get_call_record(TWILIO_USER_ID, call_id)
    if not call_record:
        return

    transcript = call_record.get("transcript", [])
    transcript.append(
        {
            "sender": sender.value,
            "text": text,
            "timestamp": datetime.utcnow().isoformat(),
            "confidence": confidence,
        }
    )
    await update_call_record(TWILIO_USER_ID, call_id, {"transcript": transcript})


def _caller_requested_end(speech_text: str) -> bool:
    text = speech_text.strip().lower()
    return any(phrase in text for phrase in END_CALL_PHRASES)


async def _create_twilio_session(
    call_sid: str,
    caller_number: str,
    caller_name: str,
) -> TwilioCallSession:
    call_data = {
        "caller_number": caller_number,
        "caller_name": caller_name,
        "status": CallStatus.SCREENING.value,
        "start_time": datetime.utcnow().isoformat(),
        "transcript": [],
        "spam_score": 0.0,
        "risk_level": RiskLevel.LOW.value,
        "scam_keywords_found": [],
        "is_blocked": False,
        "twilio_call_sid": call_sid,
    }
    call_id = await create_call_record(TWILIO_USER_ID, call_data)

    session = TwilioCallSession(
        call_sid=call_sid,
        call_id=call_id,
        caller_number=caller_number,
        caller_name=caller_name,
    )
    _ACTIVE_CALLS[call_sid] = session
    return session


async def _get_or_create_session(
    call_sid: str,
    caller_number: str,
    caller_name: str,
) -> tuple[TwilioCallSession, bool]:
    existing = _ACTIVE_CALLS.get(call_sid)
    if existing:
        return existing, False
    created = await _create_twilio_session(call_sid, caller_number, caller_name)
    return created, True


async def _build_gather_twiml(request: Request, prompt_text: str, voice_role: str = "assistant") -> Response:
    process_url = str(request.app.url_path_for("twilio_process_call"))
    prompt_verb = await _build_voice_verb(request, prompt_text, voice_role=voice_role)
    no_input_verb = await _build_voice_verb(
        request,
        "I could not hear you. Please say that again.",
        voice_role="assistant",
    )

    inner_xml = (
        f"<Gather input=\"speech\" action=\"{escape(process_url)}\" method=\"POST\" "
        f"speechTimeout=\"auto\" language=\"{escape(SPEECH_LANGUAGE)}\" timeout=\"5\">"
        f"{prompt_verb}"
        "</Gather>"
        f"{no_input_verb}"
        f"<Redirect method=\"POST\">{escape(process_url)}</Redirect>"
    )
    return _xml_response(inner_xml)


async def _finalize_session(
    call_sid: str,
    status: CallStatus = CallStatus.ENDED,
    duration_seconds: Optional[int] = None,
    ai_summary: Optional[str] = None,
    is_blocked: bool = False,
) -> None:
    session = _ACTIVE_CALLS.pop(call_sid, None)
    if not session:
        return

    if duration_seconds is None:
        duration_seconds = int((datetime.utcnow() - session.started_at).total_seconds())

    updates = {
        "status": status.value,
        "end_time": datetime.utcnow().isoformat(),
        "duration_seconds": max(0, duration_seconds),
        "is_blocked": is_blocked,
    }
    if ai_summary:
        updates["ai_summary"] = ai_summary

    await update_call_record(TWILIO_USER_ID, session.call_id, updates)


@router.api_route("/incoming", methods=["GET", "POST"])
async def twilio_incoming_call(request: Request) -> Response:
    """Initial Twilio webhook for inbound calls."""
    payload = await _extract_twilio_payload(request)
    call_sid = payload.get("CallSid", "").strip()
    caller_number = payload.get("From", "Unknown")
    caller_name = payload.get("CallerName", "")

    # Allow quick manual browser test when no Twilio payload is present.
    if not call_sid:
        return _xml_response("<Say>Twilio webhook is active.</Say>")

    session, created = await _get_or_create_session(call_sid, caller_number, caller_name)
    greeting = (
        f"{ASSISTANT_GREETING_PREFIX} This call is being screened for safety. "
        "Please tell me your name and reason for calling."
    )

    if created:
        greeting_msg = ChatMessage(sender=SenderType.AI, text=greeting)
        session.conversation_history.append(greeting_msg)
        await _append_transcript_message(session.call_id, SenderType.AI, greeting)

    return await _build_gather_twiml(request, greeting)


@router.api_route("/process", methods=["GET", "POST"])
async def twilio_process_call(request: Request) -> Response:
    """Process caller speech and continue the AI screening loop."""
    payload = await _extract_twilio_payload(request)
    call_sid = payload.get("CallSid", "").strip()
    caller_number = payload.get("From", "Unknown")
    caller_name = payload.get("CallerName", "")

    if not call_sid:
        return await _build_gather_twiml(
            request,
            "Please share your name and purpose of the call.",
        )

    session, _ = await _get_or_create_session(call_sid, caller_number, caller_name)
    speech_text = payload.get("SpeechResult", "").strip()

    confidence_value: Optional[float] = None
    try:
        raw_confidence = payload.get("Confidence", "").strip()
        if raw_confidence:
            confidence_value = float(raw_confidence)
    except ValueError:
        confidence_value = None

    if not speech_text:
        return await _build_gather_twiml(
            request,
            "I could not hear you clearly. Please repeat your message.",
        )

    if _caller_requested_end(speech_text):
        closing_text = "Thank you for the call. Ending now."
        await _append_transcript_message(session.call_id, SenderType.CALLER, speech_text, confidence_value)
        await _append_transcript_message(session.call_id, SenderType.AI, closing_text)
        await _finalize_session(
            call_sid,
            status=CallStatus.ENDED,
            ai_summary="Caller ended the call voluntarily.",
        )
        closing_verb = await _build_voice_verb(request, closing_text)
        return _xml_response(f"{closing_verb}<Hangup/>")

    caller_msg = ChatMessage(sender=SenderType.CALLER, text=speech_text)
    session.conversation_history.append(caller_msg)
    await _append_transcript_message(session.call_id, SenderType.CALLER, speech_text, confidence_value)

    ai_result = await generate_ai_reply(
        caller_message=speech_text,
        conversation_history=session.conversation_history,
        call_id=session.call_id,
    )

    ai_text = ai_result.reply_text
    ai_msg = ChatMessage(sender=SenderType.AI, text=ai_text)
    session.conversation_history.append(ai_msg)
    await _append_transcript_message(session.call_id, SenderType.AI, ai_text)

    await update_call_record(
        TWILIO_USER_ID,
        session.call_id,
        {
            "spam_score": ai_result.spam_score,
            "risk_level": ai_result.risk_level.value,
            "scam_keywords_found": ai_result.scam_keywords_found,
            "sentiment": ai_result.sentiment,
        },
    )

    # End high-risk calls to protect user safety.
    if ai_result.should_alert_user and ai_result.risk_level in {RiskLevel.HIGH, RiskLevel.CRITICAL}:
        safety_text = (
            "For safety reasons, this call cannot continue. "
            "Please contact through official channels only."
        )
        await _append_transcript_message(session.call_id, SenderType.AI, safety_text)
        await _finalize_session(
            call_sid,
            status=CallStatus.BLOCKED,
            ai_summary=ai_result.alert_message or "High-risk pattern detected; call terminated.",
            is_blocked=True,
        )
        safety_verb = await _build_voice_verb(request, safety_text, voice_role="alert")
        return _xml_response(f"{safety_verb}<Hangup/>")

    return await _build_gather_twiml(request, ai_text)


@router.api_route("/status", methods=["GET", "POST"])
async def twilio_status_callback(request: Request) -> Response:
    """Handle Twilio call status callbacks to close local sessions."""
    payload = await _extract_twilio_payload(request)
    call_sid = payload.get("CallSid", "").strip()
    call_status = payload.get("CallStatus", "").strip().lower()

    if call_sid and call_status in TERMINAL_CALL_STATES:
        duration_seconds = None
        raw_duration = payload.get("CallDuration", "").strip()
        if raw_duration.isdigit():
            duration_seconds = int(raw_duration)
        await _finalize_session(call_sid, status=CallStatus.ENDED, duration_seconds=duration_seconds)

    return Response(status_code=204)


@router.get("/media/{audio_id}.mp3")
async def twilio_media(audio_id: str) -> Response:
    """Serve short-lived TTS audio files for Twilio <Play>."""
    _cleanup_audio_cache()
    cached = _AUDIO_CACHE.get(audio_id)
    if not cached:
        return Response(status_code=404)

    audio_bytes, _ = cached
    return Response(content=audio_bytes, media_type="audio/mpeg")
