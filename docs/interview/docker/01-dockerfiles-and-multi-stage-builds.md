# Dockerfiles & Multi-Stage Builds

### Q: Walk through `Dockerfile.order-service`'s two stages — why two, and what does each actually produce?

**A:**
```dockerfile
# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-17 as builder
...
RUN mvn clean package -DskipTests -pl services/order-service -am

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy
...
COPY --from=builder /build/services/order-service/target/order-service-*.jar app.jar
```
Stage 1 has the full Maven + JDK toolchain and produces a built JAR — but that image is large (a JDK, Maven
itself, the entire multi-module source tree, the local `.m2` cache) and none of that is needed to actually
*run* the application. Stage 2 starts completely fresh from a minimal **JRE-only** base image and copies in
just the one artifact stage 1 produced (`COPY --from=builder`). The final image that actually ships and runs
contains no Maven, no JDK compiler, no source code, no build cache — only a JRE and one jar. This is the
standard multi-stage pattern specifically to keep the shipped image small and to avoid leaking build tooling
(and anything it might have cached or logged) into what actually runs in production.

### Q: Why does this Dockerfile `COPY` every module's `pom.xml` individually, then `COPY` the one service's
full source **last**, instead of just `COPY . .` once?

**A:** Docker layer caching: each `COPY`/`RUN` instruction becomes its own image layer, and Docker reuses a
cached layer if its inputs haven't changed since the last build. `pom.xml` files change far less often than
application source code — by copying every `pom.xml` first (triggering Maven's dependency resolution, which
gets cached as its own layer) and only copying the actual service's source code in a later step, a rebuild
after a pure code change can reuse the cached "all dependencies resolved" layer entirely and skip re-downloading
the whole dependency tree. `COPY . .` as a single instruction would invalidate that entire cached layer on
*any* file change anywhere in the repo, including a one-line change to a completely unrelated service's source
file — turning every build into a full dependency re-resolution regardless of what actually changed.

### Q: Why does the runtime stage create a dedicated unprivileged user and `USER 10001:10001` rather than
just running as the image's default (often root)?

**A:** The comment pinned to this exact line states the reasoning directly: *"Run as an unprivileged user: a
compromised JVM must not be root inside the container."* If an attacker found a way to execute arbitrary code
inside this specific JVM process (a deserialization bug, a vulnerable dependency, whatever), running as root
would hand them root *inside the container* — which, depending on what else is misconfigured (a mounted
docker socket, certain kernel capabilities, a container-escape vulnerability), can be a meaningfully worse
outcome than the same compromise contained to an unprivileged, capability-stripped user with no write access
outside its own working directory. This is the same `10001` UID/GID this session's own research confirmed is
mirrored in the Kubernetes `securityContext` (`runAsUser: 10001`) for the same pods — the Docker-level hardening
and the Kubernetes-level hardening agree with each other rather than one undoing the other, which is worth
being able to point out as deliberate consistency, not coincidence.

### Q: What does `HEALTHCHECK` in a Dockerfile actually do, and why does this one hit
`localhost:9083/actuator/health/readiness` specifically, not just `/actuator/health`?

**A:** `HEALTHCHECK` tells the Docker engine itself how to periodically probe whether a container is actually
*working*, not merely "running" (a process can be alive and completely wedged) — the result shows up in
`docker ps` as `healthy`/`unhealthy`/`starting`, and is exactly what Compose's `depends_on: condition:
service_healthy` waits on (see [02](02-compose-orchestration-dependencies-and-profiles.md)). Pointing it at the
**readiness** endpoint specifically — rather than the broader `/actuator/health` — mirrors the same
liveness-vs-readiness distinction covered in the [Spring Boot guide](../spring-boot/10-actuator-and-health-checks.md):
"is this instance ready to actually serve a real request" is the right question for "should a dependent
container be allowed to start now," not just "is the JVM process technically up." And it's on port `9083` — the
**management port**, not the public API port (`8083`) — because this repo deliberately separates the two
(see the microservices guide's observability chapter): the health probe never shares a port, or needs
credentials, with the public API surface.

### Q: Why does the runtime stage `RUN apt-get update && apt-get install -y curl` — isn't adding packages to
a minimal runtime image exactly what multi-stage builds are meant to avoid?

**A:** It's a deliberate, narrow exception, not a contradiction: `curl` is needed *by the `HEALTHCHECK`
instruction itself*, which runs `curl -f http://localhost:9083/...` from inside the running container — without
it, the healthcheck command would simply fail to execute at all. This is the honest tradeoff multi-stage,
minimal-image discipline always involves: the base `eclipse-temurin:17-jre-jammy` image doesn't ship `curl`, so
getting a genuinely functional in-container health probe costs one small package install. The `&& rm -rf
/var/lib/apt/lists/*` on the same line keeps even that addition from leaving apt's package index cache behind
in the image layer — a small discipline that matters more when it's habitual than in any single image's size.
