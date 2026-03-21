/*
 * ESP32 device controller: registers with Kangy backend (servo + LED outputs),
 * then uses a single POST /status round-trip per interval to ack completed actions
 * and receive new ones (minimal traffic vs separate GET + ack).
 */

#include <WiFi.h>
#include <esp_mac.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include <vector>
#include "config_local.h"

static const char* kSsid = WIFI_SSID;
static const char* kPassword = WIFI_PASSWORD;
static const char* kApiBase = API_BASE_URL;

const int kLedPin = 2;
const int kServoPin = 13;
/** Physical travel limit on this build. Backend/UI may send 0–180; values above this are clamped. */
const int kServoMaxDegrees = 165;

Servo gServo;
String gDeviceId;
String gToken;
std::vector<String> gAckIds;

const unsigned long kPollIntervalMs = 500;

unsigned long gLastPollMs = 0;
int gLastServoAngle = -1;

// --- LED (solid + non-blocking blink) ---
bool gLedBlink = false;
bool gLedOn = false;
unsigned long gLedNextToggleMs = 0;
unsigned long gLedHalfPeriodMs = 250;
unsigned long gLedStopAtMs = 0;
int gLedToggleBudget = -1;

void ledSetSolid(bool on) {
  gLedBlink = false;
  gLedOn = on;
  digitalWrite(kLedPin, on ? HIGH : LOW);
}

void ledStartBlink(unsigned long periodMs, long durationMs, int count) {
  gLedBlink = true;
  gLedHalfPeriodMs = (periodMs > 0) ? max(50UL, periodMs / 2) : 250;
  gLedNextToggleMs = millis();
  gLedStopAtMs = (durationMs > 0) ? (millis() + (unsigned long)durationMs) : 0;
  gLedToggleBudget = (count > 0) ? (count * 2) : -1;
  gLedOn = true;
  digitalWrite(kLedPin, HIGH);
}

void ledService() {
  if (!gLedBlink) {
    return;
  }
  unsigned long now = millis();
  if (gLedStopAtMs != 0 && now >= gLedStopAtMs) {
    gLedBlink = false;
    digitalWrite(kLedPin, LOW);
    return;
  }
  if (now < gLedNextToggleMs) {
    return;
  }
  gLedNextToggleMs = now + gLedHalfPeriodMs;
  gLedOn = !gLedOn;
  digitalWrite(kLedPin, gLedOn ? HIGH : LOW);
  if (gLedToggleBudget > 0) {
    gLedToggleBudget--;
    if (gLedToggleBudget == 0) {
      gLedBlink = false;
      digitalWrite(kLedPin, LOW);
    }
  }
}

