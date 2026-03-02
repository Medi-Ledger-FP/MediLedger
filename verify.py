import requests
import sys
import time

BASE_URL = 'http://localhost:8080/api'
username = f'test_user_{int(time.time())}'
password = 'password123'

print(f"Registering {username}...")
requests.post(f'{BASE_URL}/auth/register', json={'username': username, 'password': password, 'role': 'PATIENT'})

print("Logging in...")
login_res = requests.post(f'{BASE_URL}/auth/login', json={'username': username, 'password': password})
token = login_res.json().get('token')
if not token:
    print("Login failed", login_res.text)
    sys.exit(1)

print("Uploading file...")
headers = {'Authorization': f'Bearer {token}'}
files = {'file': ('test.pdf', b'fake pdf content', 'application/pdf')}
data = {'patientId': username, 'recordType': 'Report', 'department': 'General'}
upload_res = requests.post(f'{BASE_URL}/files/upload', headers=headers, files=files, data=data)

if upload_res.status_code != 200:
    print("Upload failed:", upload_res.status_code, upload_res.text)
    sys.exit(1)

record_id = upload_res.json().get('recordId')
print("Uploaded ID:", record_id)

print("Downloading file...")
download_res = requests.get(f'{BASE_URL}/files/download/{record_id}', headers=headers)
print("Download Status:", download_res.status_code)
if download_res.status_code != 200:
    print("Download Body:", download_res.text)
else:
    print("Content-Disposition:", download_res.headers.get('Content-Disposition'))

