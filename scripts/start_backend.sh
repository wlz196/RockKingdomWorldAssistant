#!/bin/bash
echo "Stopping existing roco-backend..."
pkill -9 -f "roco-backend" || true
sleep 2

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"
echo "Starting roco-backend..."
nohup java -jar backend/target/roco-backend-0.0.1-SNAPSHOT.jar > roco_backend.log 2>&1 < /dev/null &

echo "Waiting for process to initialize..."
sleep 5
tail -n 20 roco_backend.log
