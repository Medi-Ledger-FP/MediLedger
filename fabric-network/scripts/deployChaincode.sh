#!/bin/bash

# MediLedger Chaincode Deployment Script
# Packages, installs, approves, and commits Java chaincode to the channel

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuration
CHANNEL_NAME="healthcarechannel"
CHAINCODE_NAME="mediledger-cc"
CHAINCODE_VERSION="1.0"
CHAINCODE_SEQUENCE=1
ORDERER_URL="orderer.mediledger.com:7050"
PEER_URL="peer0.healthcare.mediledger.com:7051"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  MediLedger Chaincode Deployment${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Step 1: Build chaincode JAR
echo -e "${YELLOW}[1/7] Building chaincode JAR...${NC}"
cd ../chaincode
mvn clean package

if [ ! -f "target/chaincode.jar" ]; then
    echo -e "${RED}✗ Chaincode JAR not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Chaincode built: target/chaincode.jar${NC}"
cd ../fabric-network/scripts
echo ""

# Step 2: Package chaincode
echo -e "${YELLOW}[2/7] Packaging chaincode...${NC}"
mkdir -p ../chaincode-packages

# Create chaincode metadata
cat > ../chaincode-packages/metadata.json <<EOF
{
    "type": "java",
    "label": "${CHAINCODE_NAME}_${CHAINCODE_VERSION}"
}
EOF

# Create tarball
tar -czf ../chaincode-packages/code.tar.gz -C ../../chaincode/target chaincode.jar

# Create chaincode package
cd ../chaincode-packages
tar -czf ${CHAINCODE_NAME}.tar.gz metadata.json code.tar.gz
cd ../scripts

echo -e "${GREEN}✓ Chaincode packaged: ${CHAINCODE_NAME}.tar.gz${NC}"
echo ""

# Set environment for peer
export CORE_PEER_TLS_ENABLED=false
export CORE_PEER_LOCALMSPID="HealthcareOrgMSP"
export CORE_PEER_MSPCONFIGPATH=${PWD}/../../wallet/admin
export CORE_PEER_ADDRESS=${PEER_URL}
export FABRIC_CFG_PATH=${PWD}/..

# Step 3: Install chaincode on peer
echo -e "${YELLOW}[3/7] Installing chaincode on peer...${NC}"
peer lifecycle chaincode install ../chaincode-packages/${CHAINCODE_NAME}.tar.gz

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Chaincode installation failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Chaincode installed on peer${NC}"
echo ""

# Step 4: Query installed chaincode to get package ID
echo -e "${YELLOW}[4/7] Querying installed chaincode...${NC}"
peer lifecycle chaincode queryinstalled > installed.txt

PACKAGE_ID=$(grep "${CHAINCODE_NAME}_${CHAINCODE_VERSION}" installed.txt | awk '{print $3}' | sed 's/,$//')

if [ -z "$PACKAGE_ID" ]; then
    echo -e "${RED}✗ Package ID not found${NC}"
    cat installed.txt
    exit 1
fi

echo -e "${GREEN}✓ Package ID: ${PACKAGE_ID}${NC}"
rm installed.txt
echo ""

# Step 5: Approve chaincode for organization
echo -e "${YELLOW}[5/7] Approving chaincode for HealthcareOrgMSP...${NC}"
peer lifecycle chaincode approveformyorg \
    -o ${ORDERER_URL} \
    --channelID ${CHANNEL_NAME} \
    --name ${CHAINCODE_NAME} \
    --version ${CHAINCODE_VERSION} \
    --package-id ${PACKAGE_ID} \
    --sequence ${CHAINCODE_SEQUENCE}

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Chaincode approval failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Chaincode approved${NC}"
echo ""

# Step 6: Check commit readiness
echo -e "${YELLOW}[6/7] Checking commit readiness...${NC}"
peer lifecycle chaincode checkcommitreadiness \
    --channelID ${CHANNEL_NAME} \
    --name ${CHAINCODE_NAME} \
    --version ${CHAINCODE_VERSION} \
    --sequence ${CHAINCODE_SEQUENCE}

echo ""

# Step 7: Commit chaincode
echo -e "${YELLOW}[7/7] Committing chaincode to channel...${NC}"
peer lifecycle chaincode commit \
    -o ${ORDERER_URL} \
    --channelID ${CHANNEL_NAME} \
    --name ${CHAINCODE_NAME} \
    --version ${CHAINCODE_VERSION} \
    --sequence ${CHAINCODE_SEQUENCE} \
    --peerAddresses ${PEER_URL}

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Chaincode commit failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Chaincode committed to channel${NC}"
echo ""

# Verify deployment
echo -e "${YELLOW}Verifying deployment...${NC}"
peer lifecycle chaincode querycommitted \
    --channelID ${CHANNEL_NAME} \
    --name ${CHAINCODE_NAME}

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Chaincode Deployment Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Deployment Summary:"
echo "  - Channel: ${CHANNEL_NAME}"
echo "  - Chaincode: ${CHAINCODE_NAME}"
echo "  - Version: ${CHAINCODE_VERSION}"
echo "  - Package ID: ${PACKAGE_ID}"
echo ""
echo "Test chaincode invocation:"
echo "  peer chaincode invoke -C ${CHANNEL_NAME} -n ${CHAINCODE_NAME} \\"
echo "    -c '{\"function\":\"initLedger\",\"Args\":[]}'"
echo ""
