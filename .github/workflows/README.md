# Monorepo CI/CD – path filters + ECS deploy

These workflows run only when their path changed (Option 1 versioning):

- **On push to `main`:** compare with the previous commit.
- **On tag `v*`:** compare with the **previous tag** (e.g. `v1.5.1` vs `v1.5.0`). If only `devices/` changed, no backend/frontend/agents deploy runs.

## Repo layout expected

```
backend/          # Spring Boot, must have backend/Dockerfile
frontend/         # React/TS, must have frontend/Dockerfile (e.g. multi-stage: node build + nginx)
agents/           # FastAPI + LangChain, must have agents/Dockerfile
devices/          # ESP32, XIAO, RPi, etc. (no deploy to AWS; see devices/README.md)
```

## GitHub configuration

### Secrets (one of the two options)

**Option A – OIDC (recommended)**  
- `AWS_ROLE_ARN` – ARN of the IAM role used for GitHub OIDC.  
- In AWS: create an OIDC identity provider for `token.actions.githubusercontent.com` and a role that ECR + ECS can use.

**Option B – Access keys**  
- `AWS_ACCESS_KEY_ID`  
- `AWS_SECRET_ACCESS_KEY`  
In each workflow, replace the `configure-aws-credentials` step with:

```yaml
- name: Configure AWS credentials
  if: steps.filter.outputs.backend == 'true'  # or frontend/agents
  uses: aws-actions/configure-aws-credentials@v4
  with:
    aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
    aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
    aws-region: ${{ env.AWS_REGION }}
```

### Variables (optional, have defaults)

| Variable | Default | Description |
|----------|---------|-------------|
| `AWS_REGION` | `us-east-1` | Region for ECR and ECS |
| `ECS_CLUSTER` | `my-cluster` | ECS cluster name |
| `ECR_REPOSITORY_BACKEND` | `backend` | ECR repo name for backend |
| `ECR_REPOSITORY_FRONTEND` | `frontend` | ECR repo name for frontend |
| `ECR_REPOSITORY_AGENTS` | `agents` | ECR repo name for agents |
| `ECS_SERVICE_BACKEND` | `backend-service` | ECS service name for backend |
| `ECS_SERVICE_FRONTEND` | `frontend-service` | ECS service name for frontend |
| `ECS_SERVICE_AGENTS` | `agents-service` | ECS service name for agents |

Set these under **Settings → Secrets and variables → Actions → Variables** (or in each workflow).

## AWS prerequisites

1. **ECR:** one repository per app (e.g. `backend`, `frontend`, `agents`). The first push will create the repo if your IAM policy allows it, or create them manually.
2. **ECS:** one cluster and one Fargate service per app. Task definition should use the ECR image with tag **`latest`** (e.g. `123456789.dkr.ecr.us-east-1.amazonaws.com/backend:latest`) so `force-new-deployment` pulls the new image.

## Deploy behavior

- Push to `main` or tag `v*` triggers all three workflows.
- Each workflow computes “base ref” (previous commit or previous tag), then runs **dorny/paths-filter**.
- Only if that app’s path changed does it build the Docker image, push to ECR, and run `aws ecs update-service ... --force-new-deployment`.
- So: devices-only changes → tag `v1.5.1` → no backend/frontend/agents changes → no ECS deploy, only devices can use the tag for their own release.

## Dev vs prod

To separate **develop** and **production**, you can:

- Use two branches: e.g. `develop` (deploy to dev ECS services) and `main` (deploy to prod), or
- Use two ECS services (e.g. `backend-service-dev`, `backend-service-prod`) and set **environments** in GitHub so `main` deploys to prod and `develop` to dev (different `vars` per environment).

Then duplicate the trigger in each workflow, e.g.:

```yaml
on:
  push:
    branches: [main, develop]
    tags: ['v*']
```

and use `vars.ECS_SERVICE_BACKEND` (or different variable names per environment) so each branch targets the right service.
