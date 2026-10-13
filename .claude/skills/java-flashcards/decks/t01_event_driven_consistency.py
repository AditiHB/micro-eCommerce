"""Deck: topic 01, Event-driven consistency, 8 cards. Grounded in this repo's outbox / inbox / Kafka code (docs/interview/microservices/05, 06 and kafka/).
exec()d inside gen_flash.py: every engine helper (Dia, canvas, flow, cells, pointer, note, box_text, ...) is in scope."""

DECK_TITLE = "Event-driven consistency"
DECK_LABEL = "Event-driven consistency"
CHECK_QS = [
    "What can <u>fail</u> between two steps: crash, duplicate, reorder?",
    "Which guarantee do I really get: at-most, at-least or exactly-once?",
    "Where is the <u>state</u> that makes it safe (table, key, offset)?",
    "What does it cost, and when would I not use it?",
]

RED_T, BLUE_T, YEL = "#fde3df", "#e3edfa", "#fff3a8"


# ------------------------------------------------------------------ scenario pictures (front)
def ex_1():
    d = canvas(54)
    flow(d, ["save order", "pod killed", "kafka.send()"], 10, fills={0: GREENISH, 1: RED_T}, colors={1: RED, 2: GREY}, dashed={2},
         under={0: ("committed", GREEN), 1: ("crash here", RED), 2: ("never runs", RED)}, gap=26)
    return d


def ex_2():
    d = canvas(66)
    box_text(d, 8, 6, "e-17  (offset 4)", 10.5, 22, INK, BLUE_T)
    box_text(d, 8, 38, "e-17  (offset 5)", 10.5, 22, RED, RED_T)
    box_text(d, 232, 22, "payment", 11, 22, INK, GREENISH)
    d.add(arrow(140, 17, 228, 30, 4, GREY, 1.5, 4))
    d.add(arrow(140, 49, 228, 36, -4, RED, 1.5, 4))
    d.add(svg_text(150, 63, "charged twice?", 10, RED))
    return d


def ex_3():
    d = canvas(66)
    flow(d, ["2 pods", "10 pods"], 8, fills={1: YEL}, gap=40, size=10.5)
    d.add(svg_text(8, 52, "throughput barely moves", 10.5, RED))
    d.add(svg_text(8, 66, "topic order-created: 3 partitions", 10.5, PENCIL))
    d.h = 72
    return d


def ex_4():
    d = canvas(66)
    flow(d, ["msg A", "msg B (bad)", "msg C", "msg D"], 8, fills={1: RED_T}, colors={1: RED}, size=10, gap=14)
    d.add(arrow(130, 44, 130, 33, 0, RED, 1.6, 4))
    d.add(svg_text(130, 56, "throws every time, lag grows", 10, RED, "middle"))
    d.h = 70
    return d


def ex_5():
    d = canvas(66)
    flow(d, ["order", "stock held", "payment"], 8, fills={0: GREENISH, 1: GREENISH, 2: RED_T}, colors={2: RED}, gap=26,
         under={1: ("reserved", GREEN), 2: ("card declined", RED)})
    d.add(svg_text(8, 58, "3 services, 3 databases, no shared transaction", 10, PENCIL))
    d.h = 64
    return d


def ex_6():
    d = canvas(66)
    flow(d, ["producer", "leader", "replica"], 8, fills={1: YEL}, colors={2: GREY}, dashed={2}, gap=30,
         under={1: ("acked", GREEN), 2: ("copy missing", RED)})
    d.add(svg_text(8, 58, "leader dies: the acked record is gone", 10, RED))
    d.h = 64
    return d


def ex_7():
    d = canvas(66)
    flow(d, ["relay 1", "relay 2", "relay 3", "relay 4", "relay 5"], 8, size=9.5, gap=8, fills={0: BLUE_T, 1: BLUE_T, 2: BLUE_T, 3: BLUE_T, 4: BLUE_T})
    d.add(svg_text(8, 52, "all poll the same outbox_event table", 10.5, PENCIL))
    d.add(svg_text(8, 66, "same row twice? one order out of order?", 10.5, RED))
    d.h = 72
    return d


def ex_8():
    d = canvas(66)
    flow(d, ["inventory v2", "payment v1"], 8, fills={0: GREENISH, 1: BLUE_T}, gap=40, under={0: ("new field", GREEN), 1: ("last week's build", RED)})
    d.add(svg_text(8, 58, "one topic, two deployed versions", 10.5, PENCIL))
    d.h = 64
    return d


