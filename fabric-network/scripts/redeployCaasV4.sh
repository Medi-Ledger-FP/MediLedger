#!/bin/bash
# =============================================================================
# MediLedger Chaincode v4 — CaaS Redeploy (Docker Service Version)
# Chaincode server now runs as Docker container: mediledger-chaincode:9999
# =============================================================================
set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

CHANNEL="healthcarechannel"
CC_NAME="mediledger-cc"
CC_VERSION="1.0"
CC_SEQ=1
ORDERER="orderer.mediledger.com:7050"
PEER_CONTAINER="peer0.healthcare.mediledger.com"
PEER_ADDR="peer0.healthcare.mediledger.com:7051"

ADMIN_MSP="/tmp/admin_msp/msp"
PKG_FILE_HOST="fabric-network/chaincode-packages/${CC_NAME}-v1-caas.tar.gz"
PKG_FILE_CTR="/tmp/${CC_NAME}-v1-caas.tar.gz"

echo -e "${GREEN}==========================================${NC}"
echo -e "${GREEN}  MediLedger CaaS Redeploy — v4${NC}"
echo -e "${GREEN}  (Chaincode runs inside Docker network)${NC}"
echo -e "${GREEN}==========================================${NC}"

# ── Step 1: Build chaincode JAR and Docker image ─────────────────────────────
echo -e "\n${YELLOW}[1/7] Building chaincode JAR...${NC}"
(cd chaincode && mvn clean package -q -DskipTests)

echo -e "\n${YELLOW}     Building Docker image...${NC}"
docker build -t mediledger-chaincode:latest ./chaincode/
echo -e "${GREEN}✓ Docker image built${NC}"

# ── Step 2: Create CaaS package pointing to Docker service name ───────────────
echo -e "\n${YELLOW}[2/7] Creating CaaS package (address: mediledger-chaincode:9999)...${NC}"
PKG_DIR="fabric-network/chaincode-packages/caas-build-v4"
rm -rf "$PKG_DIR"
mkdir -p "$PKG_DIR/code"

cat > "$PKG_DIR/metadata.json" <<EOF
{"type": "ccaas", "label": "${CC_NAME}_${CC_VERSION}"}
EOF

# Address is now the Docker container name — reachable directly on the mediledger network
cat > "$PKG_DIR/code/connection.json" <<EOF
{"address":"mediledger-chaincode:9999","dial_timeout":"300s","tls_required":false}
EOF

(cd "$PKG_DIR/code" && tar -czf ../code.tar.gz connection.json)
(cd "$PKG_DIR" && tar -czf "../${CC_NAME}-v1-caas.tar.gz" metadata.json code.tar.gz)
echo -e "${GREEN}✓ Package created${NC}"

# ── Step 3: Copy package and MSP into container ────────────────────────────────
echo -e "\n${YELLOW}[3/7] Copying files into peer container...${NC}"
docker cp "$PKG_FILE_HOST" "$PEER_CONTAINER:$PKG_FILE_CTR"

HOST_MSP="fabric-network/crypto-config/peerOrganizations/healthcare.mediledger.com/users/Admin@healthcare.mediledger.com/msp"
docker exec "$PEER_CONTAINER" rm -rf /tmp/admin_msp 2>/dev/null || true
docker exec "$PEER_CONTAINER" mkdir -p /tmp/admin_msp
docker cp "$HOST_MSP" "$PEER_CONTAINER:/tmp/admin_msp/"

peer_exec() {
  docker exec \
    -e CORE_PEER_TLS_ENABLED=false \
    -e CORE_PEER_LOCALMSPID="HealthcareOrgMSP" \
    -e CORE_PEER_MSPCONFIGPATH="$ADMIN_MSP" \
    -e CORE_PEER_ADDRESS="$PEER_ADDR" \
    "$PEER_CONTAINER" /usr/local/bin/peer "$@"
}

# ── Step 4: Install on peer ───────────────────────────────────────────────────
echo -e "\n${YELLOW}[4/7] Installing chaincode on peer...${NC}"
peer_exec lifecycle chaincode install "$PKG_FILE_CTR"

PKG_ID=$(peer_exec lifecycle chaincode queryinstalled 2>/dev/null \
  | grep "${CC_NAME}_${CC_VERSION}" | awk '{print $3}' | sed 's/,//' | head -1)

if [ -z "$PKG_ID" ]; then
  echo -e "${RED}✗ Package ID not found${NC}"; exit 1
fi
echo -e "${GREEN}✓ Package ID: ${PKG_ID}${NC}"

# ── Step 5: Approve for org ───────────────────────────────────────────────────
echo -e "\n${YELLOW}[5/7] Approving...${NC}"
peer_exec lifecycle chaincode approveformyorg \
  -o "$ORDERER" --channelID "$CHANNEL" --name "$CC_NAME" \
  --version "$CC_VERSION" --package-id "$PKG_ID" --sequence $CC_SEQ

# ── Step 6: Commit ────────────────────────────────────────────────────────────
echo -e "\n${YELLOW}[6/7] Committing...${NC}"
peer_exec lifecycle chaincode commit \
  -o "$ORDERER" --channelID "$CHANNEL" --name "$CC_NAME" \
  --version "$CC_VERSION" --sequence $CC_SEQ --peerAddresses "$PEER_ADDR"

# ── Step 7: Update chaincode container with Package ID & restart ──────────────
echo -e "\n${YELLOW}[7/7] Restarting chaincode container with Package ID...${NC}"
docker stop mediledger-chaincode 2>/dev/null || true
CC_PACKAGE_ID="$PKG_ID" docker-compose up -d mediledger-chaincode
sleep 5

echo -e "\n${GREEN}==========================================${NC}"
echo -e "${GREEN}  Chaincode v4 deployed successfully!${NC}"
echo -e "${GREEN}  Package ID: ${PKG_ID}${NC}"
echo -e "${GREEN}  Chaincode address: mediledger-chaincode:9999${NC}"
echo -e "${GREEN}==========================================${NC}"
echo -e "\nExport for future use:"
echo -e "  export CC_PACKAGE_ID=\"${PKG_ID}\""
