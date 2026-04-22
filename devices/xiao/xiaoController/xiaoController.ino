/*
 * XIAO ESP32S3 Sense — camera controller.
 *
 * Listens for a trigger pulse from the companion ESP32 (GPIO trigger wire),
 * captures a JPEG, and POSTs it to the backend under the companion's device ID.
 * The backend stores the image and can correlate it with the ESP32's status.
 *
 * Cooldown: will not re-trigger within CAPTURE_COOLDOWN_MS of the last upload,
 * regardless of how long the trigger pin stays HIGH.
 *
 * Hardware wiring (same as xiaoSetup)
 * - Companion ESP32 5V    ->  XIAO 5V   (or power XIAO from its own USB)
 * - Companion ESP32 GND   ->  XIAO GND
 * - Companion ESP32 GPIO5 ->  XIAO D0 (GPIO1)  [trigger signal]
 *
 * Board settings in Arduino IDE
 * - Board:  XIAO_ESP32S3
 * - PSRAM:  OPI PSRAM
 *
 * Config
 * - Edit config.h to point at the right environment file.
 * - Set COMPANION_DEVICE_ID to the companion ESP32's device ID
 *   (printed to Serial on first boot of the ESP32, e.g. esp32-A4CF1234ABCD).
 *
 * LED blink codes (built-in LED, GPIO21)
 * - WiFi connecting  : rapid blink (100 ms) until connected
 * - Camera failed    : 10 fast blinks then halt — check ribbon cable / PSRAM setting
 * - Ready / idle     : off
 * - Trigger received : solid ON while capturing + uploading
 * - Upload OK        : 3 short blinks
 * - Upload failed    : 5 rapid blinks
 * - Cooldown active  : 2 slow blinks (capture was skipped)
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include "esp_camera.h"
#include "config.h"

// ── Camera pin map for XIAO ESP32S3 Sense ──────────────────────────────────
#define PWDN_GPIO_NUM   -1
#define RESET_GPIO_NUM  -1
#define XCLK_GPIO_NUM   10
#define SIOD_GPIO_NUM   40
#define SIOC_GPIO_NUM   39
#define Y9_GPIO_NUM     48
#define Y8_GPIO_NUM     11
#define Y7_GPIO_NUM     12
#define Y6_GPIO_NUM     14
#define Y5_GPIO_NUM     16
#define Y4_GPIO_NUM     18
#define Y3_GPIO_NUM     17
#define Y2_GPIO_NUM     15
#define VSYNC_GPIO_NUM  38
#define HREF_GPIO_NUM   47
#define PCLK_GPIO_NUM   13

#define TRIGGER_PIN          1      // D0 — driven HIGH by companion ESP32
#define LED_PIN             21      // built-in LED, active-LOW
#define CAPTURE_COOLDOWN_MS 30000   // minimum ms between uploads

static const char* kSsid              = WIFI_SSID;
static const char* kPassword          = WIFI_PASSWORD;
static const char* kApiBase           = API_BASE_URL;
static const char* kRegistrationToken = DEVICE_REGISTRATION_TOKEN;
static const char* kCompanionId       = COMPANION_DEVICE_ID;

bool          gLastTrigger   = false;
unsigned long gLastCaptureMs = 0;

// ── LED helpers ────────────────────────────────────────────────────────────

static void ledOn()  { digitalWrite(LED_PIN, LOW);  }
static void ledOff() { digitalWrite(LED_PIN, HIGH); }

static void ledBlink(int times, int onMs, int offMs) {
  for (int i = 0; i < times; i++) {
    ledOn();  delay(onMs);
    ledOff(); delay(offMs);
  }
}

// ── Camera ─────────────────────────────────────────────────────────────────

static bool initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0       = Y2_GPIO_NUM;
  config.pin_d1       = Y3_GPIO_NUM;
  config.pin_d2       = Y4_GPIO_NUM;
  config.pin_d3       = Y5_GPIO_NUM;
  config.pin_d4       = Y6_GPIO_NUM;
  config.pin_d5       = Y7_GPIO_NUM;
  config.pin_d6       = Y8_GPIO_NUM;
  config.pin_d7       = Y9_GPIO_NUM;
  config.pin_xclk     = XCLK_GPIO_NUM;
  config.pin_pclk     = PCLK_GPIO_NUM;
  config.pin_vsync    = VSYNC_GPIO_NUM;
  config.pin_href     = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn     = PWDN_GPIO_NUM;
  config.pin_reset    = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode    = CAMERA_GRAB_WHEN_EMPTY;

  if (psramFound()) {
    config.frame_size   = FRAMESIZE_SXGA;  // 1280x1024 — enough detail for recognition
    config.jpeg_quality = 8;               // 0-63, lower = higher quality
    config.fb_count     = 2;
    config.fb_location  = CAMERA_FB_IN_PSRAM;
  } else {
    config.frame_size   = FRAMESIZE_VGA;   // best DRAM can handle
    config.jpeg_quality = 12;
    config.fb_count     = 1;
    config.fb_location  = CAMERA_FB_IN_DRAM;
  }

  return esp_camera_init(&config) == ESP_OK;
}

// ── Capture + upload ───────────────────────────────────────────────────────

static void captureAndUpload() {
  ledOn();  // solid ON during the whole capture+upload

  camera_fb_t* fb = esp_camera_fb_get();
  if (!fb) {
    ledBlink(5, 80, 80);  // upload-failed pattern (camera error counts the same)
    return;
  }

  String url = String(kApiBase) + "/" + kCompanionId + "/image";
  HTTPClient http;
  http.begin(url);
  http.addHeader("Content-Type", "image/jpeg");
  http.addHeader("X-Registration-Token", kRegistrationToken);

  int code = http.POST(fb->buf, fb->len);
  http.end();
  esp_camera_fb_return(fb);

  gLastCaptureMs = millis();
  ledOff();

  if (code == HTTP_CODE_OK) {
    ledBlink(3, 150, 100);  // 3 short blinks = OK
  } else {
    ledBlink(5, 80, 80);    // 5 rapid blinks = failed
  }
}

// ── Setup ──────────────────────────────────────────────────────────────────

void setup() {
  Serial.begin(115200);
  delay(200);

  pinMode(LED_PIN, OUTPUT);
  ledOff();
  pinMode(TRIGGER_PIN, INPUT_PULLDOWN);

  Serial.println("XIAO ESP32S3 Sense — controller");
  Serial.printf("Companion device: %s\n", kCompanionId);
  Serial.printf("PSRAM: %s\n", psramFound() ? "found" : "NOT found");

  if (!initCamera()) {
    Serial.println("Camera FAILED — check ribbon cable and PSRAM board setting");
    ledBlink(10, 80, 80);
    while (true) { delay(1000); }
  }
  // Discard several frames so the OV2640 auto-exposure and white balance settle.
  for (int i = 0; i < 5; i++) {
    camera_fb_t* warmup = esp_camera_fb_get();
    if (warmup) esp_camera_fb_return(warmup);
    delay(100);
  }
  Serial.println("Camera OK");

  WiFi.mode(WIFI_STA);
  WiFi.begin(kSsid, kPassword);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    ledBlink(1, 100, 100);  // rapid blink while connecting
    Serial.print(".");
  }
  Serial.println();
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());
  Serial.printf("Ready — cooldown %d s between captures\n", CAPTURE_COOLDOWN_MS / 1000);
}

// ── Loop ───────────────────────────────────────────────────────────────────

void loop() {
  bool trigger = digitalRead(TRIGGER_PIN) == HIGH;

  if (trigger && !gLastTrigger) {
    unsigned long now     = millis();
    unsigned long elapsed = now - gLastCaptureMs;
    bool inCooldown = gLastCaptureMs != 0 && elapsed < CAPTURE_COOLDOWN_MS;

    if (inCooldown) {
      Serial.printf("Trigger — cooldown (%lu s remaining)\n",
                    (CAPTURE_COOLDOWN_MS - elapsed) / 1000);
      ledBlink(2, 300, 300);  // 2 slow blinks = cooldown skip
    } else if (WiFi.status() != WL_CONNECTED) {
      Serial.println("Trigger — WiFi not connected, skipping");
      ledBlink(5, 80, 80);    // same as upload-failed so you know something went wrong
    } else {
      Serial.println("Trigger — waiting for subject to settle");
      delay(800);  // give the subject time to reach the frame center
      Serial.println("Trigger — capturing");
      captureAndUpload();
    }
  }

  gLastTrigger = trigger;
  delay(10);
}
