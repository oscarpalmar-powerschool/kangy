# Frontend Deploy Setup

This document describes everything required to deploy the kangy frontend to Google Cloud Run
via GitHub Actions. It mirrors the backend setup — read `BackendDeploySetup.md` first for
shared GCP infrastructure (Workload Identity, service account, Artifact Registry).

---

## Overview

Two GitHub Actions workflows handle the frontend:

| Workflow | File | Trigger | Purpose |
|----------|------|---------|---------|
| Frontend CI | `.github/workflows/frontend-ci.yml` | Auto — push to `main` or tag | Runs TypeScript build only |
| Frontend Deploy | `.github/workflows/frontend-deploy.yml` | Manual — GitHub Actions UI | Builds image, pushes to Artifact Registry, deploys to Cloud Run |

Deployment targets:
- **Development** — `kangy-web-dev` Cloud Run service, deployable from `main` or any tag
- **Production** — `kangy-web` Cloud Run service, deployable from a version tag only (e.g. `v1.2.3`)

---

## GCP Setup (one-time)

### Shared infrastructure — already done

The following were created for the backend and are reused:

- **Artifact Registry repository** — `kangy` in `us-central1`
- **Service account** — `github-actions@project-4d02db76-5b1d-4288-b08.iam.gserviceaccount.com`
- **Workload Identity Pool/Provider** — `github-actions` / `github`
- **Repository secrets** — `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_SERVICE_ACCOUNT`

No new GCP resources are needed for these.

---

### Cloud Run Services

Two services — one per environment. Created automatically on first deploy.

| Service name | Environment | Deployable from |
|-------------|-------------|-----------------|
| `kangy-web-dev` | development | `main` branch or any tag |
| `kangy-web` | production | version tag only (e.g. `v1.2.3`) |

**Port note:** The frontend container runs nginx on port 80. The deploy workflow passes
`--port 80` to `gcloud run deploy` so Cloud Run routes traffic to the correct port.

**Authentication note:**
- Development: set to **Allow unauthenticated invocations** (Cloud Run → service → Edit & Deploy New Revision → Security tab)
- Production: keep authentication enabled

**Runtime environment variable (optional):**
To point the SPA at the backend API, set `KANGY_API_BASE` on the Cloud Run service:
- Cloud Run → service → Edit & Deploy New Revision → Variables & Secrets tab
- Example: `KANGY_API_BASE=https://kangy-api-dev-<hash>-uc.a.run.app`
- If left unset, the frontend assumes the API is same-origin

---

## GitHub Setup (one-time)

### Add `CLOUD_RUN_SERVICE` to existing GitHub Environments

The `development` and `production` environments already exist. Add the frontend service name
as a variable in each:

**Settings → Environments → development → Environment variables:**

| Variable | Value |
|----------|-------|
| `CLOUD_RUN_SERVICE` | `kangy-web-dev` |

> This environment already has `CLOUD_RUN_SERVICE = kangy-api-dev` for the backend.
> GitHub environment variables are scoped per-workflow via the `environment:` key,
> so both values can coexist — each workflow reads only its own `CLOUD_RUN_SERVICE`.
> **However**, each environment can only store one value per variable name.
> If the backend and frontend share the same GitHub Environment, you'll need to either:
> - Use separate GitHub Environments (e.g. `frontend-development`, `frontend-production`), or
> - Hardcode the Cloud Run service names directly in `frontend-deploy.yml`

The simplest approach: hardcode in the workflow. Update `frontend-deploy.yml` to replace
`${{ vars.CLOUD_RUN_SERVICE }}` with the literal service name per environment:

```yaml
- name: Deploy to Cloud Run
  run: |
    SERVICE=$([ "${{ inputs.environment }}" = "production" ] && echo "kangy-web" || echo "kangy-web-dev")
    gcloud run deploy $SERVICE \
      --image $IMAGE:$IMAGE_TAG \
      --region ${{ env.GCP_REGION }} \
      --project ${{ env.GCP_PROJECT_ID }} \
      --port 80 \
      --quiet
```

---

## Workflow Variables Reference

| Variable | Where set | Value |
|----------|-----------|-------|
| `GCP_REGION` | Hardcoded default | `us-central1` |
| `GCP_PROJECT_ID` | Hardcoded default | `project-4d02db76-5b1d-4288-b08` |
| `AR_REPOSITORY` | Hardcoded default | `kangy` |
| `IMAGE_NAME` | Hardcoded in workflow | `kangy-web` |
| `CLOUD_RUN_SERVICE` | GitHub Environment variable | `kangy-web-dev` / `kangy-web` |

---

## Deploy Flow

### Push to main (CI only)
```
git push origin main
→ Frontend CI triggers automatically (if frontend/ changed)
→ Runs: npm ci + npm run build (TypeScript check + Vite build)
→ No deploy
```

### Deploy to development
```
GitHub → Actions → Frontend Deploy → Run workflow
  environment: development
  ref: main  (or a tag, e.g. v1.2.3)
→ Runs npm ci + npm run build
→ Builds Docker image (multi-stage: node build + nginx serve)
→ Pushes to Artifact Registry as kangy-web:GIT_SHA (or kangy-web:v1.2.3 for tags)
→ Deploys to kangy-web-dev Cloud Run service
```

### Deploy to production
```
GitHub → Actions → Frontend Deploy → Run workflow
  environment: production
  ref: v1.2.3  (must be a version tag — main is rejected)
→ Runs npm ci + npm run build
→ Builds Docker image
→ Pushes to Artifact Registry as kangy-web:v1.2.3
→ Deploys to kangy-web Cloud Run service
```

---

## Docker Image Details

The frontend Dockerfile (`frontend/Dockerfile`) is a multi-stage build:

1. **Builder stage** — Node 20 Alpine: runs `npm ci` + `npm run build`, outputs to `/app/dist`
2. **Runtime stage** — nginx Alpine: serves `/app/dist` as static files

Key files copied into the image:
- `nginx.conf` — SPA routing (all paths fall back to `index.html`), no-cache on `/config.js`
- `docker-entrypoint.sh` — generates `/config.js` at container start from `KANGY_API_BASE` env var, then starts nginx

---

## Testing a Deployed Service

```bash
# Mac/Linux
curl https://SERVICE_URL

# With auth token (if unauthenticated access is disabled)
curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" https://SERVICE_URL
```

Get the service URL:
```bash
gcloud run services describe kangy-web-dev --region us-central1 --format 'value(status.url)'
```
