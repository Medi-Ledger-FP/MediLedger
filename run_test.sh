#!/bin/bash
# Re-register user to ensure credentials exist
curl -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"patient_tester1","password":"password123","role":"PATIENT"}' > /dev/null

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"patient_tester1","password":"password123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo "TOKEN: ${TOKEN:0:10}..."

echo "Fake File Content" > real_test.pdf

UPLOAD_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@real_test.pdf" \
  -F "patientId=patient_tester1" \
  -F "recordType=Radiology Report" \
  -F "department=General")

echo "UPLOAD RESPONSE:"
echo "$UPLOAD_RESP"

RECORD_ID=$(echo "$UPLOAD_RESP" | grep -o '"recordId":"[^"]*"' | cut -d'"' -f4)
echo "RECORD_ID: $RECORD_ID"

if [ ! -z "$RECORD_ID" ]; then
  curl -s -I -X GET http://localhost:8080/api/files/download/$RECORD_ID -H "Authorization: Bearer $TOKEN" > headers.txt
  cat headers.txt | grep -i content-disposition
fi
