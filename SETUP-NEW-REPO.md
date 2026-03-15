# Kangy – New repo setup

Use this when bringing up the **kangy** monorepo from scratch (new clone, or first time configuring CI/CD and AWS).

## 1. Repo and local dev

- **Clone:** `git clone https://github.com/<org>/kangy.git && cd kangy`
- **Backend:** Java 21, Maven. Run: `cd backend && ./mvnw spring-boot:run`
- **Frontend:** Node 18+. Run: `cd frontend && npm install && npm run dev`
- **Agents:** Python 3.10+. Run: `cd agents && pip install -r requirements.txt && uvicorn main:app --reload`
- **Devices:** Not deployed to AWS. Use Arduino IDE / PlatformIO for `devices/esp32/`, `devices/xiao/`; run RPi code from `devices/rpi/`. See [devices/README.md](devices/README.md).

## 2. GitHub Actions (CI/CD)

Workflows in `.github/workflows/` build and deploy **only** the app whose path changed (path filters). Triggers: push to `main` or tag `v*`. See [.github/workflows/README.md](.github/workflows/README.md) for details.

### Secrets (choose one)

| Option | Secret(s) | Notes |
|--------|-----------|--------|
| **A – OIDC** | `AWS_ROLE_ARN` | Recommended. Create OIDC provider for `token.actions.githubusercontent.com` and an IAM role with ECR + ECS permissions. |
| **B – Access keys** | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | In each workflow, use the alternate `configure-aws-credentials` block shown in the workflows README. |

Add under **Settings → Secrets and variables → Actions → Secrets**.

### Variables (optional; defaults in workflows)

| Variable | Default | Purpose |
|----------|---------|---------|
| `AWS_REGION` | `us-east-1` | ECR and ECS region |
| `ECS_CLUSTER` | `my-cluster` | ECS cluster name |
| `ECR_REPOSITORY_BACKEND` | `backend` | ECR repo for backend image |
| `ECR_REPOSITORY_FRONTEND` | `frontend` | ECR repo for frontend image |
| `ECR_REPOSITORY_AGENTS` | `agents` | ECR repo for agents image |
| `ECS_SERVICE_BACKEND` | `backend-service` | ECS service name (backend) |
| `ECS_SERVICE_FRONTEND` | `frontend-service` | ECS service name (frontend) |
| `ECS_SERVICE_AGENTS` | `agents-service` | ECS service name (agents) |

Set under **Settings → Secrets and variables → Actions → Variables** (or rely on defaults).

## 3. AWS prerequisites

Do this **before** the first deploy so the workflows can push images and update services.

1. **ECR**  
   Create one repository per app (or let the first push create them if IAM allows):
   - `backend`
   - `frontend`
   - `agents`

2. **ECS**  
   - One **cluster** (e.g. `my-cluster`).
   - One **Fargate service** per app: `backend-service`, `frontend-service`, `agents-service`.
   - Task definitions must use the ECR image with tag **`latest`** (e.g. `123456789012.dkr.ecr.us-east-1.amazonaws.com/backend:latest`) so `aws ecs update-service --force-new-deployment` picks up new builds.

3. **IAM**  
   - For OIDC: role trust policy for `token.actions.githubusercontent.com`, permissions for ECR (`GetAuthorizationToken`, push) and ECS (`UpdateService`, etc.).
   - For access keys: same ECR + ECS permissions for the IAM user.

## 4. First-time checklist

- [ ] Repo cloned; backend/frontend/agents run locally as needed.
- [ ] GitHub Actions **Secrets** set (OIDC or access keys).
- [ ] GitHub **Variables** set if not using defaults (region, cluster, ECR repos, ECS services).
- [ ] AWS: ECR repos and ECS cluster + services created; task definitions use `…:latest`.
- [ ] Push to `main` or create tag `v*` and confirm the expected workflow(s) run and deploy.

## 5. Dev vs production

To use separate environments (e.g. `develop` vs `main`):

- Use different branches and/or GitHub **Environments** with different variables (e.g. `ECS_SERVICE_BACKEND` → `backend-service-dev` vs `backend-service-prod`).
- Optionally add `develop` to workflow `on.push.branches` and point each branch to its own ECS service. See the “Dev vs prod” section in [.github/workflows/README.md](.github/workflows/README.md).
