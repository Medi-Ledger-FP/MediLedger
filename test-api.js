const fs = require('fs');

async function testApi() {
  fs.writeFileSync('dummy.pdf', 'Dummy PDF Body');
  const uniqueUser = "patient_test_" + Date.now();
  
  // 0. Register
  await fetch('http://localhost:8080/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: uniqueUser, password: "password123", role: "PATIENT" })
  });

  // 1. Login
  const loginRes = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: uniqueUser, password: "password123" })
  });
  
  const loginData = await loginRes.json();
  const token = loginData.token;
  if (!token) {
    console.error("Login failed!", loginData);
    return;
  }
  
  // 2. Upload
  const formData = new FormData();
  formData.append('file', new Blob([fs.readFileSync('dummy.pdf')]), 'dummy.pdf');
  formData.append('patientId', uniqueUser);
  formData.append('recordType', 'Medical Report');
  formData.append('department', 'General');
  
  const uploadRes = await fetch('http://localhost:8080/api/files/upload', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` },
    body: formData
  });
  
  const responseText = await uploadRes.text();
  if (uploadRes.status !== 200) {
    console.log(`Upload Status: ${uploadRes.status} | Body: ${responseText}`);
    return;
  }
  
  const uploadData = JSON.parse(responseText);
  const recordId = uploadData.recordId;
  console.log(`Uploaded ID: ${recordId}`);
  
  // 3. Download Headers
  const dlRes = await fetch(`http://localhost:8080/api/files/download/${recordId}`, {
    method: 'GET',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  const disposition = dlRes.headers.get('content-disposition');
  console.log(`Content-Disposition: ${disposition}`);
}

testApi().catch(console.error);
