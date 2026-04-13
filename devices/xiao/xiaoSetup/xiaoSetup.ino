/*
  Purpose
  - Minimal XIAO ESP32S3 Sense camera test.
  - Waits for a trigger signal from the plain ESP32 (GPIO trigger wire)
    OR a manual 'c' keypress in the Serial monitor.
  - On trigger: captures a JPEG frame, prints its size to Serial, and
    saves it to the built-in flash so you can verify the image later.
  - Built-in LED (GPIO21) lights while a capture is in progress.

  This sketch intentionally has no WiFi or backend upload — the goal is
  to confirm the camera initialises correctly and produces valid frames
  before wiring the full pipeline into the device controller.

  Board setup in Arduino IDE
  - Board:      "XIAO_ESP32S3"  (package: esp32 by Espressif >= 2.0.14)
  - PSRAM:      "OPI PSRAM"     (required — the camera frame buffer lives here)
  - USB mode:   "USB-OTG (TinyUSB)" or "Hardware CDC and JTAG" (either works)
  - Install library: "esp32-camera" (included with the Espressif board package,
    no separate install needed)

  Hardware wiring
  - Camera (built-in on the XIAO ESP32S3 Sense)
    - The OV2640 camera is connected internally via the flex ribbon cable
      that ships with the board. Attach it to the J1 connector before
      powering on. No extra wiring needed.

  - Power from plain ESP32 (no USB-C cable needed on the XIAO)
    - Plain ESP32 5V (VIN)  ->  XIAO 5V pin
      Feeds the XIAO's onboard regulator, same as USB-C would.
      Ensure your 5V supply can handle both boards (~100 mA idle for the
      XIAO, more during WiFi TX bursts).
    - Plain ESP32 GND       ->  XIAO GND

  - Trigger input from plain ESP32
    - Plain ESP32 GPIO5  ->  XIAO D0 (GPIO1)
      Signal is HIGH when the ESP32 wants a capture, LOW otherwise.
      An internal pull-down is enabled so the pin reads LOW safely when
      the plain ESP32 is not yet driving the pin.

  - LED
    - GPIO21 is the built-in "charging" LED on the XIAO ESP32S3 Sense.
      It is active-LOW (write LOW to turn it ON).

  Serial monitor
  - Open at 115200 baud.
  - Press 'c' (then Enter / Send) to manually trigger a capture without
    needing the plain ESP32 connected.
*/

#include "esp_camera.h"

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

#define TRIGGER_PIN  1   // D0 on XIAO — driven by plain ESP32 GPIO5
#define LED_PIN     21   // built-in LED, active-LOW

// ── Helpers ────────────────────────────────────────────────────────────────

static void ledOn()  { digitalWrite(LED_PIN, LOW);  }
static void ledOff() { digitalWrite(LED_PIN, HIGH); }

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

  // Use larger frame + higher quality when PSRAM is available.
  if (psramFound()) {
    config.frame_size   = FRAMESIZE_VGA;   // 640x480 — good balance for upload
    config.jpeg_quality = 12;              // 0 (best) – 63 (worst)
    config.fb_count     = 2;
    config.fb_location  = CAMERA_FB_IN_PSRAM;
  } else {
    config.frame_size   = FRAMESIZE_QVGA;  // 320x240 fallback (no PSRAM)
    config.jpeg_quality = 20;
    config.fb_count     = 1;
    config.fb_location  = CAMERA_FB_IN_DRAM;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed: 0x%x\n", err);
    return false;
  }
  return true;
}

static void captureAndReport() {
  ledOn();
  Serial.println("Capturing...");

  camera_fb_t* fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("ERROR: frame buffer capture failed");
    ledOff();
    return;
  }

  Serial.printf("Capture OK — %u bytes  (%dx%d JPEG)\n",
                fb->len, fb->width, fb->height);

  esp_camera_fb_return(fb);
  ledOff();
}

// ── Trigger debounce state ─────────────────────────────────────────────────
bool gLastTrigger = false;

void setup() {
  Serial.begin(115200);
  delay(200);

  pinMode(LED_PIN, OUTPUT);
  ledOff();

  pinMode(TRIGGER_PIN, INPUT_PULLDOWN);

  Serial.println("XIAO ESP32S3 Sense — camera setup test");
  Serial.printf("PSRAM: %s\n", psramFound() ? "found" : "NOT found (using DRAM)");

  if (initCamera()) {
    Serial.println("Camera OK");
    // Warm-up: discard the first frame (exposure settling)
    camera_fb_t* warmup = esp_camera_fb_get();
    if (warmup) esp_camera_fb_return(warmup);
    Serial.println("Ready — press 'c' in Serial monitor or assert trigger pin");
  } else {
    Serial.println("Camera FAILED — check ribbon cable and PSRAM board setting");
  }
}

void loop() {
  // ── Serial manual trigger ──
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'c' || c == 'C') {
      captureAndReport();
    }
  }

  // ── GPIO trigger (rising edge only, so one capture per pulse) ──
  bool trigger = digitalRead(TRIGGER_PIN) == HIGH;
  if (trigger && !gLastTrigger) {
    captureAndReport();
  }
  gLastTrigger = trigger;

  delay(10);
}
