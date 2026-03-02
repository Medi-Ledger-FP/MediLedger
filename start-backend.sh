#!/bin/bash
# ─────────────────────────────────────────────────────────────
#  MediLedger Backend Startup Script
#  Usage: ./start-backend.sh
# ─────────────────────────────────────────────────────────────

# ── Default seed credentials (change these to your own) ──────
export SEED_ADMIN_PASS="${SEED_ADMIN_PASS:-Admin@1234}"
export SEED_DOCTOR_PASS="${SEED_DOCTOR_PASS:-Doctor@1234}"
export SEED_PATIENT_PASS="${SEED_PATIENT_PASS:-Patient@1234}"

# ── Kill any previously running backend ──────────────────────
echo "🔴 Stopping any existing backend..."
pkill -f "mediledger-0.0.1-SNAPSHOT.jar" 2>/dev/null
lsof -ti :8080 | xargs kill -9 2>/dev/null
sleep 2

# ── Start backend in background ──────────────────────────────
echo "🚀 Starting MediLedger backend..."
java -jar "$(dirname "$0")/target/mediledger-0.0.1-SNAPSHOT.jar" > /tmp/backend.log 2>&1 &
BACKEND_PID=$!

# ── Wait for startup ─────────────────────────────────────────
echo "⏳ Waiting for server to start..."
for i in {1..20}; do
  sleep 1
  if strings /tmp/backend.log 2>/dev/null | grep -q "Started MediLedgerApplication"; then
    echo "✅ Backend is up! (PID: $BACKEND_PID)"
    echo ""
    echo "🔑 Seeded accounts:"
    echo "   admin_user  / ${SEED_ADMIN_PASS}  [ADMIN]"
    echo "   doctor_user / ${SEED_DOCTOR_PASS} [DOCTOR]"
    echo "   patient_user/ ${SEED_PATIENT_PASS} [PATIENT]"
    echo ""
    echo "📋 Logs: tail -f /tmp/backend.log"
    exit 0
  fi
done

echo "❌ Backend failed to start. Check logs: cat /tmp/backend.log"
exit 1
