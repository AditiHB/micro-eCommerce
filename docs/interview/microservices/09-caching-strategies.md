# Caching Strategies

### Q: What caching pattern does this repo use, and why Redis specifically rather than an in-process cache?

**A:** Cache-aside, via Spring's `@Cacheable`/`@CacheEvict` backed by Redis (`RedisConfig.java`), not an
in-process `ConcurrentHashMap`-style cache. The reason is specific to running multiple replicas: an in-process
cache is per-pod, so the moment you scale past one replica, a write on pod A doesn't invalidate pod B's copy —
pod B happily serves a stale value until its own TTL expires. This was confirmed as a real problem in this
project's own history (the doc for `RedisConfig` notes it was "confirmed live on Kubernetes" — a per-instance
cache serves stale reads the moment a second replica runs). A shared Redis cache means an eviction is visible
to every replica immediately, not just the one that happened to handle the write.

### Q: Why is the product catalogue cached (`ProductService.getProductById`/`getProductBySku`) but inventory
stock is deliberately *not* cached?

**A:** The deciding question for "should this be cached" is **how often is it read relative to how often it
changes, and how bad is a stale read**. Catalogue data (name, price, description) is read on essentially every
order but changes rarely — a near-ideal cache candidate. Stock quantity is the opposite: it changes on
practically every order (every reservation and release touches it), so caching it would be evicted almost as
often as it's read, buying you little, while a stale read of stock is a correctness problem, not a cosmetic
one — `ReservationService` must see the real, current, locked value, not whatever a cache last held. This is
explicitly documented as a deliberate tradeoff in `CacheConfig.java`'s own class comment, not an oversight.

### Q: Why does cache eviction need to happen *after* the write commits, not before or during?

**A:** If eviction happened before the write's transaction commits (or the transaction later rolled back), a
concurrent reader could repopulate the cache with the **old, soon-to-be-stale** value in the gap between the
eviction and the actual commit — leaving the cache wrong even though it was "just" evicted. This repo's Redis
`CacheManager` is configured `transactionAware(true)` specifically to close that gap: calling `cache.evict(...)`
inside an open transaction doesn't evict immediately, it registers the eviction to run only once the
transaction has actually committed (and drops it entirely if the transaction rolls back, since in that case
nothing actually changed).

### Q: What happens to the rest of a request if Redis is completely unreachable when a cached read or an
eviction is attempted?

**A:** It must **never** fail the request — a cache is an optimization, not a dependency a write should be
allowed to fail on. Two layers handle this: a `LoggingCacheErrorHandler` catches failures from Spring's own
annotation-driven `@Cacheable`/`@CacheEvict` calls and logs instead of propagating; and a custom `FailSafeCache`
wrapper (decorating every cache instance, including ones used directly rather than through an annotation, and
specifically the after-commit eviction path annotation-level error handling doesn't cover) treats *any* cache
failure — Redis unreachable, a value that can't be deserialized — as a miss, falling through to the database.
The end-to-end guarantee: a Redis outage degrades read latency (every read now hits the database), never
correctness or availability.

### Q: If you were adding caching for a new read-heavy endpoint in this repo, what would you check first
before reaching for `@Cacheable`?

**A:** Exactly the question answered above for inventory vs. catalogue: how often does this data change
relative to how often it's read, and what's the actual cost of a stale read for this specific data — cosmetic
(a product description a few minutes old) or correctness-critical (an account balance, a stock count, anything
feeding a decision that can't be undone). If writes are frequent or a stale read could be wrong in a way that
matters, the answer is often "don't cache this, and if it's slow, that's an indexing or query problem to solve
at the database instead" — caching workflow state that changes constantly tends to add complexity (invalidation
bugs, the transaction-timing issue above) without the throughput win that makes caching worth it for genuinely
reference-ish data.
