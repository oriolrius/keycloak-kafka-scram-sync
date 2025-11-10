#!/usr/bin/env bash
#
# Download the latest Keycloak Password Sync SPI JAR from GitHub Releases
#
# Usage:
#   ./download-spi.sh [version]
#
# Examples:
#   ./download-spi.sh           # Download latest release
#   ./download-spi.sh v1.0.0    # Download specific version
#

set -euo pipefail

REPO="oriolrius/keycloak-kafka-scram-sync"
OUTPUT_DIR="${OUTPUT_DIR:-./src/target}"
JAR_NAME="keycloak-password-sync-spi.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Parse arguments
VERSION="${1:-latest}"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Construct download URL
if [ "$VERSION" = "latest" ]; then
    log_info "Fetching latest release information..."
    DOWNLOAD_URL="https://github.com/${REPO}/releases/latest/download/${JAR_NAME}"

    # Get the latest version tag for display
    LATEST_TAG=$(curl -s "https://api.github.com/repos/${REPO}/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

    if [ -z "$LATEST_TAG" ]; then
        log_error "Could not determine latest version. Please specify a version explicitly."
        exit 1
    fi

    log_info "Latest version: ${LATEST_TAG}"
else
    log_info "Downloading version: ${VERSION}"
    DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${VERSION}/${JAR_NAME}"
fi

# Download the JAR
OUTPUT_PATH="${OUTPUT_DIR}/${JAR_NAME}"
log_info "Downloading to: ${OUTPUT_PATH}"

if curl -L -f -o "$OUTPUT_PATH" "$DOWNLOAD_URL"; then
    log_info "✅ Successfully downloaded ${JAR_NAME}"
    log_info "File location: ${OUTPUT_PATH}"
    log_info "File size: $(du -h "$OUTPUT_PATH" | cut -f1)"
else
    log_error "❌ Failed to download JAR from ${DOWNLOAD_URL}"
    log_error "Please check:"
    log_error "  1. The release exists: https://github.com/${REPO}/releases"
    log_error "  2. The version tag is correct (e.g., v1.0.0)"
    log_error "  3. You have internet connectivity"
    exit 1
fi
