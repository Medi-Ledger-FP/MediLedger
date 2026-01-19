#!/bin/bash

echo "==================================="
echo "MediLedger API Testing Script"
echo "==================================="
echo ""

# Step 1: Enroll Admin
echo "1. Enrolling Admin..."
curl -X POST http://localhost:8080/api/identity/enroll-admin
echo -e "\n"

sleep 2

# Step 2: Register a Doctor
echo "2. Registering Doctor (dr_smith)..."
DOCTOR_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "dr_smith",
    "password": "secure123",
    "role": "DOCTOR"
  }')

echo "$DOCTOR_RESPONSE" | python3 -m json.tool
echo ""

# Extract JWT token
DOCTOR_TOKEN=$(echo "$DOCTOR_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('token', ''))")

sleep 2

# Step 3: Register a Patient
echo "3. Registering Patient (patient_john)..."
PATIENT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "patient_john",
    "password": "patient123",
    "role": "PATIENT"
  }')

echo "$PATIENT_RESPONSE" | python3 -m json.tool
echo ""

sleep 2

# Step 4: Login as Doctor
echo "4. Logging in as dr_smith..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "dr_smith",
    "password": "secure123"
  }')

echo "$LOGIN_RESPONSE" | python3 -m json.tool
echo ""

sleep 2

# Step 5: Get Doctor's Certificate (protected endpoint)
if [ ! -z "$DOCTOR_TOKEN" ]; then
  echo "5. Fetching dr_smith's X.509 Certificate (using JWT)..."
  curl -s -X GET http://localhost:8080/api/identity/certificate/dr_smith \
    -H "Authorization: Bearer $DOCTOR_TOKEN" | python3 -m json.tool
  echo ""
fi

echo ""
echo "==================================="
echo "Testing Complete!"
echo "==================================="
echo ""
echo "Check the wallet directory:"
echo "  ls -la wallet/"
echo ""
