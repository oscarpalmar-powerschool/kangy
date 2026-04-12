/*
  Purpose
  - Minimal ESP32 test for the HLK-LD2410C 24 GHz presence radar.
  - Reads the OUT pin (fast GPIO presence flag) and the UART stream
    (moving/stationary target, distance, energy) and prints results
    to the Serial monitor every 500 ms.
  - Built-in LED (GPIO2) mirrors the OUT pin: ON = presence detected.

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
    - Signal is HIGH when a target is detected, LOW otherwise.
    Notes:
    - The OUT pin threshold (sensitivity, hold time) can be tuned via the
      HLKRadarTool app over Bluetooth or via UART commands. The defaults
      work fine for a first test.

  - LED (optional external, or use the on-board LED on GPIO2)
    - ESP32 GPIO2  -> (built-in LED on most DevKit boards, no extra wiring)
*/

#include <ld2410.h>

#define RADAR_RX_PIN   16   // Serial2 RX <- sensor TX
#define RADAR_TX_PIN   17   // Serial2 TX -> sensor RX
#define RADAR_OUT_PIN   4   // OUT GPIO from sensor
#define LED_PIN         2   // built-in LED

#define RADAR_BAUD   256000
#define PRINT_EVERY_MS  500

ld2410 radar;

unsigned long gLastPrintMs = 0;

void setup() {
  Serial.begin(115200);
  delay(200);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  pinMode(RADAR_OUT_PIN, INPUT_PULLDOWN);

  // Serial2 is used for the sensor so the USB serial monitor stays free.
  Serial2.begin(RADAR_BAUD, SERIAL_8N1, RADAR_RX_PIN, RADAR_TX_PIN);

  Serial.println("LD2410C radar setup test");
  Serial.println("Initialising radar...");

  if (radar.begin(Serial2)) {
    Serial.println("Radar OK");
  } else {
    Serial.println("Radar not responding — check TX/RX wiring and power");
  }
}

void loop() {
  radar.read();  // must be called frequently to service the UART frame parser

  bool outPin = digitalRead(RADAR_OUT_PIN) == HIGH;
  digitalWrite(LED_PIN, outPin ? HIGH : LOW);

  unsigned long now = millis();
  if (now - gLastPrintMs < PRINT_EVERY_MS) {
    return;
  }
  gLastPrintMs = now;

  Serial.print("OUT pin: ");
  Serial.print(outPin ? "PRESENT" : "clear  ");

  Serial.print("  |  UART: ");
  if (radar.presenceDetected()) {
    if (radar.stationaryTargetDetected()) {
      Serial.printf("stationary  dist=%4d cm  energy=%d",
                    radar.stationaryTargetDistance(),
                    radar.stationaryTargetEnergy());
    }
    if (radar.movingTargetDetected()) {
      Serial.printf("  moving  dist=%4d cm  energy=%d",
                    radar.movingTargetDistance(),
                    radar.movingTargetEnergy());
    }
  } else {
    Serial.print("no target");
  }

  Serial.println();
}