# ------------------------------------------------------------------ explanation pictures (back)
def dia_1():
    d = canvas(118)
    d.add(rect(8, 18, DW - 18, 38, TEAL, 1.6, 1.2, .6, extra='stroke-dasharray="5 4"'))
    d.add(svg_text(12, 13, "ONE local database transaction: no 2PC needed", 10, TEAL))
    flow(d, ["orders row", "outbox_event row", "COMMIT"], 26, x=14, fills={0: GREENISH, 1: GREENISH, 2: YEL}, gap=16, fit_to=DW - 6)
    d.add(svg_text(8, 76, "later, a separate step:", 10, PENCIL))
    flow(d, ["relay", "Kafka", "PUBLISHED"], 82, fills={2: GREENISH}, gap=34,
         under={0: ("every 250 ms", PENCIL), 1: ("acks=all", PENCIL), 2: ("only after ack", GREEN)})
    return d


def dia_2():
    d = canvas(118, 112)
    d.add(svg_text(8, 14, "1st delivery", 10, GREEN))
    flow(d, ["e-17", "INSERT claim", "1 row", "charge"], 20, fills={2: GREENISH, 3: GREENISH}, size=10, gap=16)
    d.add(svg_text(8, 66, "2nd delivery (duplicate)", 10, RED))
    flow(d, ["e-17", "INSERT claim", "0 rows", "skip"], 72, fills={2: RED_T, 3: RED_T}, colors={2: RED, 3: RED}, size=10, gap=16)
    note(d, 8, "claim + work: ONE transaction", 10.5, INK, hl="#fff176")
    return d


def dia_3():
    d = Dia(30)
    g = cells(d, ["p0", "p1", "p2", "-", "-", "-"], fills={0: GREENISH, 1: GREENISH, 2: GREENISH, 3: "#eeeeee", 4: "#eeeeee", 5: "#eeeeee"},
              colors={3: GREY, 4: GREY, 5: GREY}, idx=False, gap=4)
    tag_above(d, g, 0, "c1", GREEN)
    tag_above(d, g, 1, "c2", GREEN)
    tag_above(d, g, 2, "c3", GREEN)
    d.add(svg_text(cx_of(g, 4), 16, "idle pods", 10, RED, "middle"))
    d.noteY = d.top + CH_CELL + 26
    note(d, 8, "3 partitions: at most 3 busy consumers", 10.5, INK, hl="#fff176")
    d.add(svg_text(DW - 8, d.noteY + 16, "same key = same partition = in order", 9.5, GREEN, "end"))
    d.h = d.noteY + 26
    return d


def dia_4():
    d = canvas(122, 116)
    flow(d, ["try 1", "+1 s", "+2 s", "+4 s", "+8 s"], 10, size=10, gap=10, fills={0: RED_T, 1: RED_T, 2: RED_T, 3: RED_T, 4: RED_T}, colors={0: RED})
    flow(d, ["topic-dlq (14 days)", "parked in a table", "operator replays"], 62, size=9.5, gap=12, fills={0: YEL})
    d.add(svg_text(8, 50, "5 attempts = 15 s, then:", 10, PENCIL))
    note(d, 8, "NonRetryable: straight to the DLQ", 10.5, INK, hl="#fff176")
    return d


def dia_5():
    d = canvas(122, 116)
    flow(d, ["order.created", "reserved", "payment.failed"], 8, size=10, gap=14, fills={0: BLUE_T, 1: GREENISH, 2: RED_T}, colors={2: RED})
    flow(d, ["order CANCELLED", "order.cancelled", "stock released"], 58, size=10, gap=14, fills={0: RED_T, 1: YEL, 2: GREENISH})
    d.add(svg_text(8, 98, "one cancel path; refund too if money was taken", 10, PENCIL))
    note(d, 8, "no reply in 5 min: the reaper cancels it", 10.5, INK, hl="#fff176")
    return d


def dia_6():
    d = canvas(122, 116)
    d.add(svg_text(8, 13, "acks=all", 10, GREEN))
    flow(d, ["producer", "leader", "2 of 3 replicas"], 18, fills={1: GREENISH, 2: GREENISH}, size=10, gap=18)
    d.add(svg_text(8, 62, "idempotent producer: (pid, seq)", 10, GREEN))
    flow(d, ["seq 7", "retry seq 7", "log keeps one"], 68, fills={1: RED_T, 2: GREENISH}, colors={1: RED}, size=10, gap=18)
    note(d, 8, "new relay pod = new pid: dup is possible", 10.5, INK, hl="#fff176")
    return d


