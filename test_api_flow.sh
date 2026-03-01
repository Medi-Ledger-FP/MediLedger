#!/bin/bash
PATIENT_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"pat_test1","password":"123","role":"PATIENT"}' | grep -o 'ey[a-zA-Z0-9._-]*')
echo "Patient Token: $PATIENT_TOKEN"

ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"adm_test1","password":"123","role":"ADMIN"}' | grep -o 'ey[a-zA-Z0-9._-]*')
echo "Admin Token: $ADMIN_TOKEN"

DOC_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/admin/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"username":"doc_test1","password":"123","role":"DOCTOR"}' | grep -o 'ey[a-zA-Z0-9._-]*')
echo "Doctor Token: $DOC_TOKEN"

echo "This is secure med data" > med_data.txt
UPLOAD_RES=$(curl -s -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $PATIENT_TOKEN" \
  -F "file=@med_data.txt" \
  -F "recordType=Lab Report" \
  -F "department=Cardiology")
echo "Upload Result: $UPLOAD_RES"
REC_ID=$(echo $UPLOAD_RES | grep -o '"recordId":"[^"]*' | cut -d'"' -f4)
echo "Record ID: $REC_ID"

echo "Unauthorized Doctor Tries to Download"
curl -s -X GET http://localhost:8080/api/files/download/$REC_ID \
  -H "Authorization: Bearer $DOC_TOKEN" -w "\nStatus: %{http_code}\n"
