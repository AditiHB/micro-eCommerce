# Scaling & Resource Management

### Q: How does the Horizontal Pod Autoscaler actually decide when to add or remove pods here?

**A:** One `HorizontalPodAutoscaler` (`autoscaling/v2`) per business service (`k8s/base/13-hpa.yaml`):
`minReplicas: 2`, `maxReplicas: 5`, targeting **70% average CPU utilization** across the Deployment's pods. The
HPA controller continuously compares current average CPU (sourced from the metrics pipeline, not something it
measures itself — it needs `metrics-server` installed in the cluster to function at all) against that target
and adjusts replica count to try to converge on it. `discovery-server` and `config-server` are deliberately
**excluded** from autoscaling — intended as singletons in this topology, not because scaling them would be
unsafe exactly, but because nothing about this system's load profile calls for more than one of either.

### Q: What's `scaleDown.stabilizationWindowSeconds: 60` protecting against, specifically?

**A:** Without it, a brief dip in CPU right after a scale-up (common — new pods take a moment to actually
start receiving their share of traffic) could trigger an immediate scale-*down* decision, which then gets
reversed again moments later when load picks back up — a thrashing cycle of constantly adding and removing
pods that's disruptive (every removed pod drops whatever requests were in flight to it, see
[03](03-health-probes-and-self-healing.md)) and provides no real benefit over just holding steady. The
stabilization window makes the HPA look at the **highest** recommended replica count over the trailing 60
seconds before actually scaling down, so a momentary dip doesn't immediately shed capacity that might genuinely
be needed again within that same window.

### Q: `maxReplicas: 5` — is that a real system limit, or just a number that happens to be in a config file?

**A:** A real, hard ceiling, worth being able to state plainly rather than assuming the system "scales
automatically" without qualification: if real traffic needs more than 5 replicas of a given service, nothing in
this system raises that ceiling on its own — `maxReplicas` has to be edited and reapplied first. This is
exactly the kind of number that matters concretely in a capacity conversation (see this repo's own festival-load
discussion): the HPA provides *elasticity within a configured range*, not unbounded automatic scaling, and the
range itself is a deliberate operational decision someone has to revisit before a known traffic event, not
something the system figures out by itself.

### Q: Walk through what `requests`/`limits` actually do — are they the same kind of number serving two
purposes, or genuinely different mechanisms?

**A:** Genuinely different, despite looking like a matched pair. `requests` (`cpu: 250m, memory: 256Mi` per
business-service pod here) is what the **scheduler** uses to decide which node has room for this pod — a node
needs at least this much *unreserved* capacity for the pod to be placed there at all; it's a reservation
promise, not a cap. `limits` (`cpu: 500m, memory: 512Mi`) is what the **kernel/cgroup** enforces at runtime — a
container literally cannot use more CPU than its limit (it gets throttled) and, critically, memory is treated
differently: exceeding the **memory** limit gets the container OOM-killed outright, there's no "throttling"
equivalent for memory the way there is for CPU. The gap between `requests` and `limits` here (256Mi requested,
512Mi allowed) is intentional headroom — the scheduler only has to find 256Mi free to place the pod, but the
pod is allowed to burst up to 512Mi under real load without being killed for it, as long as the node actually
has that spare capacity available when it needs it.

### Q: If every business-service pod requests 250m CPU / 256Mi memory, and the HPA can run up to 5 replicas,
what's the real resource ceiling for one service, and why does that number matter beyond "it's a config
detail"?

**A:** 1.25 CPU cores / 1.25Gi memory requested at maxReplicas (5 × 250m / 5 × 256Mi), 2.5 cores / 2.5Gi at the
hard `limits` ceiling. This is exactly the kind of number that should be checked against real load-test results
before trusting it for anything like a festival-scale event, rather than assumed adequate just because it's
"the configured default" — a number nobody has validated against actual measured throughput per pod is a
number nobody actually knows is right, it's just a starting point. This repo's own capacity discussion (see the
microservices guide's deployment chapter) makes the same point about the database tier being undersized for a
real traffic spike — the same "verify, don't assume" instinct applies here at the pod-resource level too, and
for the same underlying reason: a config default that was reasonable for development and steady low-to-moderate
traffic is not automatically a number that's been validated for a planned surge.