def dia_7():
    d = Dia(30)
    g = cells(d, ["e1", "e2", "e3"], fills={0: GREENISH, 1: RED_T, 2: BLUE_T}, colors={1: RED}, idx=False, gap=14, maxpitch=60)
    tag_above(d, g, 0, "R1 locks", GREEN)
    tag_above(d, g, 2, "R2 takes", TEAL)
    d.add(svg_text(cx_of(g, 0), d.top + 40, "order 7", 9.5, PENCIL, "middle"))
    d.add(svg_text(cx_of(g, 1), d.top + 40, "order 7", 9.5, PENCIL, "middle"))
    d.add(svg_text(cx_of(g, 2), d.top + 40, "order 9", 9.5, PENCIL, "middle"))
    d.add(svg_text(cx_of(g, 1), d.top + 54, "waits for e1", 9.5, RED, "middle"))
    note(d, 8, "SKIP LOCKED + NOT EXISTS earlier same aggregate", 10, INK, hl="#fff176")
    return d


def dia_8():
    d = canvas(118, 112)
    flow(d, ["order.created v1", "+ optional field", "= still v1"], 10, fills={0: BLUE_T, 1: GREENISH, 2: GREENISH}, size=10, gap=14)
    d.add(svg_text(8, 52, "breaking change:", 10, RED))
    flow(d, ["order.created", "order.created.v2"], 58, fills={0: BLUE_T, 1: YEL}, size=10, gap=30, under={1: ("side by side", PENCIL)})
    note(d, 8, "wire = logical name, never a Java class", 10.5, INK, hl="#fff176")
    return d


