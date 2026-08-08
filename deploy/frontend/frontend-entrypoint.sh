#!/bin/sh
set -eu

/usr/local/bin/render-runtime-config /tmp/community-frontend/app-config.js

exec "$@"
