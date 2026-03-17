#include <ESP32Servo.h>

/*
  Purpose
  - Minimal ESP32 servo test: move between 0° and ~165° once per second.
  - Turn an LED ON when the commanded servo angle is > 0°, OFF when it is 0°.

  Hardware (ESP32 DevKit + hobby servo + LED)
  - Servo
    - Signal (usually yellow/orange) -> ESP32 GPIO13 (signal only)
    - V+ (usually red)              -> +5V
    - GND (usually brown/black)     -> GND (must be common with ESP32 GND)
    Notes:
    - A micro servo like the SG90 is often OK for simple tests powered from the ESP32
      board's 5V/VIN pin (i.e., from USB), but if you see resets/jitter, use a separate
      5V supply.
    - Do not power servos from the ESP32 3.3V pin.
    - Always connect grounds together (external supply GND <-> ESP32 GND).

  - LED (any small indicator LED)
    - ESP32 GPIO2 (LED pin) -> series resistor (220Ω–1kΩ) -> LED anode (+, long leg)
    - LED cathode (-, short leg / flat side) -> GND
    Notes:
    - Many ESP32 dev boards already have a built-in LED on GPIO2; you can use that,
      or wire your own LED as above.
*/

#ifndef LED_BUILTIN
#define LED_BUILTIN 2
#endif

Servo myservo;
const int servoPin = 13;  // signal wire connected here
const int ledPin = LED_BUILTIN;

static void setAngleAndLed(int angleDeg) {
  digitalWrite(ledPin, (angleDeg > 0) ? HIGH : LOW);
  myservo.write(angleDeg);
}
void setup() {
  pinMode(ledPin, OUTPUT);
  digitalWrite(ledPin, LOW);
  myservo.attach(servoPin);
}
void loop() {
  setAngleAndLed(0);    // 0°
  delay(1000);
  setAngleAndLed(165);   // 165°
  delay(3000);
}