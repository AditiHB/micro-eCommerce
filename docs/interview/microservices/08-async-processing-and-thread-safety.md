# Async Processing & Thread Safety

This repo has two distinct concurrency patterns, deliberately chosen per use case rather than one applied
everywhere. The difference between them is one of the more interesting things to be able to explain clearly in
an interview.

### Q: When do you reach for `CompletableFuture` fan-out-then-join, versus `@Async`?

**A:** They answer different questions. `CompletableFuture.supplyAsync(...)` + `.join()` is for when the
caller needs to **wait for and combine results** from multiple independent pieces of work before deciding what
to do next. `@Async` is for genuine **fire-and-forget** — work the caller kicks off but never waits for or
needs a result from.

Concretely in this repo:

- `OrderPlacementService` fans out the customer-exists check and the catalogue price lookup concurrently, then
  joins both before deciding whether the order is valid — a fan-out-then-join case.
- `OrderSagaDeadline`'s reaper fans out a batch of independent order expirations and waits for the whole batch
  (`CompletableFuture.allOf(...).join()`) before the scheduled pass returns — also fan-out-then-join.
- `NotificationDispatcher` fans out a batch of notification sends concurrently and joins all the outcomes
  before mutating any entity — also fan-out-then-join, for a subtler reason (see below).
- `CustomerMetricsRecorder.recordCreated(sample)` and `ProductCacheEvictor.evictAsync(id, sku)` are true
  fire-and-forget: the HTTP response never depends on a metrics counter being incremented or a cache entry
  being evicted, so `@Async` fits directly — no result is ever joined.

`@Async` *could* be made to return a `CompletableFuture` and have the caller join it, but at that point you've
just reimplemented the explicit pattern with extra indirection (proxy semantics, self-invocation gotchas) for
no benefit — if you're going to join a result, write the explicit `CompletableFuture` fan-out directly.

### Q: Why does `@Async` silently not work if you call the method from within the same class?

**A:** `@Async` is implemented as a Spring AOP **proxy** — when another bean calls `someBean.asyncMethod()`,
it's actually calling the proxy, which intercepts the call and dispatches it to the executor. A call from
*within* the same class (`this.asyncMethod()`, or just a bare method call inside the class) never goes through
the proxy at all — it's a plain Java method call, so it runs synchronously, silently, with no error or
warning. This is why `CustomerMetricsRecorder` and `ProductCacheEvictor` are their own separate `@Component`
beans rather than private methods on `CustomerService`/`ProductService` — the async method has to be invoked
*through another bean* for Spring's proxy to actually intercept it.

### Q: What's the `SecurityContext` propagation gotcha, and how is it handled?

**A:** `SecurityContextHolder` is **thread-local** by default — it holds whatever `Authentication` is attached
to the *calling* thread. When `OrderPlacementService` dispatches the catalogue lookup and customer-exists check
onto a different executor thread, that thread has no `SecurityContext` of its own — and both downstream clients
relay the caller's bearer token via `BearerTokenRelayInterceptor`, which reads `SecurityContextHolder`. Without
intervention, the relayed `Authorization` header would silently disappear on the async call — not an exception,
just a request made with no token.

The fix: capture `SecurityContextHolder.getContext()` on the calling thread *before* dispatching, then
explicitly `setContext(...)` it on the executor thread before running the call, and restore whatever was there
before in a `finally` block (so a reused pool thread doesn't leak one request's identity into the next task it
picks up).

### Q: Why does `NotificationDispatcher` need to join every send's result before touching any entity, instead
of just mutating each `Notification` as soon as its own send finishes?

**A:** Because a Hibernate **session is not thread-safe**, and the whole batch of due notifications is loaded
and mutated inside *one* transaction, bound to *one* Hibernate session, on the thread that owns that
transaction. If you let each async send task mutate its own `Notification` entity directly from the executor
thread the moment its send finishes, you'd have multiple threads touching the same Hibernate session
concurrently — undefined, corrupting behavior, not just a race on a single field.

