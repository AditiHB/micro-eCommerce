# Inter-Service Communication

### Q: When does this repo use synchronous HTTP vs. asynchronous events between services?

**A:** Synchronous REST, over a `RestClient`, for anything the caller needs an immediate answer to in order to
decide what happens next: order placement calls `inventory-service` (does this SKU exist, what does it cost)
and `customer-service` (does this customer exist) synchronously, because the order literally cannot be priced
or validated without those answers (`OrderPlacementService.java`).

Everything that is a *consequence* of an action, rather than a precondition for it, goes over Kafka instead:
once an order is placed, inventory reservation, payment processing, and notification are all driven by
consuming the resulting event — none of them block the client's response (see
[05](05-event-driven-architecture.md) and [06](06-saga-and-distributed-transactions.md)).

### Q: How are outbound REST clients built — one per call, or reused? Why does that matter?

**A:** Built once, as a `final` field, in the client's constructor (`CatalogClient.java`,
`CustomerDirectoryClient.java`) — never recreated per call. This matters because a `RestClient` wraps a real
HTTP client with its own connection pool; rebuilding it per request would throw away connection reuse (forcing
a fresh TCP/TLS handshake on every call) and needlessly churn objects on the hottest paths in the system. The
same discipline applies to `ObjectMapper` — one `@Bean` (`JacksonConfig.java`), injected everywhere, never
`new ObjectMapper()`'d ad hoc.

### Q: What's the "bearer token relay" pattern, and why is it needed here?

**A:** When `order-service` calls `customer-service` to check a customer exists, it should apply the *same*
authorization the original caller would get directly — a customer should only ever be able to confirm their
own record, even through this indirect call. `BearerTokenRelayInterceptor` (`common/.../client/`) does this: it
reads the current `SecurityContextHolder`'s JWT and forwards it as the outbound call's `Authorization` header,
so the downstream service enforces the original caller's actual permissions rather than order-service holding
some broader "service account" credential that could bypass per-customer authorization.

The alternative design — giving order-service a privileged service account that can read any customer — is
simpler but weaker: it means a bug in order-service can read data belonging to *any* customer, not just the one
in the current request. Relaying the caller's own token keeps the blast radius of a bug in order-service
bounded to what that specific caller was already allowed to see.

### Q: This repo has both mTLS and JWT — isn't that redundant? Aren't you authenticating twice?

**A:** They protect against different things, which is the point of defense in depth:

- **mTLS** (optional `mtls` profile) authenticates the *services themselves* to each other at the transport
  layer — it proves "this connection really is coming from the real order-service, not something that spoofed
  its network identity," and encrypts the traffic.
- **JWT** authenticates and authorizes the *end user* (or a specific caller's permissions) at the application
  layer — it proves "this specific request is allowed to do this specific thing," independent of which service
  happened to make the call.

A compromised/misconfigured network inside the cluster is exactly the scenario mTLS defends against that JWT
alone doesn't (JWT riding over an unauthenticated connection still lets anything on the network impersonate a
service's *IP*, even if it can't forge a valid token). Conversely, mTLS alone doesn't stop a legitimately
authenticated service from doing something the end user wasn't actually allowed to do — that's what the
relayed JWT and per-request authorization checks are for.

### Q: Why is `notification-service` the one exception to the mTLS mesh?

**A:** Every other business service is reached *only* from inside the mesh (through `api-gateway` or other
services), so requiring a client certificate on their inbound side is free — nothing legitimate reaches them
any other way. `notification-service` is deliberately reachable directly from outside the mesh (e.g. for
manual testing/ops tooling), so forcing mTLS on its inbound side would break that. It still gets a certificate
and uses it as its own *client* identity for its one outbound call (to `customer-service`) — it just doesn't
require one coming in.

### Q: Give a concrete example of concurrent, independent calls to two different services in the same
request — how is that structured?

**A:** `OrderPlacementService.placeOrder()` needs both "does this customer exist" (`CustomerDirectoryClient`)
and "what do these SKUs cost" (`CatalogClient`) before it can proceed — two independent reads, with nothing
in one's result needed by the other. Rather than calling them one after another, both are dispatched via
`CompletableFuture.supplyAsync(..., remoteCallExecutor)` on a small dedicated thread pool and then `.join()`ed,
so the two round trips happen in parallel instead of back-to-back. See
[08](08-async-processing-and-thread-safety.md) for the full pattern, including the `SecurityContext`
propagation gotcha this introduces (both calls relay the caller's JWT, and `SecurityContextHolder` is
thread-local — it has to be captured and re-installed explicitly on each executor thread, or the relayed token
silently disappears).
