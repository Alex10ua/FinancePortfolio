#!/bin/bash
set -euo pipefail

# Git Bash (MSYS) rewrites /tmp/... args into Windows paths — disable that,
# the paths below are container paths. Harmless no-op on Linux.
export MSYS_NO_PATHCONV=1

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="./backups/mongo-$DATE"

echo "Starting MongoDB backup..."
mkdir -p "$BACKUP_DIR"

docker exec mongodb mongodump \
  --username root \
  --password secret \
  --authenticationDatabase admin \
  --quiet \
  --out /tmp/mongodump

docker cp mongodb:/tmp/mongodump/. "$BACKUP_DIR"
docker exec mongodb rm -rf /tmp/mongodump

echo "Backup saved to $BACKUP_DIR"
