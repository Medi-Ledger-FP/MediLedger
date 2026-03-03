# MediLedger — Blockchain-Secured Medical Records Platform

> A privacy-first, decentralised medical record management system built on **Hyperledger Fabric**, **Spring Boot 3**, and **React**. Patient data is encrypted with **AES-256-GCM**, access is governed by **Ciphertext-Policy Attribute-Based Encryption (CP-ABE)**, emergency recovery is handled through **Shamir Secret Sharing (SSS)**, and all file storage uses **IPFS** via Pinata.

---

## Table of Contents

- [Architecture](#architecture)
- [Security Model](#security-model)
- [Prerequisites](#prerequisites)
- [Running Locally](#running-locally)
- [Default Accounts](#default-accounts)
- [API Reference](#api-reference)
- [Production Deployment](#production-deployment)
- [Project Structure](#project-structure)

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    React Frontend (Port 3000)             │
│        Patient Dashboard │ Doctor Dashboard │ Admin       │
└───────────────────────────┬─────────────────────────────┘
                            │ REST / JWT
┌───────────────────────────▼─────────────────────────────┐
│              Spring Boot 3 API (Port 8080)                │
│   AuthController │ FileController │ EmergencyController   │
│   ConsentController │ AdminController │ AuditController   │
└──────┬──────────────────┬────────────────────┬──────────┘
       │                  │                    │
┌──────▼──────┐  ┌────────▼────────┐  ┌───────▼──────────┐
│  Hyperledger │  │  IPFS / Pinata  │  │  In-Memory Store  │
│    Fabric    │  │ (Encrypted File │  │  (Fallback when   │
│  (Metadata + │  │    Storage)     │  │  Fabric offline)  │
│  Audit Logs) │  └─────────────────┘  └──────────────────┘
└─────────────┘
```

### Key Components

| Component | Technology | Purpose |
|---|---|---|
| Blockchain | Hyperledger Fabric 2.x | Immutable record metadata & audit trail |
| Smart Contracts | Java Chaincode | Record CRUD, access control, audit logging |
| API Server | Spring Boot 3 + Spring Security | REST API, JWT auth, business logic |
| Encryption | AES-256-GCM + CP-ABE | End-to-end file encryption with role-based key access |
| Emergency Access | Shamir Secret Sharing (3-of-5) | Threshold-based AES key recovery |
| File Storage | IPFS via Pinata | Decentralised, content-addressed encrypted file storage |
| Frontend | React | Patient, Doctor, and Admin dashboards |
| Identity | X.509 / Fabric CA + JWT | Dual-layer: blockchain identity + stateless API auth |

---

## Security Model

### File Upload Flow
```
Patient uploads file
  → AES-256-GCM encrypt (random key per file)
  → CP-ABE: AES key encrypted under role policy (e.g. PATIENT ∨ DOCTOR ∨ ADMIN)
  → Shamir: AES key split into 5 shares (threshold = 3) stored server-side
  → Encrypted file pinned to IPFS → CID stored on Fabric blockchain
  → Record metadata committed to blockchain (recordId, CID, fileHash, abePolicy)
```

### File Download Flow
```
Authorised user requests download
  → JWT validated → role extracted
  → CP-ABE: AES key decrypted if role satisfies policy
  → Encrypted file fetched from IPFS by CID
  → SHA-256 integrity check against on-chain hash
  → AES-256-GCM decrypt → original file returned
```

### Emergency Access Flow
```
Emergency request opened (recordId provided)
  → 3 independent approvers submit their SSS shares
  → Server reconstructs AES key from 3-of-5 shares (Lagrange interpolation)
  → Key used to decrypt and serve file without CP-ABE
  → Audit event written to blockchain
```

### Consent Model
- Doctors must obtain **explicit patient consent** per record before downloading
- Patients grant consent from their dashboard by entering the doctor's username
- Consent is stored and enforced at the controller layer before ABE decryption runs

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 17+ | Required for Spring Boot 3 |
| Maven | 3.6+ | Backend build tool |
| Node.js | 18+ | Frontend |
| Docker + Docker Compose | Latest | Hyperledger Fabric network |
| Git | Any | Clone the repo |

**Optional (for full blockchain mode):**
- Hyperledger Fabric binaries (peer, orderer, configtxgen)
- Fabric CA client

---

## Running Locally

### 1. Clone the Repository

```bash
git clone https://github.com/your-org/mediledger.git
cd mediledger
```

### 2. Start the Fabric Network (Optional — backend falls back to in-memory)

```bash
cd fabric-network
docker-compose up -d
```

Verify containers:
```bash
docker ps | grep -E "peer|orderer|ca|couchdb"
```

To stop the network later:
```bash
docker-compose down -v
```

### 3. Build the Backend

```bash
mvn clean package -DskipTests
```

### 4. Start the Backend

Use the provided startup script. It handles killing old instances, setting seed credentials, and waiting for the server to be ready:

```bash
./start-backend.sh
```

Or manually with custom credentials:

```bash
SEED_ADMIN_PASS=Admin@1234 \
SEED_DOCTOR_PASS=Doctor@1234 \
SEED_PATIENT_PASS=Patient@1234 \
java -jar target/mediledger-0.0.1-SNAPSHOT.jar > /tmp/backend.log 2>&1 &
```

The API will be available at **http://localhost:8080**

To watch live logs:
```bash
tail -f /tmp/backend.log
```

### 5. Start the Frontend

```bash
cd mediledger-ui
npm install
npm start
```

The UI will be available at **http://localhost:3000**

### 6. (Optional) Enrol the Fabric Admin Identity

Only needed when the Fabric network is running:

```bash
curl -X POST http://localhost:8080/api/identity/enroll-admin
```

---

## Default Accounts

Every time you start the backend with `./start-backend.sh`, these accounts are automatically available:

| Role | Username | Password |
|---|---|---|
| Admin | `admin_user` | `Admin@1234` |
| Doctor | `doctor_user` | `Doctor@1234` |
| Patient | `patient_user` | `Patient@1234` |

You can register additional patients from the UI's Register page. Doctors and Admins can only be created by an authenticated Admin.

> **Note:** Credentials are passed via environment variables and never stored in source code.

---

## API Reference

### Authentication

```bash
# Register a patient (open)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"Alice@123","role":"PATIENT"}'

# Login (returns JWT token)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"Alice@123"}'

# Register a doctor (admin token required)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{"username":"dr_smith","password":"Dr@12345","role":"DOCTOR"}'
```

### File Operations

```bash
# Upload a medical record
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@report.pdf" \
  -F "patientId=alice" \
  -F "recordType=Lab Report" \
  -F "department=Pathology"

# Download a record (returns original file with correct extension)
curl -X GET http://localhost:8080/api/files/download/<recordId> \
  -H "Authorization: Bearer <TOKEN>" \
  -o downloaded_file.pdf

# List patient records
curl -X GET http://localhost:8080/api/files/patient/<patientId>/records \
  -H "Authorization: Bearer <TOKEN>"
```

### Consent

```bash
# Patient grants a doctor access to a record
curl -X POST http://localhost:8080/api/consent/grant \
  -H "Authorization: Bearer <PATIENT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"doctorId":"dr_smith","recordId":"<recordId>"}'
```

### Emergency Access

```bash
# Open emergency request
curl -X POST http://localhost:8080/api/emergency/request \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"patientId":"alice","reason":"Unconscious patient","recordId":"<recordId>"}'

# Approve with SSS share (repeat from 3 different approvers)
curl -X POST http://localhost:8080/api/emergency/approve/<requestId> \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"shareIndex":1}'

# Download after 3 approvals
curl -X GET http://localhost:8080/api/emergency/download/<requestId> \
  -H "Authorization: Bearer <TOKEN>" \
  -o emergency_file.pdf
```

---

## Production Deployment

> ⚠️ The following steps are required to run MediLedger in a production or staging environment. Do NOT run the backend without these configurations in production.

### 1. Infrastructure Requirements

| Service | Minimum Spec |
|---|---|
| Backend server | 4 vCPU, 8 GB RAM |
| Fabric peer nodes | 2 vCPU, 4 GB RAM each (min. 2 peers) |
| Orderer node | 2 vCPU, 4 GB RAM |
| CouchDB | 2 vCPU, 4 GB RAM |
| Frontend (Nginx) | 1 vCPU, 1 GB RAM |

### 2. Environment Variables (Required)

Never commit these to source control. Use a secrets manager (e.g. AWS Secrets Manager, HashiCorp Vault, or `.env` files outside the repo):

```bash
# Seed accounts
SEED_ADMIN_PASS=<strong-random-password>
SEED_DOCTOR_PASS=<strong-random-password>
SEED_PATIENT_PASS=<strong-random-password>

# JWT signing secret (min 64 chars, cryptographically random)
JWT_SECRET=<64-char-random-string>
JWT_EXPIRY_MS=86400000

# Pinata IPFS
PINATA_API_KEY=<your-pinata-key>
PINATA_SECRET_KEY=<your-pinata-secret>

# Fabric network
FABRIC_ORG=HealthcareOrg1
FABRIC_MSP=HealthcareOrgMSP
FABRIC_PEER_ENDPOINT=grpcs://peer0.org1.example.com:7051
FABRIC_ORDERER_ENDPOINT=grpcs://orderer.example.com:7050
```

### 3. TLS / HTTPS

- Place the backend behind a reverse proxy (Nginx or Traefik)
- Obtain a TLS certificate via Let's Encrypt or your CA
- All Fabric gRPC connections must use TLS (configure in `application.yml`)

Example Nginx config:
```nginx
server {
    listen 443 ssl;
    server_name api.mediledger.yourdomain.com;

    ssl_certificate     /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 4. Replace the In-Memory User Store

The current implementation stores users in a `HashMap` (lost on restart). For production, replace with a persistent database:

1. Add Spring Data JPA + PostgreSQL dependency to `pom.xml`
2. Create a `User` entity with hashed password and role fields
3. Replace `userCredentials` / `userRoles` Maps in `AuthController` with a `UserRepository`
4. Configure `spring.datasource.*` in `application.yml`

### 5. Deploy the Fabric Network

```bash
cd fabric-network

# Generate crypto material
./generateCrypto.sh

# Start network
docker-compose -f docker-compose-prod.yaml up -d

# Create channel and deploy chaincode
./createChannel.sh
./deployChaincode.sh
```

### 6. Build and Run the Backend in Production Mode

```bash
mvn clean package -DskipTests -Pprod

SEED_ADMIN_PASS=$ADMIN_PASS \
SEED_DOCTOR_PASS=$DOCTOR_PASS \
SEED_PATIENT_PASS=$PATIENT_PASS \
java -Xmx2g \
  -Dspring.profiles.active=prod \
  -jar target/mediledger-0.0.1-SNAPSHOT.jar
```

### 7. Build and Serve the Frontend

```bash
cd mediledger-ui

# Set your production API base URL
echo "REACT_APP_API_URL=https://api.mediledger.yourdomain.com" > .env.production

npm install
npm run build

# Serve with Nginx
cp -r build/* /var/www/mediledger/
```

### 8. HIPAA / Healthcare Compliance Checklist

For use in real healthcare environments, also ensure:

- [ ] **Audit logging** is fully enabled and audit records are tamper-proof (on-chain)
- [ ] **Data at rest** encryption for the CouchDB state database
- [ ] **Backup** strategy for IPFS-pinned files and Fabric ledger data
- [ ] **Access reviews** — periodically revoke old doctor/admin accounts
- [ ] **Penetration testing** before go-live
- [ ] **BAA (Business Associate Agreement)** with any cloud/IPFS provider
- [ ] **SSS share distribution** — in production, each SSS share must be held by a distinct trusted custodian, not all accessible from one dashboard

---

## Project Structure

```
MediLedger/
├── src/main/java/com/mediledger/
│   ├── config/
│   │   ├── FabricConfig.java          # Fabric SDK & gateway configuration
│   │   └── SecurityConfig.java        # Spring Security + CORS setup
│   ├── controller/
│   │   ├── AuthController.java        # Register / Login / Seed
│   │   ├── FileController.java        # Upload / Download / Metadata
│   │   ├── EmergencyController.java   # SSS-based emergency access
│   │   ├── ConsentController.java     # Patient consent management
│   │   ├── AdminController.java       # Admin dashboard APIs
│   │   └── AuditController.java       # Audit log retrieval
│   ├── service/
│   │   ├── FileService.java           # Upload/download pipeline (ABE + IPFS)
│   │   ├── EncryptionService.java     # AES-256-GCM encrypt/decrypt
│   │   ├── ABEService.java            # CP-ABE key encryption/decryption
│   │   ├── IPFSService.java           # Pinata IPFS integration
│   │   ├── RecordService.java         # Blockchain/in-memory record store
│   │   ├── ConsentService.java        # Consent store and enforcement
│   │   ├── EmergencyAccessService.java# SSS split + reconstruct + grant
│   │   ├── ShamirSecretSharingService.java # SSS polynomial math
│   │   ├── AuditService.java          # Blockchain audit trail
│   │   ├── FabricGatewayService.java  # Fabric transaction submission
│   │   ├── IdentityService.java       # Fabric CA / wallet management
│   │   └── JwtService.java            # JWT generation + validation
│   └── model/ dto/                    # Request/Response DTOs
├── chaincode/
│   └── src/main/java/com/mediledger/chaincode/
│       ├── RecordLedger.java          # Medical record smart contract
│       └── AuditTrail.java            # Audit log smart contract
├── mediledger-ui/                     # React frontend
│   └── src/
│       ├── components/
│       │   ├── PatientDashboard.js
│       │   ├── DoctorDashboard.js
│       │   └── AdminDashboard.js
│       └── api.js                     # Centralised API client
├── fabric-network/                    # Fabric network config + scripts
├── start-backend.sh                   # One-command backend startup script
├── docker-compose.yaml                # Local Fabric network
└── pom.xml                            # Maven build descriptor
```

---

## License

MIT — see [LICENSE](LICENSE) for details.