The fix: the async tasks only ever handle plain values — the recipient/subject/message strings extracted
*before* dispatch, and a `SendOutcome` record (success or failure, never an exception) returned *after*. None of
them ever touches the `Notification` entity. Only once `CompletableFuture.allOf(...).join()` confirms every
send has finished does the original thread — the one that actually owns the transaction and the session — loop
back over the results and apply every mutation (`setStatus`, `setAttempts`, `setErrorMessage`,
`setNextAttemptAt`) itself, sequentially, safely.

### Q: Why does every executor added in this repo get its own small, dedicated thread pool instead of sharing
one pool (or Spring Boot's default `applicationTaskExecutor`)?

**A:** Two reasons. First, isolation: a burst of work on one fan-out point (say, the saga reaper processing a
large batch of expirations) should never be able to starve a completely unrelated one (order placement's
customer/catalog lookups) by exhausting a shared pool's threads. Second, Spring Boot's auto-configured default
executor is sized for generic background work, not tuned for any specific workload's actual latency/volume
profile — a pool meant for a couple of blocking HTTP calls per request (`remoteCallExecutor`: core 8, max 16,
queue 50) has a completely different right-sizing than one meant for trivial fire-and-forget cache evictions
(`cacheEvictionExecutor`: core 2, max 4) or one for a 30-second background batch job
(`sagaReaperExecutor`: core 4, max 8, queue 100). Naming and sizing each pool for its actual job, and keeping
them apart, is cheap and avoids a whole class of "why did an unrelated feature slow down" surprises.

### Q: What happens when one of these pools is actually saturated — does the system just fall over?

**A:** No — this is handled explicitly everywhere a pool was added, and it's one of the more deliberate pieces
of this design: `CompletableFuture.supplyAsync(supplier, executor)` calls `executor.execute(...)` **synchronously,
at the point of submission** — if the pool/queue is full, `ThreadPoolTaskExecutor`'s default rejection policy
throws `RejectedExecutionException` right there, *before* the future even exists, not as a failure inside it.
Every executor-submission call site in this repo wraps that specific case:

- `OrderPlacementService.submitRemote()` catches it and converts it into the same `DependencyUnavailableException`
  (503) every other remote-call failure produces — a saturated pool degrades exactly like a down dependency,
  not as an unhandled 500.
- `OrderSagaDeadline` catches it per-order and simply skips that order for the current pass — harmless, since
  it's still stale and gets picked up again next reaper pass 30 seconds later.
- `NotificationDispatcher` catches it and folds it into the *existing* retry/backoff machinery, as a
  `SendOutcome.failure(...)` — a saturated send pool is treated exactly like a slow mail server: recorded,
  backed off, retried automatically.

The common thread across all three: a pool being full is a known, expected failure mode, handled the same
deliberate way as every other failure mode in that code path — never a surprise, never a raw exception leaking
to a caller who has no idea what a `RejectedExecutionException` means.

### Q: Describe the subtlest bug this repo's `@Async` additions had to avoid, involving cache eviction and
transactions.

**A:** `inventory-service`'s Redis cache is already **transaction-aware**
(`RedisConfig`: `manager.setTransactionAware(true)`) — calling `cache.evict(...)` *during* an open transaction
doesn't evict immediately; Spring wraps the cache in a `TransactionAwareCacheDecorator` that automatically
defers the real eviction until the surrounding transaction actually commits (and silently drops it if the
transaction rolls back). This is what stops a reader from re-populating the cache with a stale value in the gap
between an eviction and the write it's meant to invalidate actually landing in the database.

That deferral mechanism is bound to `TransactionSynchronizationManager`, which is **thread-local** — scoped to
the thread that holds the transaction. If `ProductService.evict()` dispatched straight to an `@Async` method,
the eviction would run on a fresh executor thread that has *no* transaction context of its own — so the
cache's "defer until commit" logic would see no active synchronization and evict **immediately**, potentially
*before* the real transaction has actually committed, silently reopening exactly the race the transaction-aware
cache exists to prevent.

The fix: `ProductService.evict()` explicitly registers a `TransactionSynchronization` with an `afterCommit()`
callback, and only *that* callback — guaranteed to run after the real commit, still on the original thread —
hands off to the `@Async` evictor. The async dispatch happens strictly after the ordering guarantee is already
satisfied, rather than racing it.
