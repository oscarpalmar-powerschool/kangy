#!/usr/bin/env sh
set -eu

# Runtime configuration for the SPA.
# - KANGY_API_BASE can be empty (same-origin) or e.g. https://api-dev.example.com
API_BASE="${KANGY_API_BASE:-}"

cat > /usr/share/nginx/html/config.js <<EOF
window.__KANGY_CONFIG__ = {
  apiBase: ${API_BASE:+\"$API_BASE\"}
};
EOF

exec nginx -g 'daemon off;'

