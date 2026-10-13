"""Deck: topic 03, Spring, JPA and Java concurrency, 8 cards. Grounded in ReservationService / InventoryRepository, EntityTags, the @Async beans, the
Propagation.MANDATORY services, the executors in AsyncConfig, OrderPlacementService and lombok.config (docs/interview/spring-boot/ and microservices/07, 08).
exec()d inside gen_flash.py: every engine helper (Dia, canvas, flow, cells, box_text, note, ...) is in scope."""

DECK_TITLE = "Spring, JPA and concurrency"
DECK_LABEL = "Spring, JPA and concurrency"
CHECK_QS = [
    "What is the <u>actual</u> mechanism underneath: proxy, lock, queue, thread?",
    "Which line of code or which annotation carries the guarantee?",
    "How does it <u>fail silently</u>, and how would I notice?",
    "What would I change under 100x load, and what does that cost?",
]

RED_T, BLUE_T, YEL = "#fde3df", "#e3edfa", "#fff3a8"


# ------------------------------------------------------------------ scenario pictures (front)
def ex_1():
    d = canvas(62)
    flow(d, ["order A: 1", "stock = 1", "order B: 1"], 8, size=10.5, gap=28, fills={1: YEL}, under={0: ("wants the last unit", PENCIL), 2: ("same instant", RED)})
    d.h = 54
    return d


def ex_2():
    d = canvas(62)
    flow(d, ["admin A", "product v3", "admin B"], 8, size=10.5, gap=28, fills={1: YEL}, under={0: ("reads v3", PENCIL), 2: ("reads v3", PENCIL)})
    d.h = 54
    return d


def ex_3():
    d = canvas(62)
    flow(d, ["ProductService", "evictAsync()"], 8, size=10.5, gap=40, fills={0: BLUE_T, 1: YEL}, under={0: ("same class", PENCIL), 1: ("@Async, but runs inline", RED)})
    d.h = 54
    return d


def ex_4():
    d = canvas(62)
    flow(d, ["publish(event)", "no transaction", "outbox row saved"], 8, size=10, gap=18, fills={1: RED_T, 2: YEL}, colors={1: RED}, under={2: ("alone, committed", RED)})
    d.h = 54
    return d


def ex_5():
    d = canvas(62)
    flow(d, ["core 2", "queue 100", "max 4"], 8, size=10.5, gap=26, fills={0: GREENISH, 1: YEL, 2: BLUE_T}, under={1: ("50 slow tasks submitted", RED)})
    d.h = 54
    return d


def ex_6():
    d = canvas(62)
    flow(d, ["lookup 1", "lookup 2", "join()"], 8, size=10.5, gap=26, fills={1: RED_T, 2: YEL}, colors={1: RED}, under={1: ("throws", RED), 2: ("which exception?", PENCIL)})
    d.h = 54
    return d


def ex_7():
    d = canvas(62)
    flow(d, ["UPDATE qty - 1", "find(id)", "qty = ?"], 8, size=10.5, gap=26, fills={0: YEL, 2: RED_T}, colors={2: RED}, under={2: ("still the old value", RED)})
    d.h = 54
    return d


def ex_8():
    d = canvas(62)
    flow(d, ["Executor?", "remoteCall", "sagaReaper"], 8, size=10.5, gap=26, fills={0: RED_T, 1: BLUE_T, 2: BLUE_T}, colors={0: RED}, under={0: ("2 candidates: startup fails", RED)})
    d.h = 54
    return d


# ------------------------------------------------------------------ explanation pictures (back)
def dia_1():
    d = canvas(124)
    rows = [("1  LOCK  rows in product order", "PESSIMISTIC_WRITE", BLUE_T), ("2  UPDATE ... WHERE qty >= :n", "1 row, or 0", YEL), ("3  CHECK (quantity >= 0)", "the database has the last word", GREENISH)]
    for i, (a, b, fill) in enumerate(rows):
        y = 6 + i * 34
        w = box_text(d, 8, y, a, 10, 24, INK, fill)
        d.add(svg_text(8 + w + 8, y + 16, b, 9.5, PENCIL))
    d.add(svg_text(8, 114, "A: p1,p2   B: p2,p1   both lock p1 first: no deadlock", 10, GREEN))
    d.h = 120
    return d


