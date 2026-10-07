# Health Probes & Self-Healing

### Q: Liveness vs. readiness — restate the difference precisely, and give this repo's exact probe
configuration for one service.

**A:** **Liveness** answers "should this pod be killed and restarted" — a failing liveness probe gets the
container restarted in place. **Readiness** answers "should traffic be routed to this pod right now" — a
failing readiness probe just pulls the pod out of its Service's load-balancing rotation (no restart), leaving
the other replicas to absorb traffic until it recovers on its own.

`order-service`'s exact configuration (`k8s/base/09-order-service.yaml`): liveness hits
`/actuator/health/liveness` on the management port, with `initialDelaySeconds: 60`, checked every 15 seconds, 3
consecutive failures before a restart. Readiness hits `/actuator/health/readiness` on the same port, starting
sooner (`initialDelaySeconds: 45`) and checked more frequently (every 5 seconds) — deliberately more eager,
since "temporarily not ready" (still warming up, or a dependency briefly unreachable) is meant to be caught and
reflected quickly, while "should this process be killed" is a much more consequential decision that's allowed a
longer confirmation window before acting.

### Q: Why does readiness get a *shorter* initial delay than liveness, if readiness is checked *more*
frequently afterward too?

**A:** Because the two failure modes have very different costs if triggered prematurely. A readiness probe
firing too early, before the app has genuinely finished starting, just means the pod is correctly excluded from
traffic a little longer than strictly necessary — harmless, self-correcting the moment the check starts
passing. A liveness probe firing too early (checking before the app has had a realistic chance to finish
starting) would **restart a pod that was never actually broken**, which is actively disruptive — potentially
looping a pod through repeated unnecessary restarts if the startup time varies at all around the chosen delay.
Giving liveness a longer, more conservative initial delay (60s vs. 45s) reflects that asymmetry: a false
readiness failure costs a few seconds of a pod sitting out of rotation; a false liveness failure costs a
needless restart cycle.

### Q: What does `readOnlyRootFilesystem: true` actually break by default for a Java process, and how does
this repo's manifests work around it?

**A:** A JVM (and plenty of libraries it pulls in) routinely wants to write to `/tmp` — for things like
unpacking a native library, buffering, or temporary files a dependency creates without asking. With the
container's entire root filesystem mounted read-only (a genuine hardening win — nothing can persist a dropped
payload or modify the application's own files at runtime), `/tmp` being unwritable would break that behavior
outright, potentially causing cryptic startup or runtime failures that have nothing obviously to do with
filesystem permissions. `order-service`'s manifest (and the same pattern across every business service)
mounts a dedicated `emptyDir: {}` volume specifically at `/tmp`:
```yaml
volumeMounts:
- name: tmp
  mountPath: /tmp
volumes:
- name: tmp
  emptyDir: {}
```
`emptyDir` gives the pod one genuinely writable directory, backed by the node's own storage (or memory, if
configured that way) and scoped to that pod's lifetime — everything *else* in the container stays immutable,
closing off most of the "write a malicious file somewhere and have it execute" attack surface a read-write root
filesystem would otherwise leave open, while still giving the JVM the one writable directory it actually needs.

### Q: Put the full security-hardening picture together for one pod — what does `runAsNonRoot`,
`allowPrivilegeEscalation: false`, and `capabilities.drop: ["ALL"]` each individually stop, that the others
don't?

**A:** Three independent restrictions, each closing a different specific path, worth being able to name
separately rather than treating "security hardening" as one undifferentiated blob:

- `runAsNonRoot: true` + a fixed `runAsUser: 10001` stops the container process from running as root **at
  all**, regardless of what the image itself might have defaulted to — this is the same UID this session
  confirmed is set consistently in the Docker image's own `USER 10001:10001` instruction (see
  [../docker/01](../docker/01-dockerfiles-and-multi-stage-builds.md)), so the container-build-time hardening
  and the cluster-scheduling-time hardening agree with each other rather than one being able to silently
  override the other.
- `allowPrivilegeEscalation: false` stops a process from gaining **more** privilege than it started with during
  its own execution (blocking setuid-binary-style privilege escalation specifically) — a distinct concern from
  simply not starting as root, since a non-root process could still potentially escalate without this
  additional restriction.
- `capabilities.drop: ["ALL"]` strips every Linux capability a container gets by default (things like
  `NET_RAW`, `SYS_ADMIN` equivalents scoped to containers) — most container-escape and privilege-escalation
  techniques rely on *some* retained capability being available to exploit; starting from zero and only adding
  back anything genuinely needed (nothing is added back here) closes off that entire category rather than
  trusting that an unused capability happens not to matter.

None of the three is redundant with either of the others — a process could, in principle, fail to satisfy any
one of them while still passing the other two, which is exactly why all three are set together rather than
treating any single one as sufficient on its own.
