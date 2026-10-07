# AOP, Proxies & Cross-Cutting Concerns

### Q: `@Async`, `@Transactional`, and `@Cacheable` all "just work" by annotation alone — what's actually
happening underneath, mechanically?

**A:** All three are implemented the same way: **Spring AOP proxies**, not bytecode-weaving AspectJ, and not
magic the JVM understands natively. When a bean has any of these annotations on a method, Spring wraps the real
object in a proxy at startup. Every call to that bean **from another bean** actually goes through the proxy
first, which runs whatever behavior the annotation implies (start a transaction, dispatch to an executor, check
a cache) *before* delegating to the real method. This repo has **zero** hand-written `@Aspect` classes anywhere
(confirmed — no `@Aspect`/`@EnableAspectJAutoProxy` in the codebase) — every cross-cutting concern here is this
same proxy mechanism, just via different built-in annotations, never a custom aspect.

### Q: JDK dynamic proxies vs. CGLIB — which does this repo end up with, and does it matter which?

**A:** Spring Boot's auto-configuration defaults to **CGLIB-style proxying** (subclassing the actual concrete
class) for most of its own starters, and nothing in this repo overrides that —
`spring.aop.proxy-target-class` isn't set anywhere, so the Boot default stands. Practically, this rarely
matters day to day (CGLIB can proxy a concrete class without needing an interface, which JDK dynamic proxies
cannot), but it's worth knowing the one real constraint CGLIB proxies share: they work by generating a runtime
subclass, so a class marked `final`, or a method marked `final`/`private`, **cannot be proxied** — an
`@Async`/`@Transactional`/`@Cacheable` annotation on a `final` method would be silently ignored for the same
underlying reason self-invocation is (see below): the proxy has no way to intercept it.

### Q: Explain the self-invocation limitation once, in general terms, and then point to where this repo
either avoids it or had to specifically design around it.

**A:** The proxy only ever sees calls that arrive **from outside the bean**, through Spring's container.
A method on a bean calling another method on *itself* (`this.otherMethod()`, or just an unqualified call from
within the same class) is a plain, direct Java method call — it never passes through the container, so the
proxy is never invoked, and whatever annotation is on that other method (`@Async`, a different
`@Transactional` propagation, `@Cacheable`) is silently skipped. No exception, no warning — just quietly wrong
behavior that only shows up as "why didn't this run on a background thread" or "why wasn't this cached" during
actual testing.

Three concrete places this repo had to design around it, each discussed in depth in its own topic file:

- `CustomerMetricsRecorder` and `ProductCacheEvictor` are their own separate `@Component` beans, specifically
  *because* `@Async` called from a private method on `CustomerService`/`ProductService` would silently run
  synchronously (see [microservices/08](../microservices/08-async-processing-and-thread-safety.md)).
- `ReservationService`'s `Propagation.MANDATORY` only has teeth because it's invoked from a genuinely different
  bean (the saga handler) — if something internally called it via self-invocation, the propagation check
  itself would never run at all (see [05](05-transactions.md)).
- `@Cacheable`/`@CacheEvict` share the identical limitation (`@EnableCaching` is the same proxy-based
  `AdviceMode`, not AspectJ) — calling an evict-annotated method from within the same service class as a
  private helper would silently skip the eviction, exactly as it would for `@Async`. This repo's caching
  methods (`getCustomer`, `getProductById`, etc.) are always called from a different layer (a controller),
  never from inside the same class, so it never actually hits this — but it's the same mechanism as the other
  two, worth recognizing as one root cause rather than three separate coincidences.

### Q: If self-invocation silently breaks these annotations, how do you deliberately call a transactional/
cached/async method from within the same class when you genuinely need to?

**A:** Two real options, and this repo picks the first every time it comes up: (1) **move the method onto a
different bean** and inject that bean — the approach taken for every `@Async` case here, since it's simple,
explicit, and testable (a separate bean is trivially mockable in a unit test, whereas self-injection is not);
or (2) inject a self-reference to the bean's own Spring-managed proxy (via `@Lazy` self-injection, or
`AopContext.currentProxy()` with `exposeProxy = true`) and call through *that* instead of `this` — more
compact, but muddier to read and easy to forget why it's there. This repo consistently chooses the "separate
bean" option, which is also generally considered the cleaner default unless genuinely good reason exists to
avoid growing the bean graph.
