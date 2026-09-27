#!/bin/bash

#############################################################################
# Local Infrastructure Setup Script
#
# This script automates the setup and startup of the entire local
# microservices infrastructure with all dependencies.
#
# Usage: ./scripts/local-setup.sh [option]
# Options:
#   build        - Build all services (mvn clean package)
#   start        - Start all containers (docker-compose up -d)
#   stop         - Stop all containers (docker-compose down)
#   reset        - Stop and remove all data (docker-compose down -v)
#   logs         - View logs (docker-compose logs -f)
#   health       - Check health of all services
#   clean        - Remove all Docker resources
#   full         - Full setup: build + start (default if no option)
#
# Example:
#   ./scripts/local-setup.sh build
#   ./scripts/local-setup.sh start
#   ./scripts/local-setup.sh health
#############################################################################

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Root directory (parent of scripts directory)
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Functions
print_header() {
    echo -e "${BLUE}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_line() {
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

check_prerequisites() {
    print_header "Checking Prerequisites..."

    # Check Docker
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        echo "Install Docker from: https://www.docker.com/products/docker-desktop"
        exit 1
    fi
    print_success "Docker installed: $(docker --version)"

    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed"
        echo "Install Docker Compose or use Docker Desktop which includes it"
        exit 1
    fi
    print_success "Docker Compose installed: $(docker-compose --version)"

    # Check Java (optional, only if building)
    if [ "$1" == "build" ] || [ "$1" == "full" ]; then
        if ! command -v java &> /dev/null; then
            print_warning "Java is not installed - cannot build"
            echo "Install Java 21+ from: https://adoptium.net/"
            exit 1
        fi
        print_success "Java installed: $(java -version 2>&1 | head -1)"
    fi

    # Check Maven (optional, only if building)
    if [ "$1" == "build" ] || [ "$1" == "full" ]; then
        if ! command -v mvn &> /dev/null; then
            print_warning "Maven is not installed - cannot build"
            echo "Install Maven from: https://maven.apache.org/download.cgi"
            exit 1
        fi
        print_success "Maven installed: $(mvn --version | head -1)"
    fi

    print_success "All prerequisites met"
}

build_services() {
    print_header "Building all services with Maven..."

    cd "$ROOT_DIR"

    # Check if Maven builds are cached
    if [ -f "target/pom.xml" ]; then
        print_warning "Previous build found - using mvn clean to ensure fresh build"
    fi

    # Build with Maven
    mvn clean package -DskipTests -q

    if [ $? -eq 0 ]; then
        print_success "All services built successfully"
    else
        print_error "Build failed - check Maven configuration"
        exit 1
    fi
}

start_services() {
    print_header "Starting Docker containers..."

    cd "$ROOT_DIR"

    # Start containers
    docker-compose up -d

    if [ $? -eq 0 ]; then
        print_success "Docker containers started"
    else
        print_error "Failed to start containers"
        exit 1
    fi

    # Wait for services to be ready
    print_header "Waiting for services to initialize..."
    sleep 5

    # Check container status
    print_header "Container Status:"
    docker-compose ps

    print_success "Services are starting (may take 30-60 seconds to be fully ready)"
}

stop_services() {
    print_header "Stopping Docker containers..."

    cd "$ROOT_DIR"
    docker-compose down

    if [ $? -eq 0 ]; then
        print_success "Containers stopped"
    else
        print_error "Failed to stop containers"
        exit 1
    fi
}

reset_services() {
    print_header "Resetting infrastructure (removing volumes)..."
    print_warning "This will delete all data in databases and volumes"

    read -p "Are you sure? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        print_warning "Reset cancelled"
        return
    fi

    cd "$ROOT_DIR"
    docker-compose down -v

    if [ $? -eq 0 ]; then
        print_success "Infrastructure reset"
    else
        print_error "Failed to reset infrastructure"
        exit 1
    fi
}

view_logs() {
    print_header "Viewing Docker Compose logs..."

    cd "$ROOT_DIR"
    docker-compose logs -f
}

check_health() {
    print_header "Checking Service Health..."
    print_line

    # Define services and their health check URLs
    declare -A services=(
        ["API Gateway"]="http://localhost:8080/actuator/health"
        ["Customer Service"]="http://localhost:8081/actuator/health"
        ["Order Service"]="http://localhost:8083/actuator/health"
        ["Payment Service"]="http://localhost:8084/actuator/health"
        ["Prometheus"]="http://localhost:9090/-/healthy"
        ["Grafana"]="http://localhost:3000/api/health"
        ["Elasticsearch"]="http://localhost:9200/_cluster/health"
        ["Kibana"]="http://localhost:5601/api/status"
    )

    # Check each service
    for service in "${!services[@]}"; do
        url="${services[$service]}"

        if curl -s -f "$url" &>/dev/null; then
            print_success "$service is UP"
        else
            print_warning "$service is DOWN or not responding"
        fi
    done

    print_line

    # Container status
    print_header "Docker Container Status:"
    cd "$ROOT_DIR"
    docker-compose ps

    # Access information
    print_line
    print_header "Service Access Information:"
    echo -e "${GREEN}API Gateway:${NC}       http://localhost:8080"
    echo -e "${GREEN}Swagger UI:${NC}        http://localhost:8080/swagger-ui.html"
    echo -e "${GREEN}Grafana:${NC}           http://localhost:3000 (admin/admin123)"
    echo -e "${GREEN}Prometheus:${NC}        http://localhost:9090"
    echo -e "${GREEN}Kibana:${NC}            http://localhost:5601"
    echo -e "${GREEN}Alertmanager:${NC}      http://localhost:9093"
    echo -e "${GREEN}Config Server:${NC}     http://localhost:8888"
    echo -e "${GREEN}Eureka Discovery:${NC}  http://localhost:8761"
}

clean_docker() {
    print_header "Cleaning Docker resources..."
    print_warning "This will remove all Docker images, containers, and volumes"

    read -p "Are you sure? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        print_warning "Cleanup cancelled"
        return
    fi

    cd "$ROOT_DIR"

    # Stop containers
    docker-compose down -v --rmi all

    # Clean system
    docker system prune -af

    if [ $? -eq 0 ]; then
        print_success "Docker resources cleaned"
    else
        print_error "Failed to clean Docker resources"
        exit 1
    fi
}

show_help() {
    echo "Local Infrastructure Setup Script"
    echo ""
    echo "Usage: $0 [option]"
    echo ""
    echo "Options:"
    echo "  build        - Build all services (mvn clean package)"
    echo "  start        - Start all containers (docker-compose up -d)"
    echo "  stop         - Stop all containers (docker-compose down)"
    echo "  reset        - Stop and remove all data (docker-compose down -v)"
    echo "  logs         - View logs (docker-compose logs -f)"
    echo "  health       - Check health of all services"
    echo "  clean        - Remove all Docker resources"
    echo "  full         - Full setup: build + start (default)"
    echo "  help         - Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 build          # Build services"
    echo "  $0 start          # Start infrastructure"
    echo "  $0 health         # Check service health"
    echo "  $0 reset          # Reset everything"
}

# Main script logic
print_header "╔════════════════════════════════════════════════════════════════╗"
print_header "║     Local Microservices Infrastructure Setup Script           ║"
print_header "╚════════════════════════════════════════════════════════════════╝"
print_line
echo ""

# Get command from argument
COMMAND="${1:-full}"

case "$COMMAND" in
    build)
        check_prerequisites "build"
        build_services
        print_success "Setup complete! Services are built."
        ;;
    start)
        check_prerequisites "start"
        start_services
        print_success "Setup complete! Access services at:"
        echo "  API Gateway: http://localhost:8080"
        echo "  Grafana: http://localhost:3000"
        echo "  Prometheus: http://localhost:9090"
        ;;
    stop)
        stop_services
        ;;
    reset)
        reset_services
        ;;
    logs)
        view_logs
        ;;
    health)
        check_health
        ;;
    clean)
        clean_docker
        ;;
    full)
        check_prerequisites "full"
        build_services
        start_services
        print_success "Setup complete! Access services at:"
        echo "  API Gateway: http://localhost:8080"
        echo "  Grafana: http://localhost:3000"
        echo "  Prometheus: http://localhost:9090"
        echo "  Kibana: http://localhost:5601"
        echo ""
        echo "Services are starting (may take 30-60 seconds to be fully ready)"
        echo "Use '$0 health' to check service status"
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        print_error "Unknown command: $COMMAND"
        echo ""
        show_help
        exit 1
        ;;
esac

echo ""
print_line
print_success "Done!"
