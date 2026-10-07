# Data Consistency & Concurrency

### Q: A client's request times out, but the order might actually have been created. If they retry, how does
this repo avoid creating a duplicate order?

**A:** The `Idempotency-Key` header. A client generates one key per logical "attempt" and sends it with
`POST /orders`; the key plus the requesting principal plus a hash of the request body are checked against an
`idempotency_keys` table before any order is created (`OrderPlacementService.placeOrder()`). A replay with the
same key returns the *original* order's response, not a new order. This is deliberately **not** solved by a
blind HTTP retry around the write — a retry can't tell "the first attempt never reached the server" apart from
"the first attempt succeeded but the response was lost," and retrying the second case blindly creates a
duplicate. The idempotency key makes the write itself safe to repeat, so retrying is always correct regardless
of which case actually happened.

Race handling: if two requests with the same key arrive concurrently, both pass the initial "does this key
exist" check before either has committed — the one that loses the database's unique-constraint race gets a
`DataIntegrityViolationException`, which is caught and turned into "look up what the winner created and return
that," rather than surfaced as an error.

### Q: What's optimistic locking, and where does this repo use it?

**A:** A `@Version` column on an entity: every update checks the version it read hasn't changed, and bumps it
on success. If two updates race, the second one to commit fails with `OptimisticLockingFailureException`
instead of silently overwriting the first update's change (the "lost update" problem). This repo exposes it to
HTTP clients too, as an ETag: `GET /customers/{id}` returns the version as an `ETag`, and
`PUT`/`PATCH` requires it back as `If-Match` (`EntityTags.verifyIfMatch`) — a stale client gets a `412
Precondition Failed`, not a silent overwrite of someone else's concurrent change.

Optimistic locking is the right default for data where **conflicting writes are rare** and you'd rather detect
and reject a conflict than pay for a lock on every read — customer profiles, product details, order status
transitions.

### Q: Why is optimistic locking *not* good enough for inventory reservation, and what does this repo do
instead?

**A:** Optimistic locking tells you *after the fact* that two updates conflicted — fine when conflicts are
rare, but inventory reservation at scale (many orders racing to buy the last few units of a popular SKU) can
have conflicts be the *common* case, and "keep retrying until you win" degrades badly under contention, plus
still needs a check-then-act window closed somehow.

`ReservationService.reserve()` uses **pessimistic row locking** instead:
`SELECT ... FOR UPDATE`, always acquired in a fixed order — sorted by product ID — so two orders that happen to
share products can never lock them in opposite order and deadlock each other. Belt-and-braces beyond the lock:
the actual decrement is a single atomic conditional `UPDATE ... SET quantity = quantity - :n WHERE quantity >=
:n`, so the check-and-decrement can't be separated by another writer even without the lock, and a DB-level
`CHECK (quantity >= 0)` constraint is the last-resort backstop. Three independent layers, each closing a gap
the other might miss — this combination is *why* that code's own javadoc can say "reserve is all-or-nothing and
never oversells" as a guarantee, not a hope.

### Q: Pessimistic locking sounds like it would just become a bottleneck under heavy concurrent load — is
that a real concern here?

**A:** Yes, and it's worth naming the specific scenario rather than hand-waving: pessimistic locking serializes
writes *to the same row* — many orders for *different* products have no contention at all, but many orders for
the *same* hot/popular SKU (a flash-sale scenario) genuinely serialize on that one row, no matter how many
service replicas you run. Horizontal scaling doesn't help this specific case, because the bottleneck is the
database row, not application CPU. That's a real, nameable limitation of this design — the fix for a known
flash-sale scenario would be something architecturally different (e.g. a pre-decremented stock counter in a
fast store, batched settlement, or a queue in front of the hot SKU), not just more pods.

### Q: What's the general principle for choosing optimistic vs. pessimistic locking?

**A:** Optimistic when conflicts are the exception and you're willing to retry/reject on the rare collision
without paying a lock's cost on every read. Pessimistic when correctness absolutely cannot tolerate even a
narrow race window (you cannot "oversell" stock and un-sell it after the fact the way you can retry a stale
profile edit), or when conflicts under real load are frequent enough that optimistic retries would thrash. This
repo makes exactly that split: optimistic for customer/order metadata, pessimistic for the one place where a
race has an unrecoverable real-world consequence — promising stock that doesn't exist.