def dia_2():
    d = canvas(122)
    flow(d, ["A reads v3", "B reads v3", "A saves"], 8, size=10, gap=16, fills={2: GREENISH})
    d.add(svg_text(8, 50, "A: UPDATE ... SET version = 4 WHERE version = 3  (1 row)", 9.5, GREEN))
    flow(d, ["B: If-Match 3", "current is 4", "412"], 62, size=10, gap=18, fills={1: YEL, 2: RED_T}, colors={2: RED})
    d.add(svg_text(8, 106, "412 before the write; 409 when it is caught at flush", 10, INK))
    d.h = 114
    return d


def dia_3():
    d = canvas(112)
    flow(d, ["caller", "PROXY", "ProductService"], 8, size=10.5, gap=26, fills={1: YEL, 2: BLUE_T})
    d.add(svg_text(8, 52, "other bean -> proxy -> advice (@Async) -> target", 10, GREEN))
    d.add(rect(190, 66, 130, 26, RED, 1.5, 1.2, .5, fill=RED_T, extra='stroke-dasharray="5 4"'))
    d.add(svg_text(255, 83, "this.evictAsync()", 10, RED, "middle"))
    d.add(arrow(250, 66, 250, 60, 0, RED, 1.4, 4))
    d.add(svg_text(8, 82, "same class: straight to the target,", 10, RED))
    d.add(svg_text(8, 96, "the proxy (and its advice) is skipped", 10, RED))
    d.h = 104
    return d


def dia_4():
    d = canvas(122)
    rows = [("REQUIRED", "join, or start one", BLUE_T), ("MANDATORY", "join, or THROW", YEL), ("REQUIRES_NEW", "suspend + own tx", RED_T)]
    for i, (a, b, fill) in enumerate(rows):
        y = 6 + i * 30
        w = box_text(d, 8, y, a, 10.5, 22, INK, fill, minw=108)
        d.add(arrow(8 + w + 4, y + 11, 8 + w + 22, y + 11, 0, GREY, 1.4, 4))
        d.add(svg_text(8 + w + 28, y + 15, b, 10, INK))
    d.add(svg_text(8, 108, "handler owns the tx; publish / claim / reserve only join", 10, GREEN))
    d.h = 116
    return d


def dia_5():
    d = canvas(112)
    segs = [("core 2", 62, GREENISH), ("queue 100", 118, YEL), ("+2 threads", 66, BLUE_T), ("reject", 54, RED_T)]
    x = 8
    for i, (lab, w, fill) in enumerate(segs):
        d.add(rect(x, 14, w, 24, INK, 1.5, 1.2, .5, fill=fill))
        d.add(svg_text(x + w / 2, 30, lab, 10, INK, "middle"))
        d.add(svg_text(x + w / 2, 10, str(i + 1), 9, RED, "middle"))
        x += w + 4
    d.add(svg_text(8, 58, "1 core threads  2 queue  3 up to max  4 reject", 10, INK))
    d.add(svg_text(8, 74, "50 tasks: 2 running, 48 queued, still 2 threads", 10, GREEN))
    d.add(svg_text(8, 90, "threads 3 and 4 appear only when 100 are waiting", 10, RED))
    d.h = 98
    return d


def dia_6():
    d = canvas(112)
    flow(d, ["supplyAsync A", "supplyAsync B"], 8, size=10, gap=24, fills={0: BLUE_T, 1: BLUE_T})
    d.add(svg_text(8, 52, "both started first, then:", 10, PENCIL))
    flow(d, ["join(A)", "join(B) throws", "CompletionException"], 58, size=9.5, gap=12, fills={1: RED_T, 2: YEL}, colors={1: RED})
    d.add(svg_text(8, 100, "getCause() = the original exception: rethrow that", 10, GREEN))
    d.h = 106
    return d


def dia_7():
    d = canvas(122)
    d.add(rect(8, 6, 190, 44, TEAL, 1.5, 1.2, .5, extra='stroke-dasharray="5 4"'))
    d.add(svg_text(14, 18, "persistence context", 9.5, TEAL))
    box_text(d, 16, 24, "Inventory qty=5", 10, 20, INK, BLUE_T)
    box_text(d, 250, 20, "DB qty=4", 10.5, 22, INK, GREENISH)
    d.add(arrow(202, 31, 246, 31, 0, RED, 1.6, 4))
    d.add(svg_text(224, 24, "UPDATE", 9, RED, "middle"))
    d.add(svg_text(8, 70, "find() -> cached object: qty = 5  (stale, not dirty)", 10, RED))
    d.add(svg_text(8, 88, "flush first, then clear: the next find() reloads qty = 4", 10, GREEN))
    d.add(svg_text(8, 106, "bulk UPDATE skips @Version: bump it by hand", 10, PENCIL))
    d.h = 112
    return d


