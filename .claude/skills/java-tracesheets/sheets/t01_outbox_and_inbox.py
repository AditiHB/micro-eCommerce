"""Trace sheet spec: topic 01, Outbox + inbox (the relay crashes, an event is delivered twice, the consumer charges once).

The trace is NOT typed by hand: it runs the real SQL semantics in SQLite (INSERT ... ON CONFLICT DO NOTHING for the inbox claim, a real ROLLBACK for the failed
attempt) and asserts the outcome against an independent count. Repo: common/.../outbox/OutboxRelay.java, common/.../inbox/InboxService.java,
services/payment-service/.../PaymentSagaHandler.java."""
import sqlite3


def new_db():
    db = sqlite3.connect(":memory:", isolation_level=None)          # autocommit: BEGIN / COMMIT / ROLLBACK are explicit, like a real transaction
    db.executescript("""
        CREATE TABLE outbox_event (event_id TEXT PRIMARY KEY, status TEXT NOT NULL);
        CREATE TABLE processed_events (consumer TEXT NOT NULL, event_id TEXT NOT NULL, PRIMARY KEY (consumer, event_id));
        CREATE TABLE payment (order_id INTEGER PRIMARY KEY, amount INTEGER NOT NULL);
        INSERT INTO outbox_event VALUES ('e1', 'PENDING');           -- written with the business change, in the same transaction
    """)
    return db


def relay(db, topic, crash_before_commit):
    """one relay poll: lock the PENDING row, send, wait for the ack, mark PUBLISHED, COMMIT. A crash before COMMIT rolls the status back."""
    db.execute("BEGIN")
    row = db.execute("SELECT event_id FROM outbox_event WHERE status = 'PENDING' ORDER BY rowid LIMIT 1").fetchone()
    topic.append(row[0])                                                 # the broker has it (acked)
    db.execute("UPDATE outbox_event SET status = 'PUBLISHED' WHERE event_id = ?", row)
    db.execute("ROLLBACK" if crash_before_commit else "COMMIT")


def consume(db, event, fail=False):
    """PaymentSagaHandler.onInventoryReserved: ONE transaction = claim + charge. Returns the claim result (1 = first delivery, 0 = duplicate)."""
    db.execute("BEGIN")
    claim = db.execute("INSERT INTO processed_events VALUES ('payment-group', ?) ON CONFLICT (consumer, event_id) DO NOTHING", (event,)).rowcount
    if claim == 1:
        if db.execute("SELECT 1 FROM payment WHERE order_id = 7").fetchone() is None:
            db.execute("INSERT INTO payment VALUES (7, 50)")
        if fail:
            db.execute("ROLLBACK")                                       # the claim row goes with the failed work
            return claim
    db.execute("COMMIT")
    return claim


def strip(db):
    """the one-line picture of the database: outbox status, inbox row, payment row"""
    out = [(f"outbox {e}:{s}", "g" if s == "PUBLISHED" else "y") for e, s in db.execute("SELECT event_id, status FROM outbox_event")]
    inbox = [(f"inbox {e}", "t") for _, e in db.execute("SELECT consumer, event_id FROM processed_events")] or ["inbox -"]
    pay = [("pay o7: 50 EUR", "g") for _ in db.execute("SELECT * FROM payment")] or ["pay -"]
    return [("DB", out + inbox + pay)]


# ------------------------------------------------------------------ worked trace: the relay dies after the ack
db, topic = new_db(), []
w = []
relay(db, topic, crash_before_commit=True)
w.append(dict(cells=["e1", "-", "-"], hi={0: "g"}, state=strip(db), vars={"acked": "yes", "marked": "no"},
              note="The relay sent e1 (offset 0), the broker acked, then the pod died before COMMIT. The row is still PENDING."))
assert topic == ["e1"] and db.execute("SELECT status FROM outbox_event").fetchone()[0] == "PENDING"

relay(db, topic, crash_before_commit=False)
w.append(dict(cells=["e1", "e1", "-"], hi={1: "r"}, state=strip(db), vars={"marked": "yes", "copies": 2},
              note="After the restart the row is still PENDING, so e1 is sent again (offset 1) and marked PUBLISHED. At-least-once: 2 copies."))
assert topic == ["e1", "e1"] and db.execute("SELECT status FROM outbox_event").fetchone()[0] == "PUBLISHED"

claim_1 = consume(db, topic[0])
w.append(dict(cells=["e1", "e1", "-"], hi={0: "g"}, ptr={"consumer": 0}, state=strip(db), vars={"claim": claim_1},
              note="claim = 1 row: first delivery. Charge 50 EUR. The inbox row and the payment commit in ONE transaction."))
assert claim_1 == 1 and db.execute("SELECT COUNT(*) FROM payment").fetchone()[0] == 1

claim_2 = consume(db, topic[1])
w.append(dict(cells=["e1", "e1", "-"], hi={1: "r"}, ptr={"consumer": 1}, state=strip(db), vars={"claim": claim_2},
              note="claim = 0 rows (ON CONFLICT DO NOTHING): seen before. Skip it: no second charge."))
assert claim_2 == 0 and db.execute("SELECT COUNT(*) FROM payment").fetchone()[0] == 1

w.append(dict(cells=["e1", "e1", "-"], hi={0: "g", 1: "g"}, ptr={"next offset": 2}, state=strip(db), vars={"payments": db.execute("SELECT COUNT(*) FROM payment").fetchone()[0]},
              note="Both records are acked. Two copies on the topic, one charge in the database."))
assert len(w) == 5 and len(topic) == 2 and db.execute("SELECT COUNT(*) FROM payment").fetchone()[0] == len(set(topic))      # charges == distinct events

