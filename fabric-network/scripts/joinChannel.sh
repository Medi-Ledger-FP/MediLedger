#!/bin/bash

# MediLedger Channel Join Script
# Joins peer0.healthcare.mediledger.com to healthcarechannel

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
GENESIS_BLOCK="${FABRIC_CFG_PATH}/channel-artifacts/genesis.block"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  MediLedger Peer Join Channel${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Verify genesis block exists
if [ ! -f "${GENESIS_BLOCK}" ]; then
    echo -e "${RED}✗ Genesis block not found. Run ./createChannel.sh first${NC}"
    exit 1
fi

# Set environment for peer0.healthcare.mediledger.com
export CORE_PEER_TLS_ENABLED=false
export CORE_PEER_LOCALMSPID="HealthcareOrgMSP"
export CORE_PEER_MSPCONFIGPATH=${PWD}/wallet/admin
export CORE_PEER_ADDRESS=peer0.healthcare.mediledger.com:7051

echo -e "${YELLOW}[1/3] Joining peer0.healthcare.mediledger.com to '${CHANNEL_NAME}'...${NC}"
peer channel join -b ${GENESIS_BLOCK}

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Failed to join channel${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Peer successfully joined channel${NC}"
echo ""

# Verify peer joined
echo -e "${YELLOW}[2/3] Verifying peer channel membership...${NC}"
peer channel list

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Failed to verify channel membership${NC}"
    exit 1
fi
echo ""

# Update anchor peers
echo -e "${YELLOW}[3/3] Updating anchor peer configuration...${NC}"

# Generate anchor peer update transaction
ANCHOR_TX="${FABRIC_CFG_PATH}/channel-artifacts/HealthcareOrgMSPanchors.tx"
configtxgen -profile HealthcareChannel \
    -outputAnchorPeersUpdate ${ANCHOR_TX} \
    -channelID ${CHANNEL_NAME} \
    -asOrg HealthcareOrgMSP \
    -configPath ${FABRIC_CFG_PATH}

# Update anchor peer
peer channel update \
    -o ${ORDERER_URL} \
    -c ${CHANNEL_NAME} \
    -f ${ANCHOR_TX}

if [ $? -ne 0 ]; then
    echo -e "${YELLOW}⚠ Anchor peer update failed (may not be critical)${NC}"
else
    echo -e "${GREEN}✓ Anchor peer updated${NC}"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Peer Join Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Channel setup summary:"
echo "  - Channel: ${CHANNEL_NAME}"
echo "  - Peer: peer0.healthcare.mediledger.com"
echo "  - MSP ID: HealthcareOrgMSP"
echo ""
echo "Next steps:"
echo "  1. Deploy Java chaincode"
echo "  2. Test chaincode invocation"
echo ""