def dia_8():
    d = canvas(112)
    flow(d, ["by type", "2 candidates", "@Qualifier(name)"], 8, size=10, gap=16, fills={1: RED_T, 2: GREENISH}, colors={1: RED})
    d.add(svg_text(8, 52, "@RequiredArgsConstructor copies @Qualifier only if", 10, INK))
    d.add(svg_text(8, 66, "lombok.config says so (copyableAnnotations)", 10, INK))
    flow(d, ["final field", "generated ctor", "param + @Qualifier"], 76, size=9.5, gap=12, fills={2: GREENISH})
    d.h = 108
    return d


# ------------------------------------------------------------------ the cards
CARDS = [
    dict(
        n=1, tier="MUST", ref="DATA", ask="DESIGN",
        title="The last unit", short="No overselling",
        q="Two orders each want the <b>last unit</b> of the same product at the same instant. How does inventory-service guarantee it never oversells, and why does it use locks <u>and</u> a conditional UPDATE <u>and</u> a CHECK constraint?",
        ex_dia=ex_1, ex=[("both read qty 1, both write qty 0", "stock goes to -1, or an update is lost", "read-modify-write race")],
        kind="NO OVERSELL  |  lock + atomic UPDATE + CHECK", keep="keep: stock rows locked in a fixed order",
        rule="check and decrement in ONE statement",
        spot="lost update, stock never negative, hot row, deadlock",
        dia=dia_1,
        steps=[
            "<b>Lock</b> the order's stock rows with <b>PESSIMISTIC_WRITE</b>, always <u>ORDER BY product id</u>: orders that share products can never lock them in opposite orders, so no deadlock.",
            "Check every line first. A shortfall changes nothing and comes back as a <b>result</b> (inventory.failed), not an exception.",
            "Take stock with <b>SET quantity = quantity - :n WHERE quantity >= :n</b>: the check and the decrement are one statement, nothing can slip between a read and a write.",
            "<b>CHECK (quantity >= 0)</b> in the database has the last word, whatever the code does.",
        ],
        code=["@Lock(LockModeType.PESSIMISTIC_WRITE)",
              "@Query(\"SELECT i FROM Inventory i WHERE i.productId IN :ids \"",
              "     + \"ORDER BY i.productId\")",
              "List<Inventory> lockByProductIds(Collection<String> ids);",
              "@Modifying(flushAutomatically = true, clearAutomatically = true)",
              "@Query(\"UPDATE Inventory i SET i.quantity = i.quantity - :n, \"",
              "     + \"i.version = i.version + 1 \"",
              "     + \"WHERE i.productId = :p AND i.quantity >= :n\")"],
        cost="hot rows serialize", twins="optimistic + retry",
        trap="@Version alone only <u>detects</u> the race: on a hot row it turns into a retry storm. And lock in a FIXED order, or two multi-product orders deadlock.",
    ),
    dict(
        n=2, tier="CORE", ref="DATA", ask="EXPLAIN",
        title="Last write wins?", short="Optimistic locking + ETag",
        q="Two admins edit the same product at once. How does the API stop the second save from silently overwriting the first, and which status code does each kind of conflict return?",
        ex_dia=ex_2, ex=[("both read v3, both write back", "the first edit is silently lost", "no concurrency control")],
        kind="OPTIMISTIC LOCKING  |  @Version + ETag", keep="keep: a version number on the row",
        rule="write only if the version is still the one I read",
        spot="lost update over HTTP, edit conflict, If-Match, 412 vs 409",
        dia=dia_2,
        steps=[
            "<b>@Version</b> column: Hibernate adds <b>WHERE version = ?</b> to the UPDATE and bumps it. Zero rows updated means someone got there first.",
            "The version is the resource's strong <b>ETag</b>, returned on every read.",
            "Client sends <b>If-Match</b>: a mismatch gives <b class=r>412 Precondition Failed</b> before any write. No header: last write wins.",
            "A conflict caught at flush (<b>OptimisticLockingFailureException</b>) gives <b class=r>409 CONCURRENT_MODIFICATION</b>: re-read and retry.",
        ],
        code=["// EntityTags.verifyIfMatch(ifMatch, currentVersion)",
              "if (ifMatch == null || ifMatch.isBlank()) return; // last write wins",
              "...",
              "throw new PreconditionFailedException(",
              "    \"changed since you read it (current version \" + current + \")\",",
              "    \"PRECONDITION_FAILED\");",
              "@ExceptionHandler(OptimisticLockingFailureException.class)",
              "// -> 409 CONCURRENT_MODIFICATION"],
        cost="a retry path for the caller", twins="pessimistic lock, CAS",
        trap="Optimistic locking only <u>detects</u>: the caller must re-read and retry. A bulk JPQL UPDATE does not bump @Version by itself: the inventory queries set <b>version = version + 1</b> by hand.",
    ),
    dict(
        n=3, tier="MUST", ref="SPRING", ask="DEBUG",
        title="The annotation that did nothing", short="Same-class call skips the proxy",
        q="A method calls another method of the <u>same class</u> that is annotated <b>@Async</b>. It runs on the caller's thread and nothing is asynchronous. Why does that happen (it hits @Transactional and @Cacheable too), and what fix does this codebase use?",
        ex_dia=ex_3, ex=[("this.evictAsync(id, sku)", "runs inline on the request thread", "the proxy is bypassed")],
        kind="SPRING AOP PROXY  |  same-class call", keep="keep: the call must go THROUGH the proxy",
        rule="advice only runs on calls that arrive from another bean",
        spot="@Async not async, @Transactional ignored",
        dia=dia_3,
        steps=[
            "Spring wraps the bean in a <b>proxy</b> that adds @Async / @Transactional / @Cacheable. Other beans hold the proxy, not the object.",
            "<b class=r>this.method()</b> is a plain Java call on the <u>target</u>: it never meets the proxy, so the advice does not run.",
            "Fix: <b>move the method to another bean</b> (ProductCacheEvictor, CustomerMetricsRecorder). Self-injection or AopContext.currentProxy() work but hide the design smell.",
            "Private and final methods cannot be advised at all (a CGLIB proxy subclasses the class and overrides).",
        ],
        code=["// ProductService: goes THROUGH the proxy",
              "cacheEvictor.evictAsync(id, sku);",
              "@Component",
              "class ProductCacheEvictor {",
              "  @Async(\"cacheEvictionExecutor\")",
              "  public void evictAsync(Long id, String sku) { ... }",
              "}"],
        cost="one more small bean", twins="AspectJ weaving, AopContext",
        trap="It compiles, and a unit test that does <b>new</b> on the class passes. It only misbehaves when Spring wires it: test through the context and log the thread name once.",
    ),
    dict(
        n=4, tier="CORE", ref="SPRING", ask="DESIGN",
        title="Join or fail", short="Propagation.MANDATORY",
        q="EventPublisher.publish, InboxService.firstDelivery and ReservationService are annotated <b>Propagation.MANDATORY</b>. What does that buy compared with the default REQUIRED?",
        ex_dia=ex_4, ex=[("publish() called with no transaction", "the outbox row commits alone", "the business change may roll back")],
        kind="TRANSACTION PROPAGATION  |  a guardrail", keep="keep: the caller owns the transaction",
        rule="no transaction: fail loudly, never start one silently",
        spot="outbox write, inbox claim, a service that must be part of a bigger tx",
        dia=dia_4,
        steps=[
            "<b>REQUIRED</b> (the default) joins the caller's transaction or starts one. A forgotten @Transactional upstream silently gives the call its <u>own</u> transaction.",
            "<b>MANDATORY</b> joins or throws <b>IllegalTransactionStateException</b>. The outbox write and the inbox claim <u>must</u> commit with the business change, so calling them bare is a bug.",
            "The design rule: the saga handler owns the transaction; publish, claim and reserve only ever join it.",
            "<b>REQUIRES_NEW</b> is the opposite: it suspends the outer transaction (own commit, own failure).",
        ],
        code=["@Transactional(propagation = Propagation.MANDATORY)",
              "public void publish(DomainEvent event) { ... }",
              "@Transactional(propagation = Propagation.MANDATORY)",
              "public boolean firstDelivery(String consumer, String id) {",
              "  return repository.claim(consumer, id, Instant.now()) == 1;",
              "}",
              "@Transactional(propagation = Propagation.MANDATORY)",
              "public class ReservationService { ... }"],
        cost="callers must open the transaction", twins="REQUIRES_NEW, NESTED",
        trap="REQUIRES_NEW 'so the audit row survives a rollback' needs a second DB connection while the first is held: under load that can deadlock the connection pool.",
    ),
    dict(
        n=5, tier="MUST", ref="JAVA", ask="EXPLAIN",
        title="Why only two threads", short="ThreadPoolExecutor order",
        q="<b>cacheEvictionExecutor</b> is core 2, max 4, queue 100. You submit 50 slow tasks at once. How many threads run, and when would the pool ever grow to 4?",
        ex_dia=ex_5, ex=[("50 slow tasks submitted at once", "2 threads run, 48 wait in the queue", "max 4 is never reached")],
        kind="THREADPOOLEXECUTOR  |  core, queue, max, reject", keep="keep: the order of the four steps",
        rule="threads beyond core appear only when the queue is FULL",
        spot="pool size, queue capacity, RejectedExecutionException",
        dia=dia_5,
        steps=[
            "Fewer than <b>core</b> threads: start a new thread for the task, even if others are idle.",
            "All core threads busy: put the task in the <b>queue</b>. No new thread yet.",
            "<u>Queue full</u>: only now create threads, up to <b>max</b>.",
            "Max reached and queue full: the <b>rejection policy</b> (the default throws RejectedExecutionException).",
        ],
        code=["var ex = new ThreadPoolTaskExecutor();",
              "ex.setCorePoolSize(2);",
              "ex.setMaxPoolSize(4);",
              "ex.setQueueCapacity(100);   // threads 3, 4 only after 100 wait",
              "ex.setThreadNamePrefix(\"cache-evict-\");",
              "ex.setWaitForTasksToCompleteOnShutdown(false);",
              "ex.initialize();"],
        cost="deep queue = latency", twins="CallerRunsPolicy",
        trap="A big queue makes max pool size dead configuration, and Spring's default queue (Integer.MAX_VALUE) never rejects and never grows past core. Size the queue small, or set core = max.",
    ),
    dict(
        n=6, tier="CORE", ref="JAVA", ask="DEBUG",
        title="Which exception is that?", short="CompletableFuture fan-out",
        q="Two independent HTTP lookups run with <b>CompletableFuture.supplyAsync</b>. One throws. What does <b>join()</b> throw, why does the code unwrap it, and why does it pass its own executor?",
        ex_dia=ex_6, ex=[("join() on a failed future", "CompletionException, not your exception", "the real cause is wrapped")],
        kind="COMPLETABLEFUTURE  |  fan-out, join, errors", keep="keep: a dedicated, bounded executor",
        rule="join() wraps a failure in CompletionException: unwrap it",
        spot="parallel calls, join vs get, exception in async code",
        dia=dia_6,
        steps=[
            "<b>supplyAsync(call, executor)</b> with an explicit executor: without one it uses <b>ForkJoinPool.commonPool</b>, sized for CPU work and shared with parallel streams.",
            "Start <u>both</u>, then join: cost is the slower call. Joining A before starting B serialises them.",
            "<b>join()</b> throws <b>CompletionException</b> around the real cause (<b>get()</b> throws a checked ExecutionException). Unwrap so callers see the original.",
            "A saturated executor throws <b>RejectedExecutionException</b> from supplyAsync itself: catch it and return <b>failedFuture</b>.",
        ],
        code=["static <T> T join(CompletableFuture<T> f) {",
              "  try { return f.join(); }",
              "  catch (CompletionException e) {",
              "    if (e.getCause() instanceof RuntimeException re)",
              "      throw re;            // the original exception",
              "    throw e;",
              "  }",
              "}"],
        cost="threads and a join point", twins="virtual threads, structured concurrency",
        trap="No timeout by default: add <b>orTimeout</b> or a client read timeout, or join() can wait for ever. And thread-locals (security context, MDC) do not follow the task: copy them in.",
    ),
    dict(
        n=7, tier="STRETCH", ref="JPA", ask="DEBUG",
        title="The stale entity", short="@Modifying and the persistence context",
        q="After a bulk <b>UPDATE</b> query the same method reads the entity again and still sees the old quantity. What is happening, and what do <b>flushAutomatically</b> and <b>clearAutomatically</b> on @Modifying do?",
        ex_dia=ex_7, ex=[("UPDATE ... qty - 1, then find(id)", "the old quantity", "first-level cache")],
        kind="PERSISTENCE CONTEXT  |  bulk UPDATE bypasses it", keep="keep: the EntityManager's first-level cache",
        rule="a JPQL UPDATE goes straight to the DB; loaded entities go stale",
        spot="stale read after update, flush, clear, first-level cache",
        dia=dia_7,
        steps=[
            "Inside a transaction, loaded entities live in the <b>persistence context</b>. A second find returns the cached object, not a new SELECT.",
            "A JPQL <b>UPDATE</b> runs in the database directly: the cached entity is not touched, so it is stale (and not even dirty).",
            "<b>flushAutomatically</b> writes pending changes before the UPDATE, so they are not lost or reordered.",
            "<b>clearAutomatically</b> detaches everything afterwards so the next read reloads. It also detaches entities you still hold.",
        ],
        code=["@Modifying(flushAutomatically = true,",
              "           clearAutomatically = true)",
              "@Query(\"UPDATE Inventory i \"",
              "     + \"SET i.quantity = i.quantity + :n, \"",
              "     + \"i.version = i.version + 1 \"",
              "     + \"WHERE i.productId = :productId\")",
              "int increment(String productId, int n);"],
        cost="one detach of everything per call", twins="dirty checking, @DynamicUpdate",
        trap="clear() throws away pending changes to OTHER entities you changed but did not flush: that is why flush comes first. A bulk update also skips @Version and lifecycle callbacks.",
    ),
    dict(
        n=8, tier="CORE", ref="SPRING", ask="EXPLAIN",
        title="Two executors, one parameter", short="Qualifier + Lombok",
        q="order-service has several <b>Executor</b> beans (remoteCallExecutor, sagaReaperExecutor). Injecting a plain Executor fails at startup. Why, how is it fixed, and what does <b>lombok.config</b> have to do with @RequiredArgsConstructor?",
        ex_dia=ex_8, ex=[("constructor asks for an Executor", "NoUniqueBeanDefinitionException", "by type, 2 candidates")],
        kind="DEPENDENCY INJECTION  |  by type, then by name", keep="keep: constructor injection, one bean per role",
        rule="match by type first; several candidates need @Qualifier",
        spot="NoUniqueBeanDefinitionException, @Primary, @Qualifier, Lombok",
        dia=dia_8,
        steps=[
            "Spring resolves by <b>type</b>. Two beans of type Executor give <b>NoUniqueBeanDefinitionException</b> at startup, not at the first call.",
            "Choose by <b>@Qualifier(\"remoteCallExecutor\")</b> on the parameter (the bean name), or mark one bean <b>@Primary</b>.",
            "<b>@RequiredArgsConstructor</b> builds the constructor but does not copy a field's @Qualifier unless <b>lombok.config</b> lists it: <b>lombok.copyableAnnotations</b>.",
            "Constructor injection: final fields, easy to test without Spring, a missing or circular dependency fails fast.",
        ],
        code=["public OrderPlacementService(OrderService orderService,",
              "    CatalogClient catalog, OrderProperties properties,",
              "    @Qualifier(\"remoteCallExecutor\") Executor exec) {",
              "  ...",
              "}",
              "# lombok.config",
              "lombok.copyableAnnotations +=",
              "  org.springframework.beans.factory.annotation.Qualifier"],
        cost="names are strings", twins="@Primary, @Resource",
        trap="A qualifier is a string: a renamed bean compiles and fails at startup. The same goes for <b>@Async(\"name\")</b>. Check wiring in a context test, not by eye.",
    ),
]


# ---- numbers and claims on the cards, checked
def _pool(core, maximum, queue, tasks):
    """ThreadPoolExecutor admission rule: threads below core, then the queue, then threads up to max, then reject"""
    threads = queued = rejected = 0
    for _ in range(tasks):
        if threads < core:
            threads += 1
        elif queued < queue:
            queued += 1
        elif threads < maximum:
            threads += 1
        else:
            rejected += 1
    return threads, queued, rejected


assert _pool(2, 4, 100, 50) == (2, 48, 0)             # card 5: 50 slow tasks on cacheEvictionExecutor: 2 threads run, 48 wait
assert _pool(2, 4, 100, 104) == (4, 100, 0)           # threads 3 and 4 appear only once 100 are waiting (the sheet t03 runs this on the real JDK class)
assert _pool(2, 4, 100, 105) == (4, 100, 1)           # and the next one is rejected
assert _pool(8, 16, 50, 58) == (8, 50, 0) and _pool(8, 16, 50, 66) == (16, 50, 0)      # remoteCallExecutor: 8 threads until 50 tasks wait
assert sorted(["p2", "p1"]) == sorted(["p1", "p2"])   # card 1: orders [p1,p2] and [p2,p1] both lock p1 first when ordered by product id
