#!/bin/bash

set -e

echo "==================================="
echo "Minikube Deployment Script"
echo "==================================="

# Check if minikube is running
if ! minikube status > /dev/null 2>&1; then
    echo "Starting Minikube..."
    minikube start --cpus 4 --memory 8192 --driver docker --kubernetes-version v1.27
fi

echo ""
echo "Enabling metrics-server addon..."
minikube addons enable metrics-server 2>/dev/null || true

echo ""
echo "Setting up Docker environment..."
eval $(minikube docker-env)

echo ""
echo "Building Docker images in Minikube..."
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service; do
    echo "Building $service..."
    docker build -f Dockerfile.$service -t micro-ecommerce:$service . || exit 1
done

echo ""
echo "Deploying to Minikube..."

# Deploy in order
kubectl apply -f k8s/00-namespace.yaml
sleep 2
kubectl apply -f k8s/01-secrets.yaml
kubectl apply -f k8s/02-configmaps.yaml
sleep 2
kubectl apply -f k8s/03-infrastructure.yaml
sleep 5
kubectl apply -f k8s/04-discovery-server.yaml
sleep 5
kubectl apply -f k8s/05-config-server.yaml
sleep 5
kubectl apply -f k8s/06-api-gateway.yaml
sleep 5
kubectl apply -f k8s/07-customer-service.yaml
kubectl apply -f k8s/08-order-service.yaml
kubectl apply -f k8s/09-inventory-service.yaml
kubectl apply -f k8s/10-payment-service.yaml

echo ""
echo "==================================="
echo "Deployment Complete!"
echo "==================================="
echo ""
echo "Waiting for pods to be ready..."
sleep 10

echo ""
echo "Pod Status:"
kubectl get pods -n ecommerce -o wide

echo ""
echo "Service Status:"
kubectl get svc -n ecommerce

echo ""
echo "Minikube IP: $(minikube ip)"
echo ""
echo "To access API Gateway:"
echo "  kubectl port-forward -n ecommerce svc/api-gateway 8080:80"
echo ""
