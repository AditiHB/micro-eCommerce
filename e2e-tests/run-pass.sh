#!/bin/bash
# Runs one full pass of the HTTP-only e2e runners against a given deployment,
# clearing the gateway's shared per-client-IP rate-limit key in Redis before
# each runner so the fixed 100-req/60s budget never carries over from the
# previous runner in the same pass (all traffic through a single
# `kubectl port-forward` collapses to one client IP - see
# RateLimitingFilter.java). Not a k8s-specific issue: the same would happen
# against the Docker Compose stack if these runners were fired back-to-back
# fast enough; see e2e-tests/README.md's "Known gaps"/troubleshooting notes.
set -e

# Redis requires a password: source the git-ignored .env (scripts/gen-env.sh) or export REDIS_PASSWORD.
: "${REDIS_PASSWORD:?REDIS_PASSWORD is not set - run: set -a; . ./.env; set +a}"

PASS_LABEL="$1"
GATEWAY_URL="$2"
NOTIFICATION_URL="$3"
POSTGRES_PORT="$4"
REDIS_NS="$5"
REDIS_POD="$6"
KUBECTL="$7"

RUNNERS=(CompensatingTransactionRunner CustomerJourneyRunner ErrorHandlingRunner RestApiCoverageRunner TransactionalRollbackRunner)

for runner in "${RUNNERS[@]}"; do
    echo "=== [$PASS_LABEL] clearing rate limiter before $runner ==="
    "$KUBECTL" exec -n "$REDIS_NS" "$REDIS_POD" -- redis-cli -a "$REDIS_PASSWORD" --no-auth-warning eval \
      "for _,k in ipairs(redis.call('keys','rate_limit:*')) do redis.call('del',k) end" 0 >/dev/null

    echo "=== [$PASS_LABEL] running $runner ==="
    mvn -f "$(dirname "$0")/pom.xml" test -Dtest="$runner" \
      -Dgateway.url="$GATEWAY_URL" -Dnotification.url="$NOTIFICATION_URL" -Dpostgres.port="$POSTGRES_PORT"
done
