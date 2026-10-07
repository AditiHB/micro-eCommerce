# Schema Evolution & Contract Testing

### Q: This repo has no Confluent Schema Registry — how does it stop a producer and a consumer from
disagreeing about an event's shape?

**A:** Golden-file contract tests, checked into the repo, instead of a runtime schema registry. Each event
type has a sample instance serialized and compared — after normalization — against a frozen
`contracts/<EventType>.v<version>.json` file (`ProductEventContractTest.java` and the equivalent per-service
contract tests, plus a shared `EventContractTest.java` in `common`). If a field is accidentally renamed,
removed, or its type changes, the test fails at **build time**, in whichever service made the change — not
later, silently, only when some independently-deployed consumer actually receives a message it can't
deserialize correctly in production.

The tradeoff worth naming honestly: a schema registry enforces compatibility centrally and can reject an
incompatible schema at publish time, across every producer, automatically. Golden-file contract tests only
catch a break if the *specific service making the change* remembers to run its own tests and a human reviews
the resulting diff to the golden file — there's no central authority stopping an incompatible change from being
merged if its own test suite is skipped or its golden file is regenerated carelessly alongside the change that
broke it.

### Q: Golden files can be regenerated with a flag (`-Dcontracts.update=true`) — doesn't that just let you
rewrite the test to match whatever you broke?

**A:** Yes, mechanically it does — which is exactly why the real protection here is procedural, not technical:
regenerating a golden file is a deliberate, visible, reviewable diff in version control, not something that
happens silently as a side effect of running the normal test suite (the flag is opt-in, not the default). A
reviewer looking at a pull request sees *"contracts/OrderCreatedEvent.v1.json changed"* as an explicit line in
the diff, with the actual before/after shape visible — which is a meaningfully different failure mode from "the
schema silently drifted and nobody noticed until a consumer broke." The test doesn't *prevent* a breaking
change from being shipped if a team genuinely decides to ship one; it makes sure that decision is visible and
deliberate rather than accidental.

### Q: How would you version an event type in this system if you needed to add a field in a
backward-incompatible way?

**A:** The golden-file naming convention itself (`<EventType>.v<version>.json`) implies the intended pattern:
a breaking change gets a new version suffix, with the new shape's own golden file, while existing consumers
keep working against whatever version they were built against — the standard event-versioning discipline of
"never silently mutate what `v1` means once it has consumers; add `v2` instead and let both exist until
everyone's migrated." This repo's actual event classes don't currently carry an explicit version field in their
own payload (the version lives in the contract-test file naming, not the wire format itself) — worth being
precise about that distinction if asked: the contract tests version the *test artifact*, which is a weaker
guarantee than an explicit version field a consumer could branch on at runtime.

### Q: Why is schema evolution a harder problem for an event-driven system than it is for a typical
synchronous REST API?

**A:** A REST API's producer and consumer are talking to each other in the same request — if they disagree
about the contract, you find out immediately, in that one call, and can fix it before anything else is
affected. An event, once published, can sit in a topic and be consumed by services that didn't even exist yet
when it was written, or by a consumer that's been down for maintenance and only catches up hours later — the
producer has no way to know, at publish time, which version of which consumer code will eventually read a given
message. That's the real argument for schema discipline (whether via a registry or contract tests) being more
load-bearing in event-driven systems than in synchronous ones: a schema mistake here can lie dormant for an
arbitrarily long time before it's actually exercised by a real consumer, which is exactly the kind of failure
that's expensive to discover late and cheap to catch with a build-time test instead.
