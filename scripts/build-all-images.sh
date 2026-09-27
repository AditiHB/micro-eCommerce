#!/bin/bash

set -e

echo "==================================="
echo "Building Docker Images"
echo "==================================="

SERVICES=("discovery-server" "config-server" "api-gateway" "customer-service" "order-service" "inventory-service" "payment-service")

for service in "${SERVICES[@]}"; do
    echo ""
    echo "Building micro-ecommerce:$service..."
    docker build -f Dockerfile.$service -t micro-ecommerce:$service . || exit 1
    echo "✓ Successfully built micro-ecommerce:$service"
done

echo ""
echo "==================================="
echo "Build Complete!"
echo "==================================="
echo ""
docker images | grep micro-ecommerce
