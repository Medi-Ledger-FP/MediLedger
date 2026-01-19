# MediLedger Healthcare Blockchain Wallet

Enterprise healthcare blockchain wallet built with **Spring Boot 3** and **Hyperledger Fabric**.

## Overview

MediLedger provides secure identity management for healthcare professionals and patients on a blockchain network, with cryptographic X.509 certificates and JWT-based authentication.

## Prerequisites

- **Java 17+** (required for Spring Boot 3)
- **Docker** and **Docker Compose**
- **Maven 3.6+**

## Quick Start

### 1. Start the Hyperledger Fabric Network

```bash
docker-compose up -d
```

Verify all containers are running:
```bash
docker ps
```

You should see:
- `ca.healthcare.mediledger.com` - Certificate Authority
- `orderer.mediledger.com` - Orderer node
- `peer0.healthcare.mediledger.com` - Peer node
- `couchdb` - State database

### 2. Build and Run the Application

```bash
mvn clean install
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### 3. Initialize the Admin Identity

**One-time setup** - Enroll the admin with the Certificate Authority:

```bash
curl -X POST http://localhost:8080/api/identity/enroll-admin
```

## API Endpoints

### Public Endpoints (No Authentication Required)

#### Register a New User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "patient1",
    "password": "securePass123",
    "role": "PATIENT"
  }'
```

Roles: `PATIENT`, `DOCTOR`, `ADMIN`

#### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "patient1",
    "password": "securePass123"
  }'
```

Returns a JWT token for authenticated requests.

### Protected Endpoints (Requires JWT)

#### Get User's X.509 Certificate
```bash
curl -X GET http://localhost:8080/api/identity/certificate/patient1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

#### Check if User Exists
```bash
curl -X GET http://localhost:8080/api/identity/exists/patient1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Architecture

### Components

- **Spring Boot 3** - REST API framework
- **Spring Security** - JWT-based authentication
- **Hyperledger Fabric SDK** - Blockchain integration
- **Fabric CA** - Certificate Authority for identity management
- **CouchDB** - Blockchain state database

### Identity Flow

1. **Admin Enrollment**: Bootstrap admin identity with CA
2. **User Registration**: Register user with CA and receive enrollment secret
3. **User Enrollment**: Generate X.509 certificate for blockchain identity
4. **Authentication**: Login with credentials, receive JWT token
5. **Authorization**: Use JWT for accessing protected endpoints

## Project Structure

```
src/main/java/com/mediledger/
├── config/
│   ├── FabricConfig.java        # Fabric SDK configuration
│   └── SecurityConfig.java      # Spring Security setup
├── controller/
│   ├── AuthController.java      # Authentication endpoints
│   └── IdentityController.java  # Identity management
├── model/
│   ├── UserRole.java           # User role enum
│   ├── RegisterRequest.java    # Registration DTO
│   ├── LoginRequest.java       # Login DTO
│   └── AuthResponse.java       # Auth response DTO
├── security/
│   └── JwtAuthenticationFilter.java  # JWT filter
└── service/
    ├── IdentityService.java    # Fabric identity management
    └── JwtService.java         # JWT operations
```

## Configuration

Edit `src/main/resources/application.yml` to customize:

- Server port
- Fabric network settings
- CA configuration
- JWT secret and expiration
- Logging levels

## Next Steps

- Implement chaincode for healthcare records
- Add user database (replace in-memory store)
- Implement role-based access control
- Add health record management endpoints
- Set up channel and deploy chaincode
- Add comprehensive error handling

## License

MIT
