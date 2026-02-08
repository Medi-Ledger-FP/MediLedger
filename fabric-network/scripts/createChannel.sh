#!/bin/bash

# MediLedger Channel Creation Script
# Creates the healthcarechannel and generates genesis block

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
CHANNEL_NAME="healthcarechannel"
ORDERER_URL="orderer.mediledger.com:7050"
FABRIC_CFG_PATH="${PWD}/fabric-network"
CHANNEL_TX_FILE="${FABRIC_CFG_PATH}/channel-artifacts/${CHANNEL_NAME}.tx"
GENESIS_BLOCK="${FABRIC_CFG_PATH}/channel-artifacts/genesis.block"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  MediLedger Channel Creation${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Create channel-artifacts directory
echo -e "${YELLOW}[1/4] Creating channel artifacts directory...${NC}"
mkdir -p ${FABRIC_CFG_PATH}/channel-artifacts
echo -e "${GREEN}✓ Directory created${NC}"
echo ""

# Generate channel configuration transaction
echo -e "${YELLOW}[2/4] Generating channel configuration transaction...${NC}"
configtxgen -profile HealthcareChannel \
    -outputCreateChannelTx ${CHANNEL_TX_FILE} \
    -channelID ${CHANNEL_NAME} \
    -configPath ${FABRIC_CFG_PATH}

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Failed to generate channel transaction${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Channel transaction generated: ${CHANNEL_TX_FILE}${NC}"
echo ""

# Set environment for peer0.healthcare.mediledger.com
export CORE_PEER_TLS_ENABLED=false
export CORE_PEER_LOCALMSPID="HealthcareOrgMSP"
export CORE_PEER_MSPCONFIGPATH=${PWD}/wallet/admin
export CORE_PEER_ADDRESS=peer0.healthcare.mediledger.com:7051

echo -e "${YELLOW}[3/4] Creating channel '${CHANNEL_NAME}'...${NC}"
peer channel create \
    -o ${ORDERER_URL} \
    -c ${CHANNEL_NAME} \
    -f ${CHANNEL_TX_FILE} \
    --outputBlock ${GENESIS_BLOCK}

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Failed to create channel${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Channel '${CHANNEL_NAME}' created successfully${NC}"
echo ""

# Verify genesis block
echo -e "${YELLOW}[4/4] Verifying genesis block...${NC}"
if [ -f "${GENESIS_BLOCK}" ]; then
    BLOCK_SIZE=$(wc -c < "${GENESIS_BLOCK}")
    echo -e "${GREEN}✓ Genesis block created (${BLOCK_SIZE} bytes)${NC}"
else
    echo -e "${RED}✗ Genesis block not found${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Channel Creation Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Next steps:"
echo "  1. Run ./joinChannel.sh to join peers"
echo "  2. Install and deploy chaincode"
echo ""