# ------------------------------------------------------------------ practice trace: the first handler attempt fails
db, topic = new_db(), []
relay(db, topic, crash_before_commit=False)
relay_dup = ["e1"]                                                       # a duplicate that was already on the topic (an earlier relay crash)
topic += relay_dup
p = []
c1 = consume(db, topic[0], fail=True)
p.append(dict(ptr={"consumer": 0}, state=strip(db), vars={"claim": c1, "committed": "no"}, key="claim = 1, the charge throws, ROLLBACK: the claim row is gone too.",
              note="claim = 1, then the charge throws (gateway timeout). ROLLBACK takes the claim row with it: nothing is left behind."))
assert c1 == 1 and db.execute("SELECT COUNT(*) FROM processed_events").fetchone()[0] == 0 and db.execute("SELECT COUNT(*) FROM payment").fetchone()[0] == 0

p.append(dict(ptr={"consumer": 0}, state=strip(db), vars={"claim": "wait", "committed": "no"}, key="Backoff 1 s, then the same record is redelivered; the DB is unchanged.",
              note="The error handler waits 1 s (backoff) and redelivers the same record. Offset 0 is not acked and the partition waits."))

c2 = consume(db, topic[0])
p.append(dict(ptr={"consumer": 0}, state=strip(db), vars={"claim": c2, "committed": "yes"}, key="Retry: claim = 1 again, the charge works, COMMIT: one payment.",
              note="Redelivery: the claim row is free again, so claim = 1. The charge works and the transaction commits."))
assert c2 == 1 and db.execute("SELECT COUNT(*) FROM payment").fetchone()[0] == 1

c3 = consume(db, topic[1])
p.append(dict(ptr={"consumer": 1}, state=strip(db), vars={"claim": c3}, key="Offset 1 (the old duplicate): claim = 0 rows, skip it.",
              note="Offset 1 is the old duplicate of e1. claim = 0 rows, so it is skipped. Still one charge."))
assert c3 == 0 and db.execute("SELECT COUNT(*) FROM payment").fetchone()[0] == 1

p.append(dict(ptr={"next offset": 2}, state=strip(db), vars={"payments": 1}, key="Both offsets acked: 1 charge, no claim left from the failed try.",
              note="Both offsets are acked. A failed attempt left no claim behind, so the retry was not mistaken for a duplicate."))
assert len(p) == 5

SPEC = dict(
    n=1, name="Outbox and inbox: effectively once",
    card=dict(
        mantra="Write the event WITH the change. Send it at least once. Claim it before you act. Together that is effectively once.",
        recipe=["BEGIN  (business transaction)",
                "g|  save the change",
                "g|  INSERT outbox_event  PENDING",
                "i|COMMIT   (both, or neither)",
                "t|relay: lock rows, send, wait ack",
                "t|then mark the row PUBLISHED",
                "r|consumer: INSERT claim ... DO NOTHING",
                "r|0 rows = duplicate: return"],
        cost="Cost: 2 tables, ~250 ms, 1 insert per message.",
        questions=["Can the event be lost between save and send?",
                   "Can it arrive twice, and who notices?",
                   "Is the claim in the same transaction as the effect?"],
        triggers=["save, then publish", "duplicate delivery", "exactly once?", "lost after a crash"],
        donts=["need the answer now: call the service", "one database only: one transaction"],
        bugs=["Sending inside the transaction: a rollback still sent it.",
              "Claim outside the effect's transaction: a crash loses the event.",
              "Acking before the commit in the consumer: a crash loses it.",
              "Dedupe on a payload hash: two real orders collide."],
    ),
    worked=dict(
        title="Outbox and inbox", subtitle="the relay crashes, the event is sent twice",
        input_label="e1 = inventory.reserved, order 7, 50 EUR      topic offsets 0 1 2",
        cells=["-", "-", "-"], steps=w, answer="charged once (2 copies, 1 payment row)"),
    practice=dict(
        title="Outbox and inbox", subtitle="the first handler attempt fails",
        input_label="topic: e1 at offset 0 and again at 1      charge throws once",
        cells=["e1", "e1", "-"], steps=p, answer="charged once (the failed try left no claim)"),
    say=[("Context", "who consumes this event, and can the business live with a lost one or a double one?"),
         ("Naive version", "save, then kafka.send(): a crash between the two loses the event; send first and a rollback leaks it."),
         ("The mechanism", "an outbox row in the same transaction, a relay with SKIP LOCKED and acks=all, an inbox claim in the consumer."),
         ("Walk a failure", "the relay dies after the ack: the row stays PENDING, e1 is sent twice, the second claim returns 0 rows and is skipped."),
         ("Trade-offs", "at-least-once plus idempotency, not exactly-once; 2 tables and ~250 ms; CDC (Debezium) removes the poller but adds a platform.")],
    ladder=[("M", "Why can't I just call kafka.send() after saving the order?"),
            ("M", "What does the outbox pattern guarantee, and what does it not?"),
            ("M", "How does the consumer avoid charging twice?"),
            ("C", "Why must the inbox claim share the handler's transaction?"),
            ("C", "What does SKIP LOCKED buy when 5 relays run?"),
            ("C", "How do one order's events stay in order across relays?"),
            ("C", "What happens to an outbox row after 50 failed sends?"),
            ("C", "Polling relay or CDC: when do you switch?"),
            ("S", "How long must the inbox keep ids, and why 14 days?"),
            ("S", "What breaks if you add partitions to a live topic?"),
            ("S", "Design an effectively-once e-mail send (an external side effect)."),
            ("S", "Prove no event is lost when the broker is down for an hour.")],
)
