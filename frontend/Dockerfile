# Frontend (React/TypeScript) – multi-stage: build then serve with nginx
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
# Support first-time builds where package-lock.json isn't committed yet.
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY . .
RUN npm run build

# Runtime: nginx serves static files (adjust path if your build output differs, e.g. build/ for CRA)
FROM nginx:alpine
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
EXPOSE 80
CMD ["/docker-entrypoint.sh"]
