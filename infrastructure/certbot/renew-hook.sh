#!/bin/bash
# Certificate renewal hook - executed after successful renewal
# This script reloads Nginx configuration after certificates are renewed

set -e

echo "Certificate renewed at $(date)"

# Check if Nginx container is running and reload it
if docker ps | grep -q nginx; then
    echo "Reloading Nginx configuration..."
    docker exec nginx nginx -s reload
    echo "Nginx reloaded successfully"
else
    echo "Nginx container not running, skipping reload"
fi

# Log the renewal
echo "Certificate renewal completed at $(date)" >> /var/log/certbot-renewal.log
