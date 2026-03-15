# Monorepo – Devices, Backend, Frontend, Agents

Single repository for device code (ESP32, XIAO, Raspberry Pi, etc.), backend API, web frontend, and AI agents. CI/CD deploys only what changed (path-filtered) to AWS ECS.

## Structure

| Folder        | Stack                       | Purpose                                                         |
| ------------- | --------------------------- | --------------------------------------------------------------- |
| `backend/`    | Java, Spring Boot           | REST API for devices and frontend                               |
| `frontend/`   | TypeScript, React           | Web UI for device status and data                               |
| `agents/`     | Python, FastAPI + LangChain | AI/LLM processing, called by backend over HTTP                  |
| `devices/`    | Mixed                       | Code that runs on micro-devices (not deployed to AWS)           |
| ├── `esp32/`  | Arduino (.ino)              | ESP32 firmware – open in Arduino IDE or use PlatformIO          |
| ├── `xiao/`   | Arduino (.ino)              | Seeeduino XIAO firmware                                         |
| ├── `rpi/`    | Python / other              | Raspberry Pi app (scripts, systemd service)                     |
| └── `shared/` | Docs, schemas               | Optional: protocol and message specs shared across device types |

See [devices/README.md](devices/README.md) for per-device tooling and layout.

## Run locally

- **Backend:** `cd backend && ./mvnw spring-boot:run` (or your Gradle equivalent)
- **Frontend:** `cd frontend && npm install && npm run dev`
- **Agents:** `cd agents && pip install -r requirements.txt && uvicorn main:app --reload`
- **Devices:** Open the right subfolder (e.g. `devices/esp32/<sketch>/`) in Arduino IDE, or run your RPi app from `devices/rpi/`.

## CI/CD and deployment

GitHub Actions build and deploy to AWS ECS when `backend/`, `frontend/`, or `agents/` change (on push to `main` or tag `v*`). Changes under `devices/` do not trigger cloud deploys.

See [.github/workflows/README.md](.github/workflows/README.md) for secrets, variables, and dev vs prod setup.

## License
