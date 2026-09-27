FROM nginx:1.25-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Copy Nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf
COPY ssl.conf /etc/nginx/ssl.conf
COPY ssl-params.conf /etc/nginx/ssl-params.conf
COPY upstream.conf /etc/nginx/upstream.conf

# Create directories for certificates and keys
RUN mkdir -p /etc/nginx/certs

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f -k https://localhost/health || exit 1

# Expose ports
EXPOSE 80 443

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
