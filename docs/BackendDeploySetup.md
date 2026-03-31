# Backend Deploy Setup

This document describes everything required to deploy the kangy backend to Google Cloud Run
via GitHub Actions. Use it as a reference when setting up the frontend deployment.

---

## Overview

Two GitHub Actions workflows handle the backend:

| Workflow | File | Trigger | Purpose |
|----------|------|---------|---------|
| Backend CI | `.github/workflows/backend-ci.yml` | Auto — push to `main` or tag | Runs lint + tests only |
| Backend Deploy | `.github/workflows/backend-deploy.yml` | Manual — GitHub Actions UI | Builds image, pushes to Artifact Registry, deploys to Cloud Run |

Deployment targets:
- **Development** — `kangy-api-dev` Cloud Run service, deployable from `main` or any tag
- **Production** — `kangy-api` Cloud Run service, deployable from a version tag only (e.g. `v1.2.3`)

---

## GCP Setup (one-time)

### 1. Artifact Registry Repository

- **Console**: Artifact Registry → Create Repository
- **Name**: `kangy`
- **Format**: Docker
- **Region**: `us-central1`

Images are pushed as:
```
us-central1-docker.pkg.dev/project-4d02db76-5b1d-4288-b08/kangy/IMAGE_NAME:TAG
```

---

### 2. Service Account

- **Console**: IAM & Admin → Service Accounts → Create Service Account
- **Name**: `github-actions`
- **Email**: `github-actions@project-4d02db76-5b1d-4288-b08.iam.gserviceaccount.com`

**Roles to assign** (IAM & Admin → IAM → Grant Access):

| Role | Purpose |
|------|---------|
| Artifact Registry Writer | Push Docker images |
| Cloud Run Developer | Deploy Cloud Run services |
| Service Account User | Allows acting as the default Compute service account during Cloud Run deploy |

---

### 3. Workload Identity Federation

Allows GitHub Actions to authenticate to GCP without a long-lived key.

- **Console**: IAM & Admin → Workload Identity Federation
- **Pool name/ID**: `github-actions`
- **Provider name/ID**: `github`
- **Provider type**: OpenID Connect (OIDC)
- **Issuer URL**: `https://token.actions.githubusercontent.com`

**Attribute mappings:**

| Google attribute | OIDC claim |
|-----------------|------------|
| `google.subject` | `assertion.sub` |
| `attribute.repository` | `assertion.repository` |

**Attribute condition:**
```
attribute.repository == "oscarpalmar-powerschool/kangy"
```

**Grant service account access to the pool:**
- Console: IAM & Admin → Service Accounts → `github-actions` → Permissions → Principals with access → Grant Access
- Principal:
  ```
  principalSet://iam.googleapis.com/projects/223647245649/locations/global/workloadIdentityPools/github-actions/attribute.repository/oscarpalmar-powerschool/kangy
  ```
- Role: **Workload Identity User**

---

### 4. Cloud Run Services

Two services are needed — one per environment. They are created automatically on the first
deploy, but the region and project must match.

| Service name | Environment | Deployable from |
|-------------|-------------|-----------------|
| `kangy-api-dev` | development | `main` branch or any tag |
| `kangy-api` | production | version tag only (e.g. `v1.2.3`) |

**Authentication note:** By default Cloud Run requires authentication.
- Development: set to **Allow unauthenticated invocations** (Cloud Run → service → Edit & Deploy New Revision → Security tab) for easier testing
- Production: keep authentication enabled

---

## GitHub Setup (one-time)

### Repository Secrets

**Settings → Secrets and variables → Actions → Repository secrets:**

| Secret | Value | Notes |
|--------|-------|-------|
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/223647245649/locations/global/workloadIdentityPools/github-actions/providers/github` | From GCP Workload Identity Federation provider page (no `https://` prefix) |
| `GCP_SERVICE_ACCOUNT` | `github-actions@project-4d02db76-5b1d-4288-b08.iam.gserviceaccount.com` | Service account email |

---

### GitHub Environments

**Settings → Environments — create two environments:**

#### `development`

| Variable | Value |
|----------|-------|
| `CLOUD_RUN_SERVICE` | `kangy-api-dev` |

#### `production`

| Variable | Value |
|----------|-------|
| `CLOUD_RUN_SERVICE` | `kangy-api` |

Optional: add a **required reviewer** rule on the `production` environment for a human approval
gate before production deploys run.

---

## Workflow Variables Reference

These are resolved in the deploy workflow in this order: environment variable → repo variable → hardcoded default.

| Variable | Where set | Value |
|----------|-----------|-------|
| `GCP_REGION` | Hardcoded default | `us-central1` |
| `GCP_PROJECT_ID` | Hardcoded default | `project-4d02db76-5b1d-4288-b08` |
| `AR_REPOSITORY` | Hardcoded default | `kangy` |
| `IMAGE_NAME` | Hardcoded in workflow | `kangy-api` |
| `CLOUD_RUN_SERVICE` | GitHub Environment variable | `kangy-api-dev` / `kangy-api` |

---

## Deploy Flow

### Push to main (CI only)
```
git push origin main
→ Backend CI triggers automatically
→ Runs: mvn verify (checkstyle + 62 tests)
→ No deploy
```

### Deploy to development
```
GitHub → Actions → Backend Deploy → Run workflow
  environment: development
  ref: main  (or a tag, e.g. v1.2.3)
→ Runs lint + tests
→ Builds Docker image
→ Pushes to Artifact Registry as kangy-api:GIT_SHA (or kangy-api:v1.2.3 for tags)
→ Deploys to kangy-api-dev Cloud Run service
```

### Deploy to production
```
GitHub → Actions → Backend Deploy → Run workflow
  environment: production
  ref: v1.2.3  (must be a version tag — main is rejected)
→ Runs lint + tests
→ Builds Docker image
→ Pushes to Artifact Registry as kangy-api:v1.2.3
→ Deploys to kangy-api Cloud Run service
```

---

## Testing a Deployed Service

From a machine with `gcloud` installed and authenticated:

```powershell
# Windows PowerShell
$token = gcloud auth print-identity-token
Invoke-WebRequest -Uri "https://SERVICE_URL/actuator/health" -Headers @{Authorization = "Bearer $token"}
```

```bash
# Mac/Linux
curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" https://SERVICE_URL/actuator/health
```

Swagger UI (if service allows unauthenticated access):
```
https://SERVICE_URL/swagger-ui
```

---

## Notes for Frontend Setup

The frontend will follow the same pattern. When setting it up:

- Reuse the same **Workload Identity Pool** (`github-actions`) and **service account** (`github-actions`) — no need to create new ones
- Create a new Artifact Registry repository or add a new image to the existing `kangy` repository
- Add `CLOUD_RUN_SERVICE` to the `development` and `production` GitHub environments for the frontend service names
- The `GCP_WORKLOAD_IDENTITY_PROVIDER` and `GCP_SERVICE_ACCOUNT` repository secrets are already set — they are shared across workflows
- Update the attribute condition on the Workload Identity Provider if the frontend lives in a different repository
