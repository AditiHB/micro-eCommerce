"""Deck: topic 02, Resilience and service-to-service communication, 8 cards. Grounded in config-repo/api-gateway.yml, RestClients, OrderPlacementService,
RateLimitingFilter, FailSafeCache and the executors (docs/interview/microservices/03, 04, 08, 09). The circuit-breaker numbers were also run through the real
Resilience4j 2.1.0 library with the gateway's settings (see the asserts at the bottom).
exec()d inside gen_flash.py: every engine helper (Dia, canvas, flow, cells, box_text, note, ...) is in scope."""

DECK_TITLE = "Resilience and communication"
DECK_LABEL = "Resilience and communication"
CHECK_QS = [
    "What happens when the <u>callee</u> is slow, down or half-working?",
    "Who gives up first, and is the work <u>cancelled</u> or only abandoned?",
    "Is it safe to <u>repeat</u> the call? What makes it so?",
    "What does it cost, and what is the failure mode of the protection itself?",
]

RED_T, BLUE_T, YEL = "#fde3df", "#e3edfa", "#fff3a8"


# ------------------------------------------------------------------ scenario pictures (front)
def ex_1():
    d = ExDia()
    cells(d, list("SSFFF"), idx=False, gap=4, fills={0: GREENISH, 1: GREENISH, 2: RED_T, 3: RED_T, 4: RED_T}, colors={2: RED, 3: RED, 4: RED}, maxpitch=40)
    return d


def ex_2():
    d = canvas(60)
    flow(d, ["client", "gateway", "order", "catalogue"], 8, size=10, gap=22, under={0: ("10 s", PENCIL), 1: ("9 s", PENCIL), 2: ("8 s", PENCIL), 3: ("hangs", RED)},
         fills={3: RED_T}, colors={3: RED})
    d.h = 52
    return d


def ex_3():
    d = canvas(60)
    flow(d, ["client", "gateway", "order-service"], 8, gap=28, fills={2: GREENISH}, under={0: ("POST /orders", PENCIL), 1: ("timeout", RED), 2: ("order saved!", GREEN)})
    d.h = 52
    return d


def ex_4():
    d = canvas(60)
    flow(d, ["1 client", "gateway", "orders"], 8, gap=28, fills={1: YEL}, under={0: ("150 req/min", RED), 1: ("limit: 100", PENCIL)})
    d.h = 52
    return d


def ex_5():
    d = canvas(60)
    flow(d, ["reaper job", "shared pool", "order requests"], 8, gap=22, fills={0: RED_T, 1: YEL}, colors={0: RED}, under={0: ("100 tasks", RED), 2: ("starved", RED)}, size=10)
    d.h = 52
    return d


def ex_6():
    d = canvas(60)
    flow(d, ["GET product", "Redis (down)", "DB"], 8, gap=24, fills={1: RED_T, 2: GREENISH}, colors={1: RED}, dashed={1}, under={1: ("timeout", RED)})
    d.h = 52
    return d


def ex_7():
    d = canvas(60)
    flow(d, ["user JWT", "order-service", "customer-service"], 8, size=10, gap=18, fills={2: BLUE_T}, under={2: ("who is calling?", PENCIL)})
    d.h = 52
    return d


def ex_8():
    d = canvas(60)
    flow(d, ["BEGIN", "remote call 1.5 s", "UPDATE", "COMMIT"], 8, size=10, gap=14, fills={1: RED_T, 0: YEL, 3: YEL}, colors={1: RED}, under={1: ("connection held", RED)})
    d.h = 52
    return d


# ------------------------------------------------------------------ explanation pictures (back)
def dia_1():
    d = canvas(94)
    flow(d, ["CLOSED", "OPEN", "HALF_OPEN"], 8, gap=40, size=10.5, fills={0: GREENISH, 1: RED_T, 2: YEL}, colors={1: RED})
    d.add(svg_text(8, 52, "CLOSED -> OPEN: >= 50 % of the last 10 failed (min 5 calls)", 9.5, INK))
    d.add(svg_text(8, 67, "OPEN: calls rejected at once (no network). After 30 s ...", 9.5, RED))
    d.add(svg_text(8, 82, "HALF_OPEN: 5 probes. < 50 % fail = CLOSED, else OPEN", 9.5, GREEN))
    return d


