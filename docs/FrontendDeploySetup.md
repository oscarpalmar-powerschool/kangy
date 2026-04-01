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

The following were created for the backend and are reused as-is:

- **Artifact Registry repository** — `kangy` in `us-central1`
- **Service account** — `github-actions@project-4d02db76-5b1d-4288-b08.iam.gserviceaccount.com`
- **Workload Identity Pool/Provider** — `github-actions` / `github`
- **Repository secrets** — `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_SERVICE_ACCOUNT`

No new GCP resources are needed.

---

### Cloud Run Services

Two services — one per environment. Created automatically on first deploy.

| Service name | Environment | Deployable from |
|-------------|-------------|-----------------|
| `kangy-web-dev` | development | `main` branch or any tag |
| `kangy-web` | production | version tag only (e.g. `v1.2.3`) |

**Port note:** The frontend container runs nginx on port 80. The deploy workflow passes
`--port 80` to `gcloud run deploy` so Cloud Run routes traffic to the correct port.

---

## GitHub Setup (one-time)

No additional GitHub configuration is needed. The `GCP_WORKLOAD_IDENTITY_PROVIDER` and
`GCP_SERVICE_ACCOUNT` repository secrets are already set from the backend.

**Note on `CLOUD_RUN_SERVICE`:** The backend deploy workflow reads the target service name
from a `CLOUD_RUN_SERVICE` GitHub Environment variable. The frontend deploy workflow instead
hardcodes the service names (`kangy-web-dev` / `kangy-web`) directly in the workflow file.
This is intentional — the `development` and `production` GitHub Environments are shared
between both workflows, and a GitHub Environment can only hold one value per variable name,
so both workflows cannot read `CLOUD_RUN_SERVICE` and get different values. See comments in
both deploy workflow files for the full explanation.

---

## Workflow Variables Reference

| Variable | Where set | Value |
|----------|-----------|-------|
| `GCP_REGION` | Hardcoded default | `us-central1` |
| `GCP_PROJECT_ID` | Hardcoded default | `project-4d02db76-5b1d-4288-b08` |
| `AR_REPOSITORY` | Hardcoded default | `kangy` |
| `IMAGE_NAME` | Hardcoded in workflow | `kangy-web` |
| `CLOUD_RUN_SERVICE` | Hardcoded in workflow | `kangy-web-dev` / `kangy-web` |

---

## First Deploy Checklist

These steps are required once after the very first deploy to each environment. The Cloud Run
services are created by the deploy workflow; the steps below configure them afterward.

### 1. Trigger the deploy

GitHub → **Actions** → **Frontend Deploy** → **Run workflow**
- environment: `development`
- ref: `main`

### 2. Allow unauthenticated access on the frontend service

The GCP Console UI for this setting moves around between releases — the CLI is more reliable:

```bash
gcloud run services add-iam-policy-binding kangy-web-dev \
  --region us-central1 \
  --project project-4d02db76-5b1d-4288-b08 \
  --member="allUsers" \
  --role="roles/run.invoker"
```

Do **not** run this for `kangy-web` (production).

Verify it took effect:
```bash
gcloud run services get-iam-policy kangy-web-dev \
  --region us-central1 \
  --project project-4d02db76-5b1d-4288-b08
```
You should see `allUsers` with `roles/run.invoker` in the output.

### 3. Allow unauthenticated access on the backend service

The frontend calls the backend API from the browser — the browser has no GCP identity token,
so the backend dev service also needs to allow unauthenticated invocations:

```bash
gcloud run services add-iam-policy-binding kangy-api-dev \
  --region us-central1 \
  --project project-4d02db76-5b1d-4288-b08 \
  --member="allUsers" \
  --role="roles/run.invoker"
```

Do **not** run this for `kangy-api` (production).

### 4. Set the backend API URL on the frontend service

The `KANGY_API_BASE` environment variable tells the frontend where to find the backend.
This is stored on the Cloud Run **service**, so every future revision inherits it automatically
— you do not need to repeat this on each deploy.

First, get the backend dev URL:
```bash
gcloud run services describe kangy-api-dev \
  --region us-central1 \
  --project project-4d02db76-5b1d-4288-b08 \
  --format 'value(status.url)'
```

Then set it on the frontend service:
```bash
gcloud run services update kangy-web-dev \
  --region us-central1 \
  --project project-4d02db76-5b1d-4288-b08 \
  --set-env-vars KANGY_API_BASE=<paste-url-here>
```

This triggers a new revision automatically — no redeploy needed.

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
- `docker-entrypoint.sh` — generates `/config.js` at container start from `KANGY_API_BASE`,
  then starts nginx. If `KANGY_API_BASE` is unset the frontend assumes the API is same-origin.

---

## Verifying a Deployed Service

Get the frontend URL:
```bash
gcloud run services describe kangy-web-dev \
  --region us-central1 \
  --project project-4d02db76-5b1d-4288-b08 \
  --format 'value(status.url)'
```

Open the URL in a browser. Use devtools → Network tab to confirm API calls are reaching
the backend URL and returning 200s.
