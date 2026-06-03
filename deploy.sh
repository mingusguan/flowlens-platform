#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$APP_DIR"

COMMIT_FILE="${COMMIT_FILE:-commitid}"

if [ "${1:-}" != "" ]; then
  IMAGE_TAG="$1"
  printf '%s\n' "$IMAGE_TAG" > "$COMMIT_FILE"
else
  if [ ! -f "$COMMIT_FILE" ]; then
    echo "Missing $COMMIT_FILE. Put the image tag or commit id in this file." >&2
    exit 1
  fi
  IMAGE_TAG="$(sed -n 's/\r$//; /^[[:space:]]*#/d; /^[[:space:]]*$/d; s/^[[:space:]]*//; s/[[:space:]]*$//; p; q' "$COMMIT_FILE")"
fi

if [ "$IMAGE_TAG" = "" ]; then
  echo "$COMMIT_FILE is empty. Put the image tag or commit id in this file." >&2
  exit 1
fi

export FLOWLENS_IMAGE_TAG="$IMAGE_TAG"

if docker compose version >/dev/null 2>&1; then
  compose() {
    docker compose "$@"
  }
elif command -v docker-compose >/dev/null 2>&1; then
  compose() {
    docker-compose "$@"
  }
else
  echo "Docker Compose is not installed." >&2
  exit 1
fi

if [ ! -f ".env" ]; then
  echo "Warning: .env not found, docker compose will use default values." >&2
fi

echo "Deploying flowlens-admin image tag: $FLOWLENS_IMAGE_TAG"
compose config >/dev/null
compose pull flowlens-admin
compose up -d flowlens-admin
compose ps flowlens-admin
