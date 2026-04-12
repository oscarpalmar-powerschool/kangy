import asyncio
import json
import os
from typing import Any, Optional

from google import genai
from google.genai import types
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

client = genai.Client(api_key=os.environ["GOOGLE_API_KEY"])

app = FastAPI(title="Kangy Agents API")

# ---------------------------------------------------------------------------
# Request model
# ---------------------------------------------------------------------------

class DeviceStatus(BaseModel):
    device_id: str
    proximity_distance_cm: float
    human_description: Optional[str] = None
    iris_open: bool       # current iris state — so Gemini only acts on changes
    led_on: bool          # current LED state


class InstructionRequest(BaseModel):
    devices: list[DeviceStatus]


# ---------------------------------------------------------------------------
# Gemini response model (high-level; translated to action format below)
# ---------------------------------------------------------------------------

class GeminiDeviceInstruction(BaseModel):
    device_id: str
    message: Optional[str] = None          # short text to speak/display
    set_iris_open: Optional[bool] = None   # True=open, False=close, None=no change
    set_led_mode: Optional[str] = None     # "on" | "off" | "blink" | None=no change
    servo_degrees: Optional[float] = None  # 0–180 for the main servo, None=no move


class GeminiResponse(BaseModel):
    instructions: list[GeminiDeviceInstruction]


# ---------------------------------------------------------------------------
# Response model — matches Java backend's enqueue action format
# ---------------------------------------------------------------------------

class Action(BaseModel):
    type: str
    payload: dict[str, Any]


class DeviceActions(BaseModel):
    device_id: str
    actions: list[Action]


class InstructionResponse(BaseModel):
    device_actions: list[DeviceActions]


# ---------------------------------------------------------------------------
# Gemini system prompt
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = """You are an intelligent controller for ESP32 robotic devices (project: Kangy).
Each device has: a proximity sensor (distance in cm), an iris servo, an LED, and a speaker.

Decision rules:
- Human very close (< 30 cm)  : close iris (set_iris_open=false), LED off — protection mode
- Human nearby    (30–100 cm) : open iris (set_iris_open=true), LED on, greet the person
- No human / far  (> 100 cm)  : open iris (set_iris_open=true), LED off — idle / save power

Additional guidance:
- Only set a field when the current state needs to change (check iris_open and led_on)
- Messages should be short and friendly — they will be spoken aloud via text-to-speech
- set_led_mode values: "on", "off", or "blink"
- servo_degrees range: 0–180 (use only when a physical rotation is needed beyond iris)
- If nothing needs to change for a device, return an entry with all optional fields as null"""


# ---------------------------------------------------------------------------
# Gemini response schema — defined manually to avoid SDK serialisation issues
# ---------------------------------------------------------------------------

_GEMINI_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "instructions": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "device_id":     {"type": "STRING"},
                    "message":       {"type": "STRING"},
                    "set_iris_open": {"type": "BOOLEAN"},
                    "set_led_mode":  {"type": "STRING"},
                    "servo_degrees": {"type": "NUMBER"},
                },
                "required": ["device_id"],
            },
        },
    },
    "required": ["instructions"],
}


# ---------------------------------------------------------------------------
# Gemini call — sync, executed in a thread pool to keep the event loop free
# ---------------------------------------------------------------------------

def _call_gemini(device_status_json: str) -> GeminiResponse:
    response = client.models.generate_content(
        model="gemini-flash-latest",
        contents=(
            f"Current device statuses:\n\n{device_status_json}\n\n"
            "For each device decide: message, iris state, LED mode, servo degrees. "
            "Only include fields that need to change from the current state."
        ),
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=_GEMINI_SCHEMA,
        ),
    )
    return GeminiResponse.model_validate_json(response.text)


# ---------------------------------------------------------------------------
# Translate high-level Gemini instruction → Java backend action format
# ---------------------------------------------------------------------------

def _to_actions(instruction: GeminiDeviceInstruction) -> list[Action]:
    actions: list[Action] = []

    if instruction.set_iris_open is not None:
        degrees = 180.0 if instruction.set_iris_open else 0.0
        actions.append(Action(
            type="servo.setPosition",
            payload={"id": "iris", "degrees": degrees},
        ))

    if instruction.set_led_mode is not None:
        actions.append(Action(
            type="led.command",
            payload={"id": "main", "mode": instruction.set_led_mode},
        ))

    if instruction.servo_degrees is not None:
        actions.append(Action(
            type="servo.setPosition",
            payload={"id": "main", "degrees": instruction.servo_degrees},
        ))

    if instruction.message:
        actions.append(Action(
            type="speak.text",
            payload={"text": instruction.message},
        ))

    return actions


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/api/device-instructions", response_model=InstructionResponse)
async def get_device_instructions(request: InstructionRequest) -> InstructionResponse:
    if not request.devices:
        raise HTTPException(status_code=400, detail="No devices provided")

    device_status_json = json.dumps(
        [d.model_dump() for d in request.devices], indent=2
    )

    try:
        gemini_result = await asyncio.to_thread(_call_gemini, device_status_json)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Gemini call failed: {e}")

    return InstructionResponse(
        device_actions=[
            DeviceActions(
                device_id=inst.device_id,
                actions=_to_actions(inst),
            )
            for inst in gemini_result.instructions
        ]
    )