def dia_2():
    d = canvas(122)
    px = DW / 12.0                                       # px per second on the bars
    rows = [("time limiter 10 s", 10, BLUE_T), ("gateway response-timeout 9 s", 9, YEL), ("service budget 8 s", 8, GREENISH), ("read 1.5 s", 1.5, RED_T)]
    for i, (label, secs, fill) in enumerate(rows):
        y = 6 + i * 27
        w = secs * px
        d.add(rect(8, y, w, 21, INK, 1.5, 1.2, .5, fill=fill))
        if w > 120:
            d.add(svg_text(14, y + 15, label, 10, INK))
        else:
            d.add(svg_text(8 + w + 6, y + 15, label + "  (connect 0.5 s)", 10, RED))
    d.add(svg_text(8, 118, "inner timeouts fire first", 10, INK))
    d.h = 126
    return d


def dia_3():
    d = canvas(122)
    d.add(svg_text(8, 12, "key K, new", 9.5, GREEN))
    flow(d, ["POST + K", "no record", "create + remember"], 16, size=10, gap=14, fills={2: GREENISH})
    d.add(svg_text(8, 56, "key K again, same body", 9.5, TEAL))
    flow(d, ["POST + K", "hash equal", "same order back"], 60, size=10, gap=14, fills={2: BLUE_T})
    d.add(svg_text(8, 100, "key K again, other body", 9.5, RED))
    flow(d, ["POST + K", "hash differs", "422 REUSED"], 104, size=10, gap=14, fills={2: RED_T}, colors={2: RED})
    d.h = 134
    return d


def dia_4():
    d = canvas(118)
    flow(d, ["request", "INCR key", "count > limit?"], 8, size=10, gap=20, fills={1: YEL})
    d.add(svg_text(8, 52, "no:  X-RateLimit-Remaining header, pass on", 10, GREEN))
    d.add(svg_text(8, 67, "yes: 429 Too Many Requests", 10, RED))
    d.add(rect(8, 76, 150, 20, INK, 1.4, 1, .4, fill=BLUE_T))
    d.add(rect(158, 76, 150, 20, INK, 1.4, 1, .4, fill=BLUE_T))
    d.add(svg_text(83, 90, "window 1 (60 s)", 9.5, INK, "middle"))
    d.add(svg_text(233, 90, "window 2", 9.5, INK, "middle"))
    d.add(svg_text(158, 112, "100 at the end + 100 at the start = 200 in seconds", 9.5, RED, "middle"))
    d.h = 120
    return d


def dia_5():
    d = canvas(112)
    rows = [("remoteCallExecutor", "core 8 / max 16 / queue 50", BLUE_T), ("sagaReaperExecutor", "core 4 / max 8 / queue 100", YEL), ("cacheEvictionExecutor", "core 2 / max 4 / queue 100", GREENISH)]
    for i, (name, size, fill) in enumerate(rows):
        y = 6 + i * 28
        w = box_text(d, 8, y, name, 10, 22, INK, fill)
        d.add(svg_text(8 + w + 8, y + 15, size, 9.5, PENCIL))
    d.add(svg_text(8, 100, "submit: core thread, then queue, then max, then REJECT", 10, INK))
    d.h = 108
    return d


def dia_6():
    d = canvas(110)
    flow(d, ["read", "Redis fails", "treat as a miss", "load from DB"], 8, size=10, gap=12, fills={1: RED_T, 2: YEL, 3: GREENISH}, colors={1: RED})
    d.add(svg_text(8, 54, "write / evict fails: skip it, log it, the request goes on", 10, INK))
    flow(d, ["COMMIT", "afterCommit", "evict (async)"], 66, size=10, gap=20, fills={0: YEL, 2: GREENISH})
    d.add(svg_text(8, 104, "evict only AFTER the commit: no re-caching the old row", 10, PENCIL))
    d.h = 110
    return d


def dia_7():
    d = canvas(118)
    flow(d, ["request thread", "interceptor", "Authorization: Bearer"], 8, size=10, gap=14, fills={0: GREENISH, 2: BLUE_T})
    d.add(svg_text(8, 50, "pool thread: SecurityContextHolder is EMPTY", 10, RED))
    flow(d, ["worker thread", "no header", "401"], 58, size=10, gap=20, fills={0: RED_T, 1: RED_T, 2: RED_T}, colors={0: RED, 1: RED, 2: RED})
    d.add(svg_text(8, 100, "fix: capture on the request thread, set + restore on the worker", 10, GREEN))
    d.h = 108
    return d


