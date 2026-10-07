# Spring Data JPA

### Q: What are "derived query methods," and give a couple of real examples from this repo.

**A:** Spring Data parses a repository method's **name** and generates the query from it — no annotation, no
SQL, no JPQL written at all. `Page<Order> findByCustomerId(Long customerId, Pageable pageable)`
(`OrderRepository.java:17`) becomes `WHERE customer_id = ?` with pagination, purely from the method signature.
`ProductRepository` has several: `findBySku`, `findByCategory(String, Pageable)`, `findBySkuIn(Collection<String>)`,
`existsBySku(String)` — each one's SQL is entirely implied by its name, which is both the appeal (zero
boilerplate for simple lookups) and the limit (anything more complex than "a few fields combined with AND/OR"
stops being readable as a method name and needs `@Query` instead).

### Q: When do you drop to `@Query`, and does this repo use JPQL, native SQL, or both?

**A:** Both, chosen by what the query actually needs to do. JPQL `@Query` when the query is still
entity-oriented but too specific for a derived name — e.g.
`OrderRepository`'s stale-order finder: `SELECT o.id FROM Order o WHERE o.status IN :open AND o.updatedAt <
:cutoff ORDER BY o.updatedAt` (`OrderRepository.java:20-22`), or `ProductRepository`'s case-insensitive search
(`SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%'))`, line 20-21).

Native SQL (`nativeQuery = true`) when you need a database-specific feature JPQL doesn't expose at all — the
clearest example is `OutboxRepository`'s batch-locking query
(`common/.../outbox/OutboxRepository.java:25-38`): `FOR UPDATE SKIP LOCKED`, a row-locking hint with no JPQL
equivalent, is exactly what lets multiple `OutboxRelay` replicas poll the same table concurrently without
double-sending a row (see [microservices/05](../microservices/05-event-driven-architecture.md)). The same file
also shows `@Modifying @Query(...)` for a bulk delete that doesn't fit "load entities, mutate, save."

### Q: What does `@Modifying` do, and why is it required alongside `@Query` for an `UPDATE`/`DELETE`?

**A:** By default, Spring Data JPA assumes a `@Query` is a `SELECT` and will reject anything else at startup
unless you mark the method `@Modifying` — an explicit acknowledgment that this query mutates data rather than
reads it, which changes how Spring Data executes it (via `executeUpdate()`, not a result-set read) and
triggers Spring Data's own safety behavior around the persistence context (which is why this repo's
`@Modifying` queries are typically paired with `flushAutomatically = true, clearAutomatically = true` —
e.g. `InventoryRepository.decrementIfAvailable` — so the first-level cache doesn't go on serving a now-stale
entity after a bulk update bypassed it entirely).

### Q: Does this repo use Spring Data projections or `Specification`/QuerySpecification-style dynamic
queries anywhere?

**A:** No — worth knowing as an honest gap rather than claiming a pattern that isn't there. Every repository
method in this codebase returns full entities (never a narrower projection interface or a DTO built directly
in the query), and there's no `JpaSpecificationExecutor`/`Specification`/QueryDSL usage anywhere for building
queries dynamically at runtime. This connects directly to a real finding from this repo's own performance
review: `ProductRepository.findBySkuIn` loads the *complete* `Product` entity — including a `TEXT description`
column nobody downstream needs — on the single busiest request path in the system (every order's price lookup),
precisely because no projection exists to fetch only `sku`/`name`/`price`/`currency` instead. A good interview
answer here isn't just "projections exist as a Spring Data feature" — it's being able to point at the exact
place in *this* repo where using one would have mattered.

### Q: How is pagination handled consistently across list endpoints, and why wrap `Page<T>` in a custom
`PagedResponse` instead of returning Spring's `Page` directly from a controller?

**A:** Every list-returning repository method that could return a large result set takes a `Pageable`
and returns `Page<T>` — `OrderRepository.findByCustomerId`, `ProductRepository.findByCategory`/`searchByName`,
mirrored by `@RequestParam` page/size/sortBy parameters on the corresponding controller methods (clamped to a
`MAX_PAGE_SIZE`, so a client can't request an unbounded page size and force a large in-memory load). Spring's
own `Page<T>` serializes with a lot of Spring-internal structure (`pageable`, `sort` objects nested several
levels deep) that's an implementation detail leaking into a public API contract — wrapping it in this repo's
own `common/.../dto/PagedResponse.java` keeps the actual wire format stable and controlled by this codebase,
not by whatever shape Spring Data's `Page` happens to serialize to in a given Spring Boot version.

### Q: Beyond pessimistic row locking (already covered for inventory reservation), how does this repo use
`@Lock` generally, and what's the one-line mental model for picking a `LockModeType`?

**A:** `PESSIMISTIC_WRITE` is the only lock mode this repo uses (`InventoryRepository.lockByProductIds`), and
the mental model is: use it when a read is actually "I'm about to decide something based on this value and then
write back based on that decision, and I cannot tolerate someone else changing it in between" — a true
read-then-write critical section, not just an ordinary read. For everything else — most of this codebase — no
lock at all plus optimistic `@Version` checking is the default, exactly because most reads aren't gating a
decision that another writer could invalidate before the write lands (see
[microservices/07](../microservices/07-data-consistency-and-concurrency.md) for the full optimistic-vs-pessimistic
reasoning).
