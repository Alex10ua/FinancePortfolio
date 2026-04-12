#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="./backups/mongo-$DATE"

echo "Starting MongoDB backup..."

docker exec mongodb mongodump \
  --username root \
  --password secret \
  --authenticationDatabase admin \
  --out /tmp/mongodump

docker cp mongodb:/tmp/mongodump "$BACKUP_DIR"
docker exec mongodb rm -rf /tmp/mongodump

echo "Backup saved to $BACKUP_DIR"
