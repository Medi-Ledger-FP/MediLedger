#!/bin/bash

# Generate Crypto Materials for MediLedger Network
# This creates all necessary certificates and keys for the Fabric network

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Generating Crypto Materials${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if cryptogen is installed
if ! command -v cryptogen &> /dev/null; then
    echo -e "${RED}✗ cryptogen not found${NC}"
    echo "Please install Hyperledger Fabric binaries:"
    echo "curl -sSL https://bit.ly/2ysbOFE | bash -s -- 2.5.5 1.5.6"
    exit 1
fi

cd "$(dirname "$0")"

# Clean old crypto materials
echo -e "${YELLOW}[1/3] Cleaning old crypto materials...${NC}"
rm -rf crypto-config

# Generate crypto materials
echo -e "${YELLOW}[2/3] Generating certificates and keys...${NC}"
cryptogen generate --config=./crypto-config.yaml --output="./crypto-config"

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Failed to generate crypto materials${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Crypto materials generated${NC}"
echo ""

# List generated files
echo -e "${YELLOW}[3/3] Generated crypto materials:${NC}"
echo "Orderer:"
ls -la crypto-config/ordererOrganizations/mediledger.com/orderers/orderer.mediledger.com/msp 2>/dev/null || echo "  (orderer MSP)"
echo ""
echo "Peer:"
ls -la crypto-config/peerOrganizations/healthcare.mediledger.com/peers/peer0.healthcare.mediledger.com/msp 2>/dev/null || echo "  (peer MSP)"
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Crypto Generation Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Next steps:"
echo "  1. Restart Docker containers: docker-compose restart"
echo "  2. Create channel: ./scripts/createChannel.sh"
echo ""
