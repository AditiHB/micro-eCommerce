#!/bin/bash

set -e

echo "==================================="
echo "Helm Deployment Script"
echo "==================================="

# Check if Helm is installed
if ! command -v helm &> /dev/null; then
    echo "Helm is not installed. Please install Helm first."
    exit 1
fi

echo "Helm version:"
helm version

echo ""
echo "Building Docker images..."
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service; do
    echo "Building $service..."
    docker build -f Dockerfile.$service -t micro-ecommerce:$service . || exit 1
done

echo ""
echo "Validating Helm chart..."
helm lint ./helm/ecommerce

echo ""
echo "Dry-run deployment..."
helm install ecommerce ./helm/ecommerce \
    --namespace ecommerce \
    --create-namespace \
    --dry-run \
    --debug | head -50

echo ""
read -p "Proceed with deployment? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Deploying Helm chart..."
    helm install ecommerce ./helm/ecommerce \
        --namespace ecommerce \
        --create-namespace

    echo ""
    echo "==================================="
    echo "Deployment Complete!"
    echo "==================================="
    echo ""
    echo "Release Status:"
    helm status ecommerce -n ecommerce

    echo ""
    echo "To upgrade deployment:"
    echo "  helm upgrade ecommerce ./helm/ecommerce -n ecommerce"
    echo ""
    echo "To rollback:"
    echo "  helm rollback ecommerce -n ecommerce"
    echo ""
else
    echo "Deployment cancelled"
    exit 1
fi
