#!/bin/bash
#
# Usage: ./scripts/deploy-kind.sh [h2|postgres]
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
echo "Kind Deployment Script (profile: $PROFILE)"
echo "==================================="

CLUSTER_NAME="ecommerce"

# Check if cluster exists
if kind get clusters | grep -q "^$CLUSTER_NAME$"; then
    echo "Cluster $CLUSTER_NAME already exists"
else
    echo "Creating Kind cluster: $CLUSTER_NAME"

    # Both api-gateway (k8s/base/07-api-gateway.yaml) and nginx-https
    # (k8s/nginx-https/service.yaml) are type: LoadBalancer, which just sits
    # Pending on Kind (no cloud-provider/MetalLB) - this project always
    # reaches services via `kubectl port-forward` instead (see this script's
    # own closing instructions), so these mappings are never actually used.
    # Kept on unusual high ports rather than 80/8080 so creating this cluster
    # never fights the Docker Compose stack's api-gateway, which legitimately
    # owns host port 8080 whenever that stack is also running locally.
    cat > kind-config.yaml << 'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: ecommerce
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 80
    hostPort: 18880
    listenAddress: "127.0.0.1"
  - containerPort: 8080
    hostPort: 18888
    listenAddress: "127.0.0.1"
EOF

    kind create cluster --config kind-config.yaml
    rm kind-config.yaml
fi

echo ""
echo "Installing metrics-server (required for the HPAs in k8s/base to report real numbers)..."
echo "Kind's kubelet serving certs aren't signed by a CA metrics-server trusts by default -"
echo "this patches it to skip that verification, fine for a local learning cluster."
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl patch deployment metrics-server -n kube-system --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' \
  2>/dev/null || true

echo ""
echo "Building Docker images..."
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service notification-service; do
    echo "Building $service..."
    docker build -f Dockerfile.$service -t micro-ecommerce:$service . || exit 1
done

echo ""
echo "Loading images into Kind cluster..."
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service notification-service; do
    echo "Loading $service..."
    kind load docker-image micro-ecommerce:$service --name $CLUSTER_NAME
done

echo ""
echo "Deploying to Kind cluster ($PROFILE profile)..."

# Switch to Kind cluster context
kubectl cluster-info --context kind-$CLUSTER_NAME

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
echo "  kubectl port-forward -n ecommerce svc/api-gateway 8080:80"
echo "  curl http://localhost:8080/api/customers"
echo ""
