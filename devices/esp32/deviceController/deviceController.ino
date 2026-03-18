/*
 * ESP32 remote servo: polls servo angle from JSON URL and sets the servo.
 * URL: http://192.168.132.73:8000/esp32/servo.json  ->  { "angle": 20 }
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include "config_local.h"

const char* ssid     = WIFI_SSID;
const char* password = WIFI_PASSWORD;
const char* url      = SERVER_URL;

// --- Servo ---
Servo myservo;
const int servoPin = 13;
int lastAngle = -1;

const unsigned long pollIntervalMs = 500;  // poll every 500 ms
unsigned long lastPoll = 0;

void setup() {
  Serial.println("Setup starting");
  Serial.begin(115200);
  pinMode(2, OUTPUT);  // built-in LED
  Serial.println("Servo attached, connecting");
  myservo.attach(servoPin);

  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.println("WiFi connected");
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    digitalWrite(2, LOW);
    delay(500);
    return;
  }
  digitalWrite(2, HIGH);

  unsigned long now = millis();
  if (now - lastPoll < pollIntervalMs) {
    delay(50);
    return;
  }
  lastPoll = now;

  HTTPClient http;
  http.begin(url);
  int code = http.GET();

  if (code != HTTP_CODE_OK) {
    Serial.printf("GET failed: %d\n", code);
    http.end();
    return;
  }

  String payload = http.getString();
  http.end();

  // Parse JSON: { "angle": 20 }
  StaticJsonDocument<128> doc;
  DeserializationError err = deserializeJson(doc, payload);
  if (err) {
    Serial.printf("JSON parse error: %s\n", err.c_str());
    return;
  }

  int angle = doc["angle"] | 90;  // default 90 if missing
  angle = constrain(angle, 0, 180);

  if (angle != lastAngle) {
    myservo.write(angle);
    lastAngle = angle;
    Serial.printf("Servo -> %d\n", angle);
  }
}
