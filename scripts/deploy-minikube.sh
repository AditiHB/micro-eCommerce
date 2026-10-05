#!/bin/bash
#
# Usage: ./scripts/deploy-minikube.sh [h2|postgres]
# Defaults to h2 (the same default docker-compose.yml uses). Pass postgres
# for a real shared database - needed once you run more than 1 replica per
# service. See docs/KUBERNETES_DEPLOYMENT.md for the full walkthrough.

set -e

PROFILE="${1:-h2}"
if [[ "$PROFILE" != "h2" && "$PROFILE" != "postgres" ]]; then
    echo "Usage: $0 [h2|postgres]"
    exit 1
fi

echo "==================================="
echo "Minikube Deployment Script (profile: $PROFILE)"
echo "==================================="

# Check if minikube is running
if ! minikube status > /dev/null 2>&1; then
    echo "Starting Minikube..."
    minikube start --cpus 4 --memory 6144 --driver docker --kubernetes-version stable
fi

echo ""
echo "Enabling metrics-server addon (required for the HPAs in k8s/base to report real numbers)..."
minikube addons enable metrics-server 2>/dev/null || true

echo ""
echo "Setting up Docker environment..."
eval $(minikube docker-env)

echo ""
echo "Building Docker images in Minikube..."
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service notification-service; do
    echo "Building $service..."
    docker build -f Dockerfile.$service -t micro-ecommerce:$service . || exit 1
done

echo ""
echo "Deploying to Minikube (k8s/overlays/$PROFILE)..."
kubectl apply -k "k8s/overlays/$PROFILE"

echo ""
echo "==================================="
echo "Deployment Complete!"
echo "==================================="
echo ""
echo "Waiting for pods to be ready..."
kubectl wait --for=condition=Ready pods --all -n ecommerce --timeout=300s || true

echo ""
echo "Pod Status:"
kubectl get pods -n ecommerce -o wide

echo ""
echo "Service Status:"
kubectl get svc -n ecommerce

echo ""
echo "HPA Status (TARGETS stays <unknown> for a minute or two after metrics-server starts):"
kubectl get hpa -n ecommerce

echo ""
echo "To access API Gateway:"
echo "  minikube service api-gateway -n ecommerce --url"
echo "  # or: kubectl port-forward -n ecommerce svc/api-gateway 8080:80"
echo ""