# ------------------------------------------------------------------ the cards
CARDS = [
    dict(
        n=1, tier="MUST", ref="EVENTS", ask="DEBUG",
        title="The order nobody heard about", short="Outbox: save + announce",
        q="order-service saves the order, then calls <b>kafka.send()</b>. The pod is killed between the two: the order exists, payment never hears about it. How do you make <u>save</u> and <u>announce</u> all-or-nothing without a distributed transaction?",
        ex_dia=ex_1, ex=[("DB commit OK, then the pod dies", "no event on the topic", "a dual write")],
        kind="TRANSACTIONAL OUTBOX  |  one local tx", keep="keep: an outbox table next to the order table",
        rule="write the event in the SAME transaction; a relay sends it later",
        spot="dual write: DB + broker, a crash window, events must not be lost",
        dia=dia_1,
        steps=[
            "<b class=g>WRITE</b>: <b>EventPublisher.publish()</b> inserts an <b>outbox_event</b> row inside the caller's transaction. <b>Propagation.MANDATORY</b>: outside one it fails loudly.",
            "<b class=t>RELAY</b>: a poller (every 250 ms) locks due rows and sends them with <b>acks=all</b>.",
            "Mark the row <b>PUBLISHED</b> only after the broker acks. A crash leaves it PENDING and it is sent again.",
        ],
        code=["// EventPublisher.publish (Propagation.MANDATORY)",
              "outbox.save(OutboxEvent.builder()",
              "    .eventId(e.getEventId()).topic(topic)",
              "    .messageKey(e.getAggregateId())",
              "    .payload(json).status(PENDING).build());",
              "// OutboxRelay.deliver: own tx, rows locked",
              "kafka.send(toRecord(ev)).get(10, SECONDS);",
              "ev.setStatus(PUBLISHED);   // only after the ack"],
        cost="+1 table, +1 poller; adds ~250 ms", twins="CDC (Debezium), inbox",
        trap="The relay can send <b>twice</b> (crash after the ack, before the row is marked). Outbox is <u>at-least-once</u>, never exactly-once: every consumer must de-duplicate.",
    ),
    dict(
        n=2, tier="MUST", ref="EVENTS", ask="DEBUG",
        title="Charged twice", short="Idempotent consumer (inbox)",
        q="The relay crashed right after Kafka acked and before it marked the row PUBLISHED, so the event is sent again and <b>payment-service</b> receives it twice. How do you charge the customer exactly once?",
        ex_dia=ex_2, ex=[("same event id, delivered twice", "two charges", "at-least-once delivery")],
        kind="IDEMPOTENT CONSUMER  |  inbox claim", keep="keep: processed_events(consumer, event_id)",
        rule="claim the id in the SAME transaction as the effect",
        spot="at-least-once, redelivery, retries, replay from a DLQ",
        dia=dia_2,
        steps=[
            "First act of the handler: <b>inbox.firstDelivery(consumer, eventId)</b> is an <b>INSERT ... ON CONFLICT DO NOTHING</b>: 1 row or 0.",
            "<b class=g>1</b>: first time. Do the work (charge, save the payment, queue the next event in the outbox) in this same transaction.",
            "<b class=r>0</b>: seen before, return. The duplicate is a logged no-op.",
            "A failure rolls the claim back <u>with</u> the work, so a retry is not mistaken for a duplicate.",
        ],
        code=["@Transactional",
              "void onInventoryReserved(InventoryReservedEvent e) {",
              "  if (!inbox.firstDelivery(CONSUMER, e.getEventId()))",
              "      return;                // duplicate",
              "  if (payments.findByOrderId(e.getOrderId())",
              "          .isPresent()) return;  // backstop",
              "  paymentService.charge(e.getOrderId(), amount, cur);",
              "}"],
        cost="1 insert per message; purged after 14 days", twins="unique payment.order_id",
        trap="Claim <u>outside</u> the work's transaction and a crash right after it loses the event for good. And retention (14 days) must outlast any redelivery: topic retention plus DLQ replays.",
    ),
    dict(
        n=3, tier="MUST", ref="KAFKA", ask="TRADE-OFF",
        title="Ten pods, three partitions", short="Partitions cap parallelism",
        q="Order events are slow to process, so you scale <b>inventory-service</b> from 2 to 10 pods. Throughput barely moves. Why? And what decides which events stay in order?",
        ex_dia=ex_3, ex=[("pods 2 -> 10", "same throughput", "3 partitions")],
        kind="PARTITION  |  unit of parallelism and order", keep="keep: key = aggregate id (the order id)",
        rule="active consumers in a group <= partitions",
        spot="scale consumers, ordering per entity, consumer lag",
        dia=dia_3,
        steps=[
            "A partition is read by <b>one</b> consumer of a group, so 3 partitions means at most <b class=r>3</b> busy consumers. The rest idle (useful only for failover).",
            "Same key goes to the same partition: events of <b>one order stay in order</b>. Across orders there is no order.",
            "Each pod also runs <b>3</b> listener threads: 4 pods = 12 threads for 3 partitions.",
            "Adding partitions later changes the key-to-partition map and breaks per-key order during the change.",
        ],
        code=["new NewTopic(topic, partitions /*3*/, replicationFactor)",
              "factory.setConcurrency(3);      // threads per pod",
              "new ProducerRecord<>(topic, null,",
              "    event.getMessageKey(),      // = aggregate id",
              "    event.getPayload());"],
        cost="capped by the partition count", twins="consumer lag, rebalance",
        trap="Size partitions for peak load <u>up front</u> (target rate / per-consumer rate, plus headroom). Scaling pods past the partition count buys failover only.",
    ),
    dict(
        n=4, tier="CORE", ref="KAFKA", ask="DEBUG",
        title="One bad message", short="Retry, then dead-letter",
        q="One malformed <b>inventory.reserved</b> record throws every time. The consumer keeps retrying it, its partition is stuck and the lag climbs. What should happen to it, and what should <u>never</u> be retried?",
        ex_dia=ex_4, ex=[("a poison record at offset 1", "offsets 2 and 3 wait", "head-of-line blocking")],
        kind="RETRY WITH BACKOFF, THEN DEAD-LETTER", keep="keep: <topic>-dlq + a parked-message table",
        rule="transient: retry; permanent: dead-letter at once",
        spot="poison message, stuck partition, lag, replay",
        dia=dia_4,
        steps=[
            "<b>Never catch-and-ack</b> in a listener: throw, and let the error handler decide.",
            "Transient (lock conflict, DB blip): exponential backoff <b>1 s, 2 s, 4 s, 8 s</b>, 5 attempts in all.",
            "Permanent (<b>NonRetryableEventException</b>, unreadable payload): to <b>topic-dlq</b> with no retries.",
            "The offset is committed after dead-lettering, so the partition moves on. An operator inspects and <b>replays</b>.",
        ],
        code=["var backOff = new ExponentialBackOffWithMaxRetries(4);",
              "backOff.setInitialInterval(1000);",
              "backOff.setMultiplier(2.0);",
              "backOff.setMaxInterval(30_000);",
              "var h = new DefaultErrorHandler(recoverer, backOff);",
              "h.addNotRetryableExceptions(",
              "    NonRetryableEventException.class);",
              "h.setCommitRecovered(true);"],
        cost="a retry blocks its partition for 15 s", twins="DeadLetterService.replay",
        trap="Blocking retries hold up every later event of that partition. Handlers must be idempotent (a retry can re-run them) and an unwatched DLQ is just a slower way to lose data: alert on it.",
    ),
    dict(
        n=5, tier="CORE", ref="SAGA", ask="DESIGN",
        title="Undo without a transaction", short="Choreographed saga",
        q="Placing an order touches order, inventory and payment, each with its own database. Payment declines the card after the stock was reserved. How does the system undo the reservation, and what if nobody ever answers?",
        ex_dia=ex_5, ex=[("payment.failed after stock reserved", "stock must be released", "no 2PC across services")],
        kind="SAGA  |  choreography + compensation", keep="keep: the order status as a state machine",
        rule="every step has an undo; nothing waits forever",
        spot="distributed transaction, rollback across services, long-running flow",
        dia=dia_5,
        steps=[
            "Forward: <b>order.created</b> then stock <b>reserved</b> then payment <b>captured</b>. Each service reacts to events; no central coordinator.",
            "A failure reaches the <b>order</b>, the only service that decides to cancel. It emits <b>order.cancelled</b>: inventory releases stock, payment refunds if it took money.",
            "Late or out-of-order events are expected: stock reserved for an already CANCELLED order re-announces the cancellation.",
            "A <b>deadline</b> reaper cancels orders stuck in flight longer than 5 minutes.",
        ],
        code=["case CANCELLED -> {",
              "  log.warn(\"Stock reserved after the order was \"",
              "         + \"cancelled - asking inventory to release\");",
              "  orderService.announceCancellation(order,",
              "      \"Stock reserved after cancel\", event.getEventId());",
              "}",
              "// OrderSagaDeadline: every 30 s, cancel open",
              "// orders not updated for 5 min (sagaTimeout)"],
        cost="eventual consistency; compensations to write", twins="orchestration (a coordinator)",
        trap="Compensations must be idempotent (they will run twice) and a saga with no deadline leaves a lost event as a PENDING order for ever. Undo is a business action (a refund), not a rollback.",
    ),
    dict(
        n=6, tier="CORE", ref="KAFKA", ask="EXPLAIN",
        title="What acks=all really buys", short="Producer safety settings",
        q="What do <b>acks=all</b> and <b>enable.idempotence=true</b> each protect against? And why does a producer with both still not give you end-to-end exactly-once?",
        ex_dia=ex_6, ex=[("leader acks, then dies", "acked record lost", "acks=1 or RF 1")],
        kind="PRODUCER SAFETY  |  durability + no retry dupes", keep="keep: RF 3 + min.insync.replicas 2",
        rule="acks=all waits for the in-sync replicas",
        spot="lost message, duplicate on retry, exactly-once",
        dia=dia_6,
        steps=[
            "<b class=g>acks=all</b>: the leader answers when every in-sync replica has the record. With <b>min.insync.replicas=2</b> (RF 3) a write needs 2 copies or it fails.",
            "<b class=g>enable.idempotence</b>: the broker drops a retried send by (producer id, sequence): no duplicate or reorder <u>from retries</u>.",
            "<b class=r>Gap</b>: a restarted relay is a new producer with a new id, so the same outbox row sent twice is two records.",
            "End to end = outbox (no lost event) + idempotent consumer (no double effect).",
        ],
        code=["props.put(ACKS_CONFIG, \"all\");",
              "props.put(ENABLE_IDEMPOTENCE_CONFIG, true);",
              "props.put(DELIVERY_TIMEOUT_MS_CONFIG, 15000);",
              "// repo defaults (dev): RF 1, min ISR 1",
              "// prod: RF 3 and kafka.event.min-insync-replicas=2"],
        cost="one extra replica round trip per send", twins="KafkaEventConfig:146",
        trap="Durability is the PAIR acks=all + min ISR. With RF 1 or min ISR 1, acks=all is no safer than acks=1, and the dev defaults here are exactly that.",
    ),
    dict(
        n=7, tier="STRETCH", ref="EVENTS", ask="DESIGN",
        title="Five relays, one table", short="SKIP LOCKED + ordering",
        q="Five pods all run the outbox relay. What stops two of them sending the same row, and how do the events of one order still leave in order?",
        ex_dia=ex_7, ex=[("e1, e2 of order 7 pending", "e2 sent before e1?", "two relays, no coordination")],
        kind="ROW LOCKS  |  SKIP LOCKED + NOT EXISTS", keep="keep: locks held for the batch transaction",
        rule="skip locked rows; wait for earlier rows of the same aggregate",
        spot="competing pollers, queue in a database table, per-key ordering",
        dia=dia_7,
        steps=[
            "One poll = one transaction: <b>SELECT ... FOR UPDATE SKIP LOCKED</b> takes up to 50 due rows. A row another relay holds is <u>skipped</u>, not waited for.",
            "<b>NOT EXISTS</b> an earlier PENDING row of the same aggregate: e2 waits until e1 is PUBLISHED. One order's events leave in order.",
            "Different orders flow in parallel. A failed row backs off 1 s up to 1 min and is DEAD after 50 attempts, with an alert.",
        ],
        code=["SELECT o.* FROM outbox_event o",
              "WHERE o.status = 'PENDING' AND o.next_attempt_at <= :now",
              "  AND NOT EXISTS (SELECT 1 FROM outbox_event e",
              "      WHERE e.aggregate_id = o.aggregate_id",
              "        AND e.status = 'PENDING' AND e.id < o.id)",
              "ORDER BY o.id LIMIT :limit",
              "FOR UPDATE SKIP LOCKED"],
        cost="a hot order moves 1 event per poll", twins="CDC, Debezium outbox router",
        trap="The locks are held until every send is acked or times out (10 s each): a slow broker pins the rows and idles the other relays. Keep the send timeout short and watch the pending-age metric.",
    ),
    dict(
        n=8, tier="STRETCH", ref="EVENTS", ask="DESIGN",
        title="Changing an event in flight", short="Event schema evolution",
        q="inventory-service wants to add a field to <b>inventory.reserved</b> while payment-service still runs last week's build. How do you change an event without a big-bang deploy, and why is a Java class name in the message header a bad idea?",
        ex_dia=ex_8, ex=[("add an optional field", "old consumers ignore it", "compatible", "g"), ("rename or retype a field", "old consumers break", "needs a .v2 type")],
        kind="SCHEMA = CONTRACT  |  add-only", keep="keep: a logical type name per event",
        rule="only ADD optional fields; anything else is a new .vN type",
        spot="rolling deploy, producer and consumer on different versions",
        dia=dia_8,
        steps=[
            "The wire carries a <b>logical type</b> (<b>order.created</b>), never a Java class name: producers and consumers can rename classes freely.",
            "<b>@EventSchema(type, topic, version)</b> declares the contract; <b>EventCatalog</b> scans them for the type header, the consumer allow-list and the topic list.",
            "Consumers build only types in that allow-list (one trusted package): a forged header deserializes nothing.",
            "Breaking change: publish <b>order.created.v2</b> beside v1, migrate consumers, retire v1. <b>EventContractTest</b> pins a golden JSON per type.",
        ],
        code=["@EventSchema(type = \"order.created\",",
              "             topic = Topics.ORDER_CREATED)  // version = 1",
              "class OrderCreatedEvent extends DomainEvent {...}",
              "props.put(JsonDeserializer.TYPE_MAPPINGS,",
              "          EventCatalog.typeMapping());",
              "props.put(JsonDeserializer.TRUSTED_PACKAGES,",
              "          \"com.ecommerce.common.events\");"],
        cost="a golden file per type; discipline", twins="schema registry (Avro)",
        trap="Additive-only is a rule you enforce with a test, not a habit. A consumer that fails on an unknown field breaks the rule from the other side: ignore unknown properties.",
    ),
]

# ---- every number printed on a card is checked against this repo's defaults and simple arithmetic
BACKOFF_S = [1.0 * 2.0 ** k for k in range(4)]                      # ExponentialBackOffWithMaxRetries(max-attempts 5 - 1 retries), initial 1 s, x2, cap 30 s
assert BACKOFF_S == [1, 2, 4, 8] and sum(BACKOFF_S) == 15           # card 4: "5 attempts = 15 s"
assert 5 - 1 == 4                                                   # retryMaxAttempts 5 -> 4 backoff steps
assert 4 * 3 == 12 and 12 - 3 == 9                                  # card 3: 4 pods x 3 threads, 3 partitions -> 9 idle threads
assert 300 // 60 == 5                                               # card 5: sagaTimeout PT5M is 5 minutes, reaper every 30 s
assert 14 * 24 * 3600 * 1000 == 1209600000                          # card 4: dlq-retention-ms default is 14 days
