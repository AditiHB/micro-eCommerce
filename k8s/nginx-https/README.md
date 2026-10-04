# nginx-https (optional, untested in this pass)

A TLS-terminating reverse proxy in front of the stack, mirroring
docker-compose.yml's `https` profile. Kept as a starting point, **not**
brought to the same verified bar as `k8s/base`/`k8s/overlays` in this round
of fixes - scope was deliberately kept to deployment + scaling (see
docs/KUBERNETES_DEPLOYMENT.md's "What this round didn't cover" section).

What's already right: `configmap.yaml`'s `upstream.conf`/`nginx.conf`
`proxy_pass` targets already point at the correct, now-matching
order-service:8083 / inventory-service:8082 (this file predates the
port-swap bug fixed in `k8s/base`, and happened to already have it right).

What's not wired up: `deployment.yaml` mounts a `nginx-tls` Secret
(`tls.crt`/`tls.key`) that nothing in this repo creates. The original
design assumed cert-manager + a `Certificate` resource for this (cut from
this round - see docs/KUBERNETES_DEPLOYMENT.md for why), so you'll need to
create it yourself before this Deployment's pods will start, e.g.:

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt -subj "/CN=ecommerce.local"
kubectl create secret tls nginx-tls -n ecommerce --cert=tls.crt --key=tls.key
```

Then:

```bash
kubectl apply -f k8s/nginx-https/configmap.yaml
kubectl apply -f k8s/nginx-https/deployment.yaml
kubectl apply -f k8s/nginx-https/service.yaml
```

Not validated end-to-end in this pass - treat it as a documented starting
point, not a verified deployment path.
