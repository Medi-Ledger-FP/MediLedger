#!/bin/bash
echo "Dummy PDF Body" > dummy.pdf
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"patient_demo","password":"password123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
RESPONSE=$(curl -s -X POST http://localhost:8080/api/files/upload -H "Authorization: Bearer $TOKEN" -F "file=@dummy.pdf" -F "patientId=patient_demo" -F "recordType=X-Ray" -F "department=Radiology")
RECORD_ID=$(echo $RESPONSE | grep -o '"recordId":"[^"]*"' | cut -d'"' -f4)
echo "Uploaded ID: $RECORD_ID"
curl -s -I -X GET "http://localhost:8080/api/files/download/$RECORD_ID" -H "Authorization: Bearer $TOKEN"