def dia_8():
    d = canvas(112)
    flow(d, ["validate", "price", "BEGIN"], 26, size=10, gap=18, fills={0: BLUE_T, 1: BLUE_T, 2: YEL})
    flow(d, ["INSERT order", "INSERT outbox", "COMMIT"], 76, size=10, gap=14, fills={0: GREENISH, 1: GREENISH, 2: YEL})
    d.add(svg_text(8, 20, "remote calls, in parallel, NO connection held", 9.5, INK))
    d.add(svg_text(8, 70, "one short, atomic write", 9.5, GREEN))
    d.add(svg_text(8, 118, "cost of the lookups = the slower call, not the sum", 10, PENCIL))
    d.h = 124
    return d


# ------------------------------------------------------------------ the cards
CARDS = [
    dict(
        n=1, tier="MUST", ref="RESILIENCE", ask="EXPLAIN",
        title="When the breaker opens", short="Circuit breaker",
        q="A downstream service starts failing. With the gateway's settings (a window of 10 calls, minimum 5, open at 50 % failures, 30 s wait, 5 probes) walk through what the breaker does, and what the caller sees while it is open.",
        ex_dia=ex_1, ex=[("S S F F F  (3 of 5 failed)", "OPEN", "60 % >= 50 %"), ("S S F F  (2 of 4)", "still CLOSED", "under the minimum of 5 calls", "g")],
        kind="CIRCUIT BREAKER  |  3 states", keep="keep: a window of the last 10 outcomes",
        rule="open at >= 50 % failures, once 5 calls are in the window",
        spot="fail fast, cascading failure, protect a struggling dependency",
        dia=dia_1,
        steps=[
            "<b class=g>CLOSED</b>: calls pass and are counted. The failure rate is <u>not evaluated</u> until 5 calls are in the window.",
            "<b class=r>OPEN</b>: calls are rejected with no network call. The gateway answers <b>503 + Retry-After: 30</b> as a problem document.",
            "After 30 s the next call becomes a probe: <b class=t>HALF_OPEN</b> lets 5 through. Under 50 % failed: CLOSED, with a fresh window. Otherwise OPEN again.",
        ],
        code=["resilience4j.circuitbreaker.configs.default:",
              "  slidingWindowSize: 10",
              "  minimumNumberOfCalls: 5",
              "  failureRateThreshold: 50",
              "  waitDurationInOpenState: 30000ms",
              "  permittedNumberOfCallsInHalfOpenState: 5",
              "  slowCallDurationThreshold: 2000ms",
              "  slowCallRateThreshold: 100"],
        cost="a rejected call costs ~nothing", twins="time limiter, fallback",
        trap="<b>slowCallRateThreshold: 100</b> opens only when EVERY call in the window is slow: 4 slow of 5 stays CLOSED. And the state lives in each gateway JVM, so every instance learns on its own.",
    ),
    dict(
        n=2, tier="MUST", ref="RESILIENCE", ask="DESIGN",
        title="Who gives up first", short="Timeout budget",
        q="A client calls the gateway, which calls order-service, which calls the catalogue. Each hop has its own timeout. In what order must they fire, and what goes wrong if you set them the other way round?",
        ex_dia=ex_2, ex=[("catalogue never answers", "a pool thread waits for ever", "no timeout = a thread leak")],
        kind="TIMEOUT BUDGET  |  edge to inside", keep="keep: every inner timeout shorter than the outer one",
        rule="the callee gives up before the caller does",
        spot="hung connection, slow dependency, cascading latency",
        dia=dia_2,
        steps=[
            "<b>Every</b> outbound call has an explicit connect and read timeout. An HTTP call with none is a thread leak waiting for a slow peer.",
            "Nest them from the edge inward: time limiter <b>10 s</b> > gateway response <b>9 s</b> > service budget <b>8 s</b> > downstream read <b>1.5 s</b> (connect 0.5 s).",
            "The inner timeout fires first, so the user gets a clean, mapped error instead of an opaque gateway timeout that abandons work mid-flight.",
        ],
        code=["// RestClients.builder(baseUrl, connect, read)",
              "HttpClient.Builder b = HttpClient.newBuilder()",
              "        .connectTimeout(connectTimeout);   // 500 ms",
              "var f = new JdkClientHttpRequestFactory(b.build());",
              "f.setReadTimeout(readTimeout);             // 1500 ms",
              "// api-gateway.yml: response-timeout: 9s",
              "// timelimiter: timeoutDuration: 10000ms"],
        cost="a timeout is a decision, not a default", twins="circuit breaker, retries",
        trap="A timeout abandons the wait, it does <u>not</u> cancel the work: the callee may still finish and commit. Pair timeouts with idempotency, and never let a caller's timeout be shorter than its callee's.",
    ),
    dict(
        n=3, tier="MUST", ref="RESILIENCE", ask="TRADE-OFF",
        title="Retry the order?", short="Retries and idempotency",
        q="POST /orders times out at the gateway. Should the gateway retry it? Why does this repo have no Retry filter on any route, and what protects a client that retries after a timeout?",
        ex_dia=ex_3, ex=[("timeout after the order was saved", "retry creates a second order", "unknown outcome")],
        kind="RETRY ONLY WHAT IS IDEMPOTENT", keep="keep: Idempotency-Key + a SHA-256 of the body",
        rule="same key + same body = same order; other body = 422",
        spot="timeout, duplicate request, at-least-once client, retry storm",
        dia=dia_3,
        steps=[
            "A gateway retry of <b>POST /orders</b> could create <b class=r>two orders</b>, so no route has a Retry filter. Retries belong to the <u>client</u>, with a key.",
            "The server stores (principal, key, hash of the body, order id) in the <u>same transaction</u> as the order.",
            "Two requests racing with one key: the unique constraint rolls the loser back, and it then replays the winner's order.",
        ],
        code=["var replay = orderService.findReplay(p, key, hash);",
              "if (replay.isPresent()) return replay.get();",
              "try {",
              "  return orderService.place(cust, cur, lines, p, key, hash);",
              "} catch (DataIntegrityViolationException raced) {",
              "  return orderService.findReplay(p, key, hash)",
              "      .orElseThrow(() -> raced);   // the other one won",
              "}"],
        cost="1 table + a hash per create", twins="exactly-once effect",
        trap="Scope the key to the caller and fingerprint the body, or one client can replay another's key. Real retries also need backoff, jitter and a budget: blind retries multiply load on a service that is already failing.",
    ),
    dict(
        n=4, tier="CORE", ref="RESILIENCE", ask="EXPLAIN",
        title="Counting requests", short="Rate limiting at the gateway",
        q="How does the gateway rate-limit a client, what does a client over the limit see, and what are the weak points of this design?",
        ex_dia=ex_4, ex=[("a client sends 150 requests in a minute", "the 101st gets 429", "order limit is 100 / min")],
        kind="FIXED-WINDOW COUNTER  |  Redis INCR", keep="keep: rate_limit:<client ip> with a 60 s TTL",
        rule="INCR is atomic; set the TTL only when the count is 1",
        spot="abuse, noisy neighbour, protect a backend, 429",
        dia=dia_4,
        steps=[
            "One atomic <b>INCR</b> per request (GET then SET would let a burst under-count). <b>EXPIRE 60 s</b> only when the count is 1, so the window really is fixed.",
            "Over the limit: <b class=r>429</b>. Otherwise <b>X-RateLimit-Limit / -Remaining</b> go back. Limits per route: orders 100, customers 150, inventory 200, payments 50.",
            "The client is the first <b>X-Forwarded-For</b> value, else the remote address. The key has no route in it: all routes of one client share one counter.",
        ],
        code=["long n = redis.opsForValue().increment(key);",
              "if (n == 1) redis.expire(key, 60, SECONDS);",
              "if (n > maxRequests) {",
              "  res.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);",
              "  return res.setComplete();",
              "}",
              "addResponseHeaders(exchange, n, maxRequests);"],
        cost="1 Redis round trip per request", twins="token bucket, sliding window",
        trap="Name the weak points: a 2x burst across a window edge; X-Forwarded-For is spoofable unless the edge overwrites it; INCR and EXPIRE are two calls (a crash between leaves a key with no TTL); RedisTemplate is a <u>blocking</u> client inside a reactive gateway.",
    ),
    dict(
        n=5, tier="CORE", ref="RESILIENCE", ask="DESIGN",
        title="A pool for each job", short="Bulkheads: pool per concern",
        q="A burst of background work, the saga reaper, must never slow down order placement. How does this codebase isolate them, what happens when a pool is full, and what would a Resilience4j Bulkhead add?",
        ex_dia=ex_5, ex=[("a reaper pass queues 100 tasks", "request threads wait behind them", "one shared pool")],
        kind="BULKHEAD  |  a bounded pool per concern", keep="keep: small, separate, bounded executors",
        rule="a full queue sheds load instead of piling up",
        spot="noisy neighbour, shared pool, starvation",
        dia=dia_5,
        steps=[
            "<b>One pool per concern</b>, each sized for its job: remote-call fan-out 8 / 16 / queue 50, saga reaper 4 / 8 / 100, cache eviction 2 / 4 / 100.",
            "<b>ThreadPoolExecutor order</b>: core threads first, then the <u>queue</u>, only then up to max, then reject. With queue 50 the pool stays at 8 threads until 50 tasks wait.",
            "A rejected submit is caught and mapped to <b>503</b> (DependencyUnavailableException), not an unhandled 500.",
            "A Resilience4j <b>Bulkhead</b> would add a per-dependency concurrency cap. This code does not use one (no @Bulkhead).",
        ],
        code=["try {",
              "  return CompletableFuture.supplyAsync(call, remoteCallExecutor);",
              "} catch (RejectedExecutionException e) {",
              "  log.warn(\"remoteCallExecutor is saturated\");",
              "  return CompletableFuture.failedFuture(",
              "      new DependencyUnavailableException(\"busy, retry\"));",
              "}"],
        cost="threads and memory per pool", twins="semaphore bulkhead, load shedding",
        trap="A big queue hides overload as latency, not errors. Isolate per <u>dependency</u> as well as per concern, or one slow catalogue still eats the whole fan-out pool.",
    ),
    dict(
        n=6, tier="CORE", ref="RESILIENCE", ask="DESIGN",
        title="Redis is down", short="Cache failure isolation",
        q="Redis goes down. Should product reads fail? And what should happen to a cache eviction that fails <u>after</u> the database commit?",
        ex_dia=ex_6, ex=[("Redis unreachable", "every product read errors", "cache treated as a dependency")],
        kind="FAIL-SAFE CACHE  |  a miss, never an error", keep="keep: the database as the source of truth",
        rule="cache errors are misses; failed puts and evicts are skipped",
        spot="cache outage, optional dependency, graceful degradation",
        dia=dia_6,
        steps=[
            "Wrap the cache itself (<b>FailSafeCache</b>): a failing read is a <b>miss</b>; a failing put or evict is skipped and logged.",
            "It covers what Spring's CacheErrorHandler does not: a cache used directly, and the put/evict a transaction-aware cache runs <u>after commit</u>.",
            "An exception after the commit would fail a request whose change was already saved, and the caller would retry a write that happened.",
            "The TTL (10 min) is only a safety net; entries are evicted by key when the record changes.",
        ],
        code=["public ValueWrapper get(Object key) {",
              "  try { return delegate.get(key); }",
              "  catch (RuntimeException e) {",
              "    return failed(\"get\", key, e, null);   // = miss",
              "  }",
              "}",
              "manager.setTransactionAware(true);   // after commit"],
        cost="a stale entry until the TTL", twins="cache-aside, write-through",
        trap="Only reference data is cached. Stock and order status are never cached: they change on every saga step, and a stale read there is a wrong answer, not a slow one.",
    ),
    dict(
        n=7, tier="CORE", ref="SECURITY", ask="DEBUG",
        title="The vanishing token", short="Token relay across threads",
        q="order-service calls customer-service on behalf of a logged-in user. Whose identity should the call carry, and what silently breaks when you move that call onto another thread?",
        ex_dia=ex_7, ex=[("same call, run on a pool thread", "401 from customer-service", "the header is gone")],
        kind="TOKEN RELAY  |  the caller's JWT", keep="keep: the user's access token, forwarded as it is",
        rule="downstream authorizes the user, not the calling service",
        spot="service-to-service auth, on-behalf-of, ThreadLocal lost in async code",
        dia=dia_7,
        steps=[
            "<b>BearerTokenRelayInterceptor</b> copies the caller's JWT onto the outgoing request (if there is one and no Authorization header yet).",
            "The callee applies the <u>user's</u> authorization: a customer can only validate their own record. No broad service credential to leak.",
            "<b>SecurityContextHolder</b> is a ThreadLocal: on a pool thread it is empty, so the relayed header vanishes.",
            "Capture the context on the request thread, set it on the worker, restore the previous one in <b>finally</b>.",
        ],
        code=["SecurityContext ctx = SecurityContextHolder.getContext();",
              "supplyAsync(() -> onContext(ctx, call), executor);",
              "static <T> T onContext(SecurityContext c, Supplier<T> call) {",
              "  var prev = SecurityContextHolder.getContext();",
              "  SecurityContextHolder.setContext(c);",
              "  try { return call.get(); }",
              "  finally { SecurityContextHolder.setContext(prev); }",
              "}"],
        cost="a token travels through every hop", twins="mTLS = which service, not which user",
        trap="Do not use MODE_INHERITABLETHREADLOCAL on pooled threads: a thread keeps the identity of whichever request created it, so identities leak between requests. Pass it explicitly or wrap the executor.",
    ),
    dict(
        n=8, tier="STRETCH", ref="DATA", ask="DESIGN",
        title="Calls before the transaction", short="No remote calls in a tx",
        q="OrderPlacementService validates the customer and prices every line from the catalogue <u>before</u> any database transaction opens, and does the two lookups concurrently. Why not simply call them inside the @Transactional method?",
        ex_dia=ex_8, ex=[("a 1.5 s remote call inside the tx", "the DB connection is held 1.5 s", "pool exhaustion under load")],
        kind="REMOTE CALLS OUTSIDE THE TRANSACTION", keep="keep: one short, atomic write at the end",
        rule="slow I/O never holds a connection or a lock",
        spot="remote call inside @Transactional, connection pool exhausted",
        dia=dia_8,
        steps=[
            "A transaction holds a <b>pooled DB connection</b> (and locks). A remote call inside it ties them up for the latency of <u>someone else's</u> service.",
            "<b>Validate and price first</b> (customer exists; unit prices come from the catalogue, a client never states a price), then <b>OrderService.place</b> does the single atomic write.",
            "The two reads are independent: fan out with <b>CompletableFuture</b> on a dedicated executor and join. Cost = the slower call.",
            "A remote failure becomes one clear 503 before anything was written, so there is nothing to roll back.",
        ],
        code=["// OrderPlacementService (no transaction open here)",
              "var customerExists = submitRemote(() -> onContext(ctx,",
              "    () -> remote(\"customer service\", () -> customers.exists(id))));",
              "var priceLookup = submitRemote(() -> onContext(ctx,",
              "    () -> remote(\"catalogue\", () -> catalog.lookup(ids))));",
              "if (!join(customerExists)) throw new UnprocessableEntityException(...);",
              "return orderService.place(...);   // @Transactional, short"],
        cost="the check-then-write gap", twins="saga, outbox",
        trap="There is a gap between pricing and commit: the price can change in between. The order stores the price it was given and the saga re-checks stock; do not hold a transaction open to 'fix' it.",
    ),
]


