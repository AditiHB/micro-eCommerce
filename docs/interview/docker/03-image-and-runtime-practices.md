# Image & Runtime Practices

### Q: What resource constraints does this repo actually set at the Compose level, and how is that different
from what Kubernetes sets?

**A:** `mem_limit`/`mem_reservation` directly on individual Compose service definitions — e.g. Kafka:
`mem_limit: 640m`, `mem_reservation: 320m`; Redis: `mem_limit: 64m`. These are Docker-engine-level cgroup
constraints enforced on the single host running Compose. Kubernetes' `resources.requests`/`resources.limits`
(covered in the [Kubernetes guide](../kubernetes/02-scaling-and-resource-management.md)) express the same
underlying idea — bound how much a container can consume, and declare how much it needs for scheduling — but
Compose's version is a fixed, single-host constraint with no scheduler behind it, while Kubernetes' version
also drives *where* a pod gets placed across a multi-node cluster and feeds its own autoscaling decisions. Same
concept, very different blast radius: Compose can only tell a container "you may use at most X" on this one
machine; Kubernetes additionally decides *which machine* gets to run it at all based on that same declared
number.

### Q: Why does Kafka specifically get `KAFKA_HEAP_OPTS: "-Xms192m -Xmx320m"` set explicitly, while most of
the Spring Boot services in this Compose file don't set JVM heap flags at all?

**A:** The JVM's default heap-sizing heuristics (a fraction of available container/host memory) are usually a
reasonable default for an ordinary application process, but Kafka's JVM has a known, well-documented tendency
to want to grow its heap aggressively if left to its own defaults, which conflicts directly with the
`mem_limit: 640m` constraint sitting right next to it — an unconstrained heap inside a hard-capped container is
a recipe for the container getting OOM-killed by the kernel rather than the JVM gracefully managing its own
memory pressure. Explicitly bounding the heap to a known-safe range, comfortably inside the container's memory
limit, avoids that specific interaction; for the ordinary business services here, the default heuristics
inside their own `mem_limit`-equivalent (set at the Kubernetes layer, not Compose, for those) are closer to
"good enough without manual tuning," so there was no need to override them.

### Q: Beyond the runtime-stage minimalism covered in [01](01-dockerfiles-and-multi-stage-builds.md), what's
the actual cost of an image being large, concretely — beyond "it takes up more disk"?

**A:** Slower cold starts and slower rolling deploys, most concretely — in Kubernetes, a node that doesn't
already have an image cached has to pull the full thing before a pod can even begin starting, which directly
adds to how long a rolling update or an autoscale-driven new pod takes to become ready (and, per the
[Kubernetes guide](../kubernetes/03-health-probes-and-self-healing.md)'s readiness-probe discussion, a pod isn't
receiving traffic until it passes its readiness check *after* that pull and startup finish). A multi-stage
build's smaller final image is genuinely a scaling-responsiveness concern, not just a tidiness one — a system
that needs to autoscale quickly under a real traffic spike (the exact scenario this repo's own capacity
analysis discussed) benefits directly from new pods being schedulable and ready faster, and image size is one
of the few genuinely controllable levers for that.

### Q: If you needed to debug a running container that doesn't even have a shell available (a truly minimal
"distroless"-style image), how would that change your approach compared to this repo's current images?

**A:** This repo's current runtime images (`eclipse-temurin:17-jre-jammy`, a full Debian-based userland, not a
distroless/scratch image) still have a shell and basic utilities available, so `docker exec -it <container> sh`
works for interactive debugging — one of the practical reasons a project might choose a "minimal but not
distroless" base over a truly bare one, even though a distroless image would be smaller and have an even
narrower attack surface still. With a genuinely shell-less image, the same debugging need instead has to go
through Kubernetes' `kubectl debug` (which can attach a separate, full-featured debug container sharing the
target's process/network namespace) or rely entirely on the app's own logs/metrics/actuator endpoints — a real
tradeoff between "maximum hardening" and "can a human get a shell in a hurry during an incident," worth being
able to name explicitly rather than assuming smaller is strictly better with no downside.
