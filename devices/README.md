# Device code (ESP32, XIAO, Raspberry Pi, etc.)

One folder per device type. None of this is deployed to AWS; it runs on the hardware or is flashed/installed manually (or via your own OTA/install flow).

## Structure

```
devices/
├── esp32/          # Arduino .ino or PlatformIO – flash via Arduino IDE or pio
├── xiao/           # Seeeduino XIAO – Arduino .ino or PlatformIO
├── rpi/            # Raspberry Pi – Python (or other) app, e.g. systemd service
└── shared/         # Optional: protocol docs, message schemas, constants (no code or per-language copies)
```

## Per-device notes

| Device    | Typical stack                       | How to run / flash                                                                                                          |
| --------- | ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| **esp32** | Arduino (.ino), ESP-IDF, PlatformIO | Arduino IDE: open `devices/esp32/<sketch>/` and upload. Or `pio run -d devices/esp32` if using PlatformIO.                  |
| **xiao**  | Arduino (.ino), PlatformIO          | Same as ESP32; select board “Seeed XIAO” (or SAMD21/SAMD51 depending on model).                                             |
| **rpi**   | Python, Node, etc.                  | Copy/clone to Pi, install deps, run as script or systemd service. Optionally build a package or use your own deploy script. |

## Optional: `shared/`

Use `devices/shared/` for things that apply to all device types but aren’t source code (e.g. API contract docs, JSON schema for device↔backend messages). Actual code sharing across Arduino and RPi is limited by language; keep protocol and docs in `shared/` and implement per device in `esp32/`, `xiao/`, `rpi/`.

## Lint/Test cases

# Run tests only

mvn -f backend/pom.xml test

# Run lint only

mvn -f backend/pom.xml checkstyle:check

# Run both (what CI will do)

mvn -f backend/pom.xml verify

curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
https://kangy-api-dev-223647245649.us-central1.run.app/actuator/health