#!/bin/bash

set -e

echo "==================================="
echo "Kind Deployment Script"
echo "==================================="

CLUSTER_NAME="ecommerce"

# Check if cluster exists
if kind get clusters | grep -q "^$CLUSTER_NAME$"; then
    echo "Cluster $CLUSTER_NAME already exists"
else
    echo "Creating Kind cluster: $CLUSTER_NAME"

    cat > kind-config.yaml << 'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: ecommerce
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 80
    hostPort: 80
    listenAddress: "127.0.0.1"
  - containerPort: 8080
    hostPort: 8080
    listenAddress: "127.0.0.1"
EOF

    kind create cluster --config kind-config.yaml
    rm kind-config.yaml
fi

echo ""
echo "Building Docker images..."
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service; do
    echo "Building $service..."
    docker build -f Dockerfile.$service -t micro-ecommerce:$service . || exit 1
done

echo ""
echo "Loading images into Kind cluster..."
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service; do
    echo "Loading $service..."
    kind load docker-image micro-ecommerce:$service --name $CLUSTER_NAME
done

echo ""
echo "Deploying to Kind cluster..."

# Switch to Kind cluster context
kubectl cluster-info --context kind-$CLUSTER_NAME

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
echo "To access API Gateway on localhost:"
echo "  http://localhost:8080/api/customers"
echo ""
