# Caching Abstraction

### Q: How does `@Cacheable` know what key to cache under, and how does this repo control it?

**A:** By default, Spring builds a key from the method's arguments; `key = "..."` on the annotation lets you
write a **SpEL** expression instead for full control. `ProductService` uses this explicitly, with a prefix
distinguishing lookup-by-ID from lookup-by-SKU in the *same* cache region:
`@Cacheable(value = CacheConfig.PRODUCTS_CACHE, key = "'id:' + #id")` and
`@Cacheable(value = CacheConfig.PRODUCTS_CACHE, key = "'sku:' + #sku")`. Without the explicit prefix, a product
with numeric ID `7` and a SKU that happened to also evaluate to `"7"` would collide on the same cache key — the
SpEL prefix is a small detail doing real work.

### Q: Why does one write path need to evict *two* different cache keys for the same logical entity?

**A:** Because it's reachable by two different keys that both need invalidating independently —
`ProductService.evict(id, sku)` evicts both `"id:" + id` and `"sku:" + sku` on every update/delete. A cache
that only evicted the ID-keyed entry would leave the SKU-keyed entry serving a stale value indefinitely (it's a
genuinely different cache *key*, so evicting one has zero effect on the other) — a correctness bug that would
only surface as "the price updated, but only when looked up by ID, not by SKU," which is exactly the kind of
inconsistency that's easy to miss if you only test one lookup path.

### Q: `@Cacheable` and `@CacheEvict` are proxy-based, just like `@Async` — what does that actually mean for
how this repo's caching code has to be structured?

**A:** Same underlying mechanism as [07](07-aop-proxies-and-cross-cutting-concerns.md): a call to a
`@Cacheable`/`@CacheEvict` method only goes through Spring's cache interception if it arrives through the
container-managed proxy — i.e. from a *different* bean, never from a private helper on the same class calling
itself. This repo's cached methods (`CustomerService.getCustomer`, `ProductService.getProductById`/
`getProductBySku`) are always invoked from controllers, a different layer entirely, so this limitation never
actually bites here — but it's the reason you can't, for instance, refactor a cached method to be called
internally from another method on the same service "for convenience" without silently losing the caching
behavior on that call path.

### Q: Walk through why `ProductService.evict()` can't just call the `@Async` evictor directly, and needs an
explicit `afterCommit` registration instead — what Spring mechanism is actually responsible for the ordering
guarantee here?

**A:** This is really a `Cache`-level behavior, not an annotation at all: `RedisConfig`'s `CacheManager` is
built with `manager.setTransactionAware(true)`, which wraps every cache Spring hands out in a
`TransactionAwareCacheDecorator`. That decorator checks `TransactionSynchronizationManager` for an active
transaction on the *calling thread* — if one exists, `cache.evict(...)` doesn't evict immediately; it silently
registers the real eviction to run only once that transaction actually commits (and drops it if the
transaction rolls back, since nothing really changed). This is what stops a concurrent reader from
repopulating the cache with a stale value in the gap between an eviction call and the write it's meant to
invalidate actually landing in the database.

The catch: that deferral is bound to `TransactionSynchronizationManager`'s **thread-local** state. Dispatch
straight to an `@Async` executor thread and you're now calling `cache.evict(...)` from a thread with *no*
transaction context of its own — the decorator sees nothing to defer against, and evicts immediately,
potentially before the real transaction has committed. `ProductService.evict()` works around this by
explicitly registering its own `TransactionSynchronization.afterCommit()` callback and only dispatching to the
`@Async` evictor from *inside* that callback — guaranteed to run after the real commit, on the original thread,
closing the gap the async dispatch would otherwise reopen. Full detail in
[microservices/08](../microservices/08-async-processing-and-thread-safety.md), since this is as much a
concurrency story as a caching one.

### Q: What stops a Redis outage from turning every cache read into a failed request?

**A:** Two independent layers, deliberately redundant: `LoggingCacheErrorHandler` (registered via
`CachingConfigurer.errorHandler()`) catches failures from annotation-driven `@Cacheable`/`@CacheEvict` calls and
logs rather than propagates; a custom `FailSafeCache` wrapper additionally decorates every cache instance at
the source (covering direct/manual cache usage and the after-commit eviction path that annotation-level error
handling alone wouldn't reach), treating any failure — connection refused, a value that can't deserialize — as
a cache miss, falling straight through to the database. The guarantee end to end: a Redis outage can only ever
degrade read latency, never correctness or availability of a write.
