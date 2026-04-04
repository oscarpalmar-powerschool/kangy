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

- **Backend:** `cd backend && mvn -DskipTests spring-boot:run` (or from repo root: 'docker build -t kangy-backend ./backend' 'docker run --rm -p 8080:8080 kangy-backend' )
  or after adding security:
  `set DEVICE_REGISTRATION_TOKEN=local-reg-token` 
  `set FRONTEND_API_KEY=local-api-key` 
  `mvn spring-boot:run -f backend/pom.xml`
  Useful URLs:

Health: GET /actuator/health
OpenAPI JSON: GET /api-docs
Swagger UI: GET /swagger-ui

- **Frontend:** `cd frontend && npm install && npm run dev`
  - By default the frontend calls the backend on the same origin via `/api/...` (Vite dev server proxies `/api` to `http://localhost:8080`).
  - To override the API base URL at build time: set `VITE_API_BASE` (see `frontend/.env.example`).
  - For Docker/nginx runtime config: set `KANGY_API_BASE` (the container writes `/config.js` on startup).
- **Agents:** `cd agents && pip install -r requirements.txt && uvicorn main:app --reload`
- **Devices:** Open the right subfolder (e.g. `devices/esp32/<sketch>/`) in Arduino IDE, or run your RPi app from `devices/rpi/`.

## CI/CD and deployment

GitHub Actions build and deploy to AWS ECS when `backend/`, `frontend/`, or `agents/` change (on push to `main` or tag `v*`). Changes under `devices/` do not trigger cloud deploys.

See [.github/workflows/README.md](.github/workflows/README.md) for secrets, variables, and dev vs prod setup.

## License

## Lint/Test cases

# Run tests only

mvn -f backend/pom.xml test

# Run lint only

mvn -f backend/pom.xml checkstyle:check

# Run both (what CI will do)

mvn -f backend/pom.xml verify

curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
https://kangy-api-dev-223647245649.us-central1.run.app/actuator/health

To revert your CLI to the previously installed version, you may run:
$ gcloud components update --version 562.0.0
