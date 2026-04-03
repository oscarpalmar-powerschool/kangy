#!/usr/bin/env sh
set -eu

# Runtime configuration for the SPA.
# - KANGY_API_BASE can be empty (same-origin) or e.g. https://api-dev.example.com
# - KANGY_API_KEY must match the FRONTEND_API_KEY on the backend.
API_BASE="${KANGY_API_BASE:-}"
API_KEY="${KANGY_API_KEY:-}"

{
  echo "window.__KANGY_CONFIG__ = {"
  if [ -n "$API_BASE" ]; then echo "  apiBase: \"$API_BASE\","; fi
  if [ -n "$API_KEY" ];  then echo "  apiKey: \"$API_KEY\",";  fi
  echo "};"
} > /usr/share/nginx/html/config.js

exec nginx -g 'daemon off;'