// Use eFuse / Wi-Fi STA MAC from the chip. WiFi.macAddress() is often all zeros until
// after WiFi.begin(), so calling it from setup() before connect produced esp32-000000000000.
static void buildDeviceIdFromMac() {
  uint8_t mac[6] = {0};
  esp_err_t err = esp_read_mac(mac, ESP_MAC_WIFI_STA);
  bool allZero =
      (mac[0] | mac[1] | mac[2] | mac[3] | mac[4] | mac[5]) == 0;
  if (err != ESP_OK || allZero) {
    WiFi.macAddress(mac);
  }
  char buf[32];
  snprintf(buf, sizeof(buf), "esp32-%02X%02X%02X%02X%02X%02X",
           mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
  gDeviceId = buf;
}

static bool postJson(const String& url, const String& authBearer, const String& jsonBody, String& outBody, int& httpCode) {
  HTTPClient http;
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  if (authBearer.length() > 0) {
    http.addHeader("Authorization", "Bearer " + authBearer);
  }
  httpCode = http.POST(jsonBody);
  if (httpCode > 0) {
    outBody = http.getString();
  } else {
    outBody = "";
  }
  http.end();
  return httpCode > 0;
}

bool registerWithBackend() {
  DynamicJsonDocument req(512);
  req["deviceId"] = gDeviceId;
  req["deviceType"] = "ESP32";
  JsonArray inCaps = req.createNestedArray("inputCapabilities");
  inCaps.add("heartbeat");
  JsonArray outCaps = req.createNestedArray("outputCapabilities");
  outCaps.add("servo");
  outCaps.add("led");

  String body;
  serializeJson(req, body);

  String resp;
  int code = 0;
  String url = String(kApiBase) + "/register";
  if (!postJson(url, "", body, resp, code)) {
    Serial.printf("register: HTTP error (no response)\n");
    return false;
  }
  if (code != HTTP_CODE_OK) {
    Serial.printf("register: HTTP %d: %s\n", code, resp.c_str());
    return false;
  }

  DynamicJsonDocument doc(1024);
  DeserializationError err = deserializeJson(doc, resp);
  if (err) {
    Serial.printf("register: JSON error: %s\n", err.c_str());
    return false;
  }
  const char* tok = doc["token"];
  if (!tok || !tok[0]) {
    Serial.println("register: missing token");
    return false;
  }
  gToken = tok;
  Serial.printf("Registered %s\n", gDeviceId.c_str());
  return true;
}

static void applyServo(JsonObjectConst payload) {
  const char* id = payload["id"];
  if (!id || strcmp(id, "servo") != 0) {
    return;
  }
  if (!payload.containsKey("degrees")) {
    return;
  }
  int deg = (int) payload["degrees"].as<float>();
  deg = constrain(deg, 0, kServoMaxDegrees);
  gServo.write(deg);
  if (deg != gLastServoAngle) {
    gLastServoAngle = deg;
    Serial.printf("Servo -> %d\n", deg);
  }
}

static void applyLed(JsonObjectConst payload) {
  const char* id = payload["id"];
  if (!id || strcmp(id, "led") != 0) {
    return;
  }
  const char* mode = payload["mode"];
  if (!mode) {
    return;
  }
  if (strcmp(mode, "on") == 0) {
    ledSetSolid(true);
    Serial.println("LED on");
  } else if (strcmp(mode, "off") == 0) {
    ledSetSolid(false);
    Serial.println("LED off");
  } else if (strcmp(mode, "blink") == 0) {
    unsigned long periodMs = payload["periodMs"].isNull() ? 500 : (unsigned long) payload["periodMs"].as<unsigned long>();
    long durationMs = payload["durationMs"].isNull() ? 0 : payload["durationMs"].as<long>();
    int count = payload["count"].isNull() ? 0 : payload["count"].as<int>();
    ledStartBlink(periodMs, durationMs, count);
    Serial.println("LED blink");
  }
}

static void applyAction(JsonObjectConst action) {
  const char* type = action["type"];
  JsonObjectConst payload = action["payload"];
  if (!type) {
    return;
  }
  if (strcmp(type, "servo.setPosition") == 0) {
    applyServo(payload);
  } else if (strcmp(type, "led.command") == 0) {
    applyLed(payload);
  } else {
    Serial.printf("Unknown action type: %s (will still ack)\n", type);
  }
}

static bool publishStatusRoundTrip() {
  DynamicJsonDocument req(1024);
  if (!gAckIds.empty()) {
    JsonArray arr = req.createNestedArray("ackedActionIds");
    for (const String& id : gAckIds) {
      arr.add(id);
    }
  }
  req["actionLimit"] = 25;

  String reqBody;
  serializeJson(req, reqBody);

  String url = String(kApiBase) + "/" + gDeviceId + "/status";
  String resp;
  int code = 0;
  if (!postJson(url, gToken, reqBody, resp, code)) {
    return false;
  }

  if (code == HTTP_CODE_UNAUTHORIZED) {
    Serial.println("status: unauthorized, re-registering");
    if (!registerWithBackend()) {
      return false;
    }
    if (!postJson(url, gToken, reqBody, resp, code)) {
      return false;
    }
  }

  if (code != HTTP_CODE_OK) {
    Serial.printf("status: HTTP %d: %s\n", code, resp.c_str());
    return false;
  }

  DynamicJsonDocument doc(6144);
  DeserializationError err = deserializeJson(doc, resp);
  if (err) {
    Serial.printf("status: JSON error: %s\n", err.c_str());
    return false;
  }

  gAckIds.clear();

  JsonArray actions = doc["actions"].as<JsonArray>();
  for (JsonObject act : actions) {
    applyAction(act);
    const char* aid = act["actionId"];
    if (aid && aid[0]) {
      gAckIds.push_back(aid);
    }
  }
  return true;
}

void setup() {
  Serial.begin(115200);
  delay(200);
  pinMode(kLedPin, OUTPUT);
  digitalWrite(kLedPin, LOW);

  gServo.attach(kServoPin);

  WiFi.mode(WIFI_STA);
  buildDeviceIdFromMac();

  WiFi.begin(kSsid, kPassword);
  Serial.print("Connecting ");
  Serial.println(gDeviceId);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());

  if (gDeviceId == "esp32-000000000000") {
    buildDeviceIdFromMac();
    Serial.print("Device id (after WiFi up): ");
    Serial.println(gDeviceId);
  }

  while (!registerWithBackend()) {
    delay(3000);
  }
}

void loop() {
  ledService();

  if (WiFi.status() != WL_CONNECTED) {
    delay(500);
    return;
  }

  unsigned long now = millis();
  if (now - gLastPollMs < kPollIntervalMs) {
    delay(20);
    return;
  }
  gLastPollMs = now;

  if (!publishStatusRoundTrip()) {
    delay(500);
  }
}
