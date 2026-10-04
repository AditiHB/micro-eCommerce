# Kubernetes Deployment Guide - Micro-eCommerce

This guide provides instructions for deploying the micro-ecommerce microservices system to Kubernetes using Docker images and Helm charts.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Architecture Overview](#architecture-overview)
3. [Docker Images](#docker-images)
4. [Local Deployment (Minikube/Kind)](#local-deployment-minikubukind)
5. [Cloud Deployment](#cloud-deployment)
6. [Helm Deployment](#helm-deployment)
7. [Verification & Testing](#verification--testing)
8. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Tools

- **Docker**: Version 20.10+ ([Install](https://docs.docker.com/get-docker/))
- **Kubernetes Cluster**: One of:
  - **Minikube**: Version 1.26+ ([Install](https://minikube.sigs.k8s.io/docs/start/))
  - **Kind**: Version 0.17+ ([Install](https://kind.sigs.k8s.io/docs/user/quick-start/))
  - **Kubeadm**: For on-premises clusters
- **kubectl**: Version 1.25+ ([Install](https://kubernetes.io/docs/tasks/tools/))
- **Helm**: Version 3.12+ ([Install](https://helm.sh/docs/intro/install/))

### Resource Requirements

- **Minimum**: 4 CPU cores, 8 GB RAM
- **Recommended**: 8 CPU cores, 16 GB RAM

## Architecture Overview

### Components

- **Infrastructure Services**:
  - **Discovery Server** (Eureka): Service discovery and registration
  - **Config Server**: Centralized configuration management
  - **API Gateway**: Single entry point for client requests

- **Microservices**:
  - **Customer Service** (Port 8081): Customer management
  - **Order Service** (Port 8082): Order processing
  - **Inventory Service** (Port 8083): Stock management
  - **Payment Service** (Port 8084): Payment processing

- **Data Layer**:
  - **Redis** (Port 6379): Distributed caching
  - **H2 Database**: In-memory relational database

### Deployment Architecture

```
┌─────────────────────────────────────────┐
│      Load Balancer (API Gateway)        │
│          (LoadBalancer Service)         │
└──────────────┬──────────────────────────┘
               │
    ┌──────────┼──────────┐
    │          │          │
┌───▼──┐  ┌────▼───┐  ┌──▼────┐
│Order │  │Inventory│  │Payment │
│Service  │ Service │  │Service │
└───┬──┘  └────┬───┘  └──┬────┘
    │          │         │
    └──────────┼─────────┘
               │
        ┌──────▼──────┐
        │   Redis     │
        │   Cache     │
        └─────────────┘
```

## Docker Images

### Building Docker Images

Each service has an optimized multi-stage Dockerfile for minimal image size.

#### Build All Images

```bash
# Build discovery-server
docker build -f Dockerfile.discovery-server -t micro-ecommerce:discovery-server .

# Build config-server
docker build -f Dockerfile.config-server -t micro-ecommerce:config-server .

# Build api-gateway
docker build -f Dockerfile.api-gateway -t micro-ecommerce:api-gateway .

# Build customer-service
docker build -f Dockerfile.customer-service -t micro-ecommerce:customer-service .

# Build order-service
docker build -f Dockerfile.order-service -t micro-ecommerce:order-service .

# Build inventory-service
docker build -f Dockerfile.inventory-service -t micro-ecommerce:inventory-service .

# Build payment-service
docker build -f Dockerfile.payment-service -t micro-ecommerce:payment-service .
```

#### Build Script

Create a `build-images.sh` script:

```bash
#!/bin/bash

set -e

SERVICES=("discovery-server" "config-server" "api-gateway" "customer-service" "order-service" "inventory-service" "payment-service")

for service in "${SERVICES[@]}"; do
    echo "Building $service..."
    docker build -f Dockerfile.$service -t micro-ecommerce:$service .
done

echo "All images built successfully!"
docker images | grep micro-ecommerce
```

## Local Deployment (Minikube/Kind)

### Using Minikube

#### Step 1: Start Minikube

```bash
# Start with adequate resources
minikube start \
  --cpus 4 \
  --memory 8192 \
  --driver docker \
  --kubernetes-version v1.27

# Enable metrics-server for resource monitoring
minikube addons enable metrics-server
```

#### Step 2: Build and Load Images

```bash
# For each Dockerfile
eval $(minikube docker-env)

# Build images in Minikube's Docker
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service; do
    docker build -f Dockerfile.$service -t micro-ecommerce:$service .
done
```

#### Step 3: Deploy Using kubectl

```bash
# Apply manifests in order
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-secrets.yaml
kubectl apply -f k8s/02-configmaps.yaml
kubectl apply -f k8s/03-infrastructure.yaml
kubectl apply -f k8s/04-discovery-server.yaml
kubectl apply -f k8s/05-config-server.yaml
kubectl apply -f k8s/06-api-gateway.yaml
kubectl apply -f k8s/07-customer-service.yaml
kubectl apply -f k8s/08-order-service.yaml
kubectl apply -f k8s/09-inventory-service.yaml
kubectl apply -f k8s/10-payment-service.yaml
```

#### Step 4: Access the Service

```bash
# Get Minikube IP
minikube ip

# Forward port for API Gateway
kubectl port-forward -n ecommerce service/api-gateway 8080:80

# Access the API
curl http://localhost:8080/api/customers
```

### Using Kind

#### Step 1: Create Kind Cluster

```bash
# Create a cluster configuration file: kind-config.yaml
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

# Create the cluster
kind create cluster --config kind-config.yaml
```

#### Step 2: Load Docker Images

```bash
# Build all images first
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service; do
    docker build -f Dockerfile.$service -t micro-ecommerce:$service .
done

# Load images into Kind cluster
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service; do
    kind load docker-image micro-ecommerce:$service --name ecommerce
done
```

#### Step 3: Deploy

```bash
# Apply all manifests
kubectl apply -f k8s/

# Verify all resources
kubectl get all -n ecommerce
```

## Cloud Deployment

### Prerequisites

- Cloud CLI configured (AWS CLI, GCP SDK, Azure CLI)
- Container registry access (ECR, GCR, ACR)
- Kubernetes cluster created (EKS, GKE, AKS)

### Push to Container Registry

#### AWS ECR

```bash
# Create ECR repositories
aws ecr create-repository --repository-name micro-ecommerce/discovery-server
aws ecr create-repository --repository-name micro-ecommerce/config-server
aws ecr create-repository --repository-name micro-ecommerce/api-gateway
aws ecr create-repository --repository-name micro-ecommerce/customer-service
aws ecr create-repository --repository-name micro-ecommerce/order-service
aws ecr create-repository --repository-name micro-ecommerce/inventory-service
aws ecr create-repository --repository-name micro-ecommerce/payment-service

# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Tag and push images
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service; do
    docker tag micro-ecommerce:$service <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/micro-ecommerce/$service:latest
    docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/micro-ecommerce/$service:latest
done
```

#### Google Container Registry (GCR)

```bash
# Configure Docker authentication
gcloud auth configure-docker gcr.io

# Tag and push images
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service; do
    docker tag micro-ecommerce:$service gcr.io/PROJECT_ID/micro-ecommerce/$service:latest
    docker push gcr.io/PROJECT_ID/micro-ecommerce/$service:latest
done
```

### Deploy to EKS

```bash
# Update kubeconfig
aws eks update-kubeconfig --name ecommerce-cluster --region us-east-1

# Apply manifests
kubectl apply -f k8s/

# Verify deployment
kubectl get deployments -n ecommerce
kubectl get services -n ecommerce
```

## Helm Deployment

### Using Helm Charts

Helm simplifies deployment with templating and variable management.

#### Installation

```bash
# Add Helm repository (if using private repo)
# helm repo add ecommerce https://example.com/helm
# helm repo update

# Dry-run to see what will be deployed
helm install ecommerce ./helm/ecommerce \
  --namespace ecommerce \
  --create-namespace \
  --dry-run \
  --debug

# Install the chart
helm install ecommerce ./helm/ecommerce \
  --namespace ecommerce \
  --create-namespace
```

#### Custom Values

Create a `custom-values.yaml`:

```yaml
namespace: ecommerce

replicaCount: 3

image:
  registry: your-registry.azurecr.io
  pullPolicy: Always
  tag: v1.0.0

resources:
  limits:
    cpu: 500m
    memory: 512Mi
  requests:
    cpu: 250m
    memory: 256Mi

apiGateway:
  enabled: true
  replicaCount: 3
  service:
    type: LoadBalancer

services:
  customer:
    replicaCount: 3
  order:
    replicaCount: 3
  inventory:
    replicaCount: 3
  payment:
    replicaCount: 3

infrastructure:
  redis:
    persistence:
      size: 5Gi

secrets:
  jwtSecret: "your-production-secret-key"
  dbPassword: "your-secure-password"
  redisPassword: "your-redis-password"
```

Deploy with custom values:

```bash
helm install ecommerce ./helm/ecommerce \
  --namespace ecommerce \
  --create-namespace \
  -f custom-values.yaml
```

#### Upgrade Deployment

```bash
# Upgrade to a new version
helm upgrade ecommerce ./helm/ecommerce \
  --namespace ecommerce \
  -f custom-values.yaml

# Rollback to previous version
helm rollback ecommerce -n ecommerce

# Get release history
helm history ecommerce -n ecommerce
```

## Verification & Testing

### Check Deployment Status

```bash
# Get all resources in ecommerce namespace
kubectl get all -n ecommerce

# Check pod status
kubectl get pods -n ecommerce -o wide

# Check deployment readiness
kubectl get deployment -n ecommerce

# Check services
kubectl get svc -n ecommerce

# Check statefulsets
kubectl get statefulset -n ecommerce
```

### View Logs

```bash
# View logs from a pod
kubectl logs -n ecommerce pod/api-gateway-xxxx

# Stream logs
kubectl logs -f -n ecommerce pod/api-gateway-xxxx

# View logs from all pods in deployment
kubectl logs -n ecommerce -l app=api-gateway --tail=100
```

### Health Check Endpoints

```bash
# Port forward to a service
kubectl port-forward -n ecommerce svc/api-gateway 8080:80

# Test health endpoints
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/metrics
```

### Test API Endpoints

```bash
# Customer Service
curl http://localhost:8080/api/customers

# Order Service
curl http://localhost:8080/api/orders

# Inventory Service
curl http://localhost:8080/api/inventory

# Payment Service
curl http://localhost:8080/api/payments
```

### Monitor Resources

```bash
# Check resource usage
kubectl top nodes -n ecommerce
kubectl top pods -n ecommerce

# Watch pod creation
kubectl get pods -n ecommerce -w

# Describe a pod for troubleshooting
kubectl describe pod -n ecommerce pod/customer-service-xxxxx
```

## Troubleshooting

### Common Issues

#### Pods Not Starting

```bash
# Check pod events
kubectl describe pod -n ecommerce <pod-name>

# View pod logs
kubectl logs -n ecommerce <pod-name>

# Check resource availability
kubectl top nodes
kubectl describe node <node-name>
```

#### Service Discovery Issues

```bash
# Verify Eureka registration
kubectl exec -it -n ecommerce discovery-server-0 -- \
  curl http://localhost:8761/eureka/apps

# Check service connectivity
kubectl exec -it -n ecommerce customer-service-0 -- \
  curl http://discovery-server:8761/eureka/apps/CUSTOMER-SERVICE
```

#### Redis Connection Issues

```bash
# Test Redis connectivity
kubectl exec -it -n ecommerce redis-0 -- redis-cli ping

# Check Redis logs
kubectl logs -n ecommerce -l app=redis

# Verify Redis service
kubectl get svc redis -n ecommerce
```

#### Configuration Issues

```bash
# Verify ConfigMaps
kubectl get configmap -n ecommerce
kubectl describe configmap -n ecommerce discovery-server-config

# Verify Secrets
kubectl get secret -n ecommerce
kubectl describe secret -n ecommerce ecommerce-secrets
```

### Debug Commands

```bash
# Execute commands in container
kubectl exec -it -n ecommerce <pod-name> -- /bin/sh

# Copy files from pod
kubectl cp ecommerce/<pod-name>:/app/app.jar ./app.jar

# Port forward for debugging
kubectl port-forward -n ecommerce <pod-name> 5005:5005

# View resource quotas
kubectl describe quota -n ecommerce
```

## Scaling

### Manual Scaling

```bash
# Scale deployment
kubectl scale deployment -n ecommerce customer-service --replicas=3

# Scale statefulset
kubectl scale statefulset -n ecommerce redis --replicas=2
```

### Horizontal Pod Autoscaler (HPA)

```bash
# Create HPA for API Gateway
kubectl autoscale deployment -n ecommerce api-gateway \
  --min=2 \
  --max=10 \
  --cpu-percent=70

# View HPA status
kubectl get hpa -n ecommerce
```

## Persistence

### Storage Classes

Redis and H2 Database use PersistentVolumeClaims:

```bash
# View persistent volumes
kubectl get pv -n ecommerce

# View persistent volume claims
kubectl get pvc -n ecommerce

# Check storage classes
kubectl get storageclass
```

### Backup & Restore

```bash
# Create backup of Redis data
kubectl exec -n ecommerce redis-0 -- redis-cli BGSAVE

# Backup database
kubectl cp ecommerce/h2-database-0:/opt/h2-data ./h2-backup

# Restore from backup
kubectl cp ./h2-backup ecommerce/h2-database-0:/opt/h2-data
```

## Cleanup

```bash
# Delete all resources in namespace
kubectl delete namespace ecommerce

# Delete using Helm
helm uninstall ecommerce -n ecommerce

# Delete Minikube cluster
minikube delete

# Delete Kind cluster
kind delete cluster --name ecommerce
```

## References

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Helm Documentation](https://helm.sh/docs/)
- [Minikube Documentation](https://minikube.sigs.k8s.io/)
- [Kind Documentation](https://kind.sigs.k8s.io/)
- [Spring Cloud Kubernetes](https://spring.io/projects/spring-cloud-kubernetes)
- [Docker Documentation](https://docs.docker.com/)

## Support

For issues or questions:
1. Check logs: `kubectl logs -n ecommerce <pod-name>`
2. Describe resources: `kubectl describe <resource> -n ecommerce`
3. Review manifest files in `k8s/` directory
4. Consult Helm chart values in `helm/ecommerce/values.yaml`
