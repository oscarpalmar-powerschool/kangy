/*
  Purpose
  - Minimal ESP32 test for the HLK-LD2410C 24 GHz presence radar.
  - Triggers the XIAO (and prints to Serial) only when a target is detected
    within the TRIGGER_MIN_CM – TRIGGER_MAX_CM range.
  - Outside that range: silent.
  - Built-in LED (GPIO2) lights whenever a target is in the trigger zone.

  Library required
  - "ld2410" by ncmreynolds — install via Arduino Library Manager.

  Hardware (ESP32 DevKit + HLK-LD2410C)
  - Power
    - LD2410C VCC  -> ESP32 5V (VIN / "5V" pin — must be powered from USB or
                      an external 5 V rail; the sensor draws up to ~200 mA)
    - LD2410C GND  -> ESP32 GND

  - UART (sensor TX/RX are labeled from the sensor's perspective)
    - LD2410C TX   -> ESP32 GPIO16  (Serial2 RX)
    - LD2410C RX   -> ESP32 GPIO17  (Serial2 TX)
    Notes:
    - The LD2410C uses 3.3 V logic levels, compatible with ESP32 directly.
    - Default baud rate is 256000.

  - OUT pin (GPIO presence flag)
    - LD2410C OUT  -> ESP32 GPIO4  (digital input, internal pull-down active)

  - LED
    - ESP32 GPIO2  -> built-in LED on most DevKit boards

  - XIAO trigger output
    - ESP32 GPIO5  ->  XIAO D0 (GPIO1)
    - ESP32 GND    ->  XIAO GND (shared ground, no 5V wire during USB testing)
    Pin is pulsed HIGH for 200 ms on the rising edge of zone entry.
    Will not re-trigger until the target leaves the zone and returns.
*/

#include <ld2410.h>

#define RADAR_RX_PIN     16   // Serial2 RX <- sensor TX
#define RADAR_TX_PIN     17   // Serial2 TX -> sensor RX
#define RADAR_OUT_PIN     4   // OUT GPIO from sensor
#define LED_PIN           2   // built-in LED
#define XIAO_TRIGGER_PIN  5   // output to XIAO D0

#define RADAR_BAUD        256000
#define TRIGGER_PULSE_MS  200   // how long to hold the trigger pin HIGH
#define TRIGGER_MIN_CM     80   // trigger zone start
#define TRIGGER_MAX_CM    100   // trigger zone end

ld2410 radar;

bool          gLastInZone   = false;  // was target in zone last iteration?
unsigned long gTriggerEndMs = 0;      // when to drop the trigger pin LOW

// Returns the closest detected target distance, or -1 if no target.
static int closestTargetCm() {
  if (!radar.presenceDetected()) return -1;
  int dist = INT_MAX;
  if (radar.movingTargetDetected())
    dist = min(dist, (int)radar.movingTargetDistance());
  if (radar.stationaryTargetDetected())
    dist = min(dist, (int)radar.stationaryTargetDistance());
  return (dist == INT_MAX) ? -1 : dist;
}

void setup() {
  Serial.begin(115200);
  delay(200);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  pinMode(XIAO_TRIGGER_PIN, OUTPUT);
  digitalWrite(XIAO_TRIGGER_PIN, LOW);

  pinMode(RADAR_OUT_PIN, INPUT_PULLDOWN);

  Serial2.begin(RADAR_BAUD, SERIAL_8N1, RADAR_RX_PIN, RADAR_TX_PIN);

  Serial.println("LD2410C radar setup test");
  Serial.printf("Trigger zone: %d – %d cm\n", TRIGGER_MIN_CM, TRIGGER_MAX_CM);
  Serial.println("Initialising radar...");

  if (radar.begin(Serial2)) {
    Serial.println("Radar OK");
  } else {
    Serial.println("Radar not responding — check TX/RX wiring and power");
  }
}

void loop() {
  radar.read();

  int  dist   = closestTargetCm();
  bool inZone = (dist >= TRIGGER_MIN_CM && dist <= TRIGGER_MAX_CM);

  digitalWrite(LED_PIN, inZone ? HIGH : LOW);

  // Rising edge into zone → print and pulse trigger
  if (inZone && !gLastInZone) {
    Serial.printf("IN ZONE  dist=%d cm  >> trigger sent to XIAO\n", dist);
    digitalWrite(XIAO_TRIGGER_PIN, HIGH);
    gTriggerEndMs = millis() + TRIGGER_PULSE_MS;
  }

  // While in zone, keep reporting distance
  if (inZone) {
    Serial.printf("  dist=%d cm\n", dist);
  }

  gLastInZone = inZone;

  // Drop trigger pin after pulse duration
  if (gTriggerEndMs != 0 && millis() >= gTriggerEndMs) {
    digitalWrite(XIAO_TRIGGER_PIN, LOW);
    gTriggerEndMs = 0;
  }

  delay(500);
}
