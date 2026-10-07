# KRaft & Cluster Topology

### Q: Does this repo run Kafka with ZooKeeper, or without it — and what's the difference for someone who's
only used the older setup?

**A:** **KRaft mode — no ZooKeeper at all.** `docker-compose.yml`'s comment is explicit: *"Single-node KRaft
Kafka (no ZooKeeper)."* Historically, Kafka relied on a separate ZooKeeper ensemble to store cluster metadata
(which broker is the controller, topic/partition configuration, ACLs) and elect a controller broker. KRaft
(Kafka Raft) removes that separate system entirely — brokers themselves participate in a Raft consensus
protocol to manage that same metadata, configured via `KAFKA_PROCESS_ROLES: broker,controller` (both roles on
the same node here). Practically: one fewer distributed system to deploy, operate, and keep consistent with the
Kafka cluster itself — a real operational simplification that's one of KRaft's main selling points over the
ZooKeeper-based architecture it replaces.

### Q: What's the actual difference between the default Compose setup and the `docker-compose.kafka-ha.yml`
overlay?

**A:** The default `docker-compose.yml` runs a **single KRaft node** acting as both broker and controller —
simple, fast to start, zero replication, fine for local development and the test suite, but a single point of
failure with no tolerance for that one node going down. `docker-compose.kafka-ha.yml` is an **opt-in overlay**
(layered on top via `docker compose -f docker-compose.yml -f docker-compose.kafka-ha.yml up`, not the default)
standing up **three** KRaft nodes, each again playing both broker and controller roles, with every topic's
replication factor raised to 3 and `min.insync.replicas` raised to 2. Same `CLUSTER_ID` constant across both
files — this is genuinely the same logical cluster configuration scaled up, not a divergent setup.

### Q: What does raising `replicationFactor` to 3 and `min.insync.replicas` to 2 actually buy you, and why
isn't it the default?

**A:** With replication factor 3, each partition's data exists on three separate brokers, so losing any one
broker doesn't lose any data — a replica survives. `min.insync.replicas = 2` (paired with the producer's
`acks=all`, discussed in [02](02-producers-and-delivery-guarantees.md)) means a write is only acknowledged once
**two** of those three replicas have it, not just the leader — so even an immediate leader failure right after
an acknowledged write can't lose it, since a second replica already has it too. The code comment pinned to this
exact setting spells out the tradeoff precisely: *"With replication factor 3 set this to 2: a write is
acknowledged only once two replicas have it."*

It isn't the default specifically because it isn't needed for local development or CI — three containers
instead of one is more resources and more startup time for a guarantee that only matters once you're running
something you actually can't afford to lose, which local dev and the test suite generally aren't. This is the
same "pay for exactly the guarantee you need, where you need it" instinct behind several other opt-in overlays
in this repo (mTLS, Vault) — see
[../microservices/12](../microservices/12-deployment-and-scaling.md) for the deployment-topology version of
this same theme.

### Q: If a message is only durable once two of three replicas have it, what happens to in-flight,
unreplicated writes if the leader broker crashes at the worst possible moment?

**A:** This is exactly what `min.insync.replicas` is protecting against, and it's worth walking through the
failure mode precisely rather than just naming the setting: if a producer's write has only reached the leader
(not yet replicated to any follower) and the leader crashes before any follower catches up, that write is lost
from the leader's perspective — **but** with `acks=all` and `min.insync.replicas=2`, the producer would never
have received an acknowledgment for that write in the first place (acknowledgment only happens once 2 replicas
confirm it), so from the producer's point of view it's an unacknowledged, retriable failure, not a silently
lost "successful" write. The producer's own retry logic (bounded by `delivery.timeout.ms`, discussed in
[02](02-producers-and-delivery-guarantees.md)) would reattempt the send once a new leader is elected — the
guarantee isn't "nothing is ever lost no matter what," it's the narrower, achievable "nothing *acknowledged* is
ever lost," which is the realistic guarantee any distributed system can actually make.