# ---- the circuit-breaker numbers: a small model of Resilience4j's count-based window, checked against the real library
# (CbTrace.java run on Resilience4j 2.1.0 with the gateway's default config: window 10, minimum 5 calls, 50 %, 30 s wait, 5 probes)
def _breaker_closed_to_open(outcomes, window=10, minimum=5, threshold=50):
    """state after each call while the breaker is CLOSED: the rate is only evaluated once `minimum` calls are in the window"""
    buf, state, trace = [], "CLOSED", []
    for ok in outcomes:
        if state == "OPEN":
            break
        buf = (buf + [ok])[-window:]
        if len(buf) >= minimum and 100 * buf.count(False) / len(buf) >= threshold:
            state = "OPEN"
        trace.append(state)
    return trace


assert _breaker_closed_to_open([True, True, False, False, False]) == ["CLOSED"] * 4 + ["OPEN"]      # card 1: S S F F F -> OPEN, 3 of 5 = 60 %
assert _breaker_closed_to_open([True, True, False, False]) == ["CLOSED"] * 4                          # card 1: 2 of 4 = 50 % but only 4 calls: not evaluated
assert 100 * 3 / 5 == 60 and 100 * 2 / 4 == 50
assert [4 / 5 * 100 < 100, 5 / 5 * 100 >= 100] == [True, True]                                        # card 1 trap: slowCallRateThreshold 100 needs 5 of 5 slow
assert 10 > 9 > 8 > 1.5                                                                                # card 2: the timeouts nest from the edge inward
