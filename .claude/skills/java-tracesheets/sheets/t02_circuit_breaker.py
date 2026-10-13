"""Trace sheet spec: topic 02, Circuit breaker with the gateway's settings (config-repo/api-gateway.yml: window 10, minimum 5 calls, 50 %, 30 s, 5 probes).

The trace is produced by a small model of Resilience4j's count-based window and asserted against what the REAL library (resilience4j-circuitbreaker 2.1.0,
configured exactly like the gateway's `default` config) printed for the same call sequences. The oracle lists below are copied from those runs
(CbTrace.java / CbPractice.java: state after every call, and REJECTED for calls made while OPEN)."""


class Breaker:
    def __init__(self, window=10, minimum=5, threshold=50, probes=5):
        self.window, self.minimum, self.threshold, self.probes = window, minimum, threshold, probes
        self.state, self.buf, self.half, self.expired = "CLOSED", [], [], False

    @staticmethod
    def rate(calls):
        return 100.0 * calls.count(False) / len(calls)

    def wait(self):
        assert self.state == "OPEN"
        self.expired = True                                   # the 30 s are over; the NEXT call is allowed through as the first probe

    def call(self, ok):
        """returns what the caller sees: 'S', 'F' or 'REJECTED'; self.state is the state AFTER the call"""
        if self.state == "OPEN":
            if not self.expired:
                return "REJECTED"
            self.state, self.half, self.expired = "HALF_OPEN", [], False
        if self.state == "HALF_OPEN":
            self.half.append(ok)
            if len(self.half) == self.probes:
                self.last_probe_rate = self.rate(self.half)
                self.state = "OPEN" if self.last_probe_rate >= self.threshold else "CLOSED"
                self.buf = []                                  # CLOSED again: a fresh, empty window
            return "S" if ok else "F"
        self.buf = (self.buf + [ok])[-self.window:]
        if len(self.buf) >= self.minimum and self.rate(self.buf) >= self.threshold:
            self.state = "OPEN"
        return "S" if ok else "F"

    def shown_rate(self):
        return f"{self.rate(self.buf):.0f}%" if len(self.buf) >= self.minimum else "n/a"


def window_cells(br, size=10):
    marks = ["S" if ok else "F" for ok in br.buf]
    return marks + [""] * (size - len(marks))


def run(br, plan):
    """plan: a list of 'S' / 'F' calls, or the string 'wait'; returns [(result, state after)]"""
    out = []
    for item in plan:
        if item == "wait":
            br.wait()
            continue
        out.append((br.call(item == "S"), br.state))
    return out


# ---- the oracle: what the real library printed (state after each call)
REAL_WORKED = [("S", "CLOSED"), ("S", "CLOSED"), ("F", "CLOSED"), ("F", "CLOSED"), ("F", "OPEN"), ("REJECTED", "OPEN"), ("REJECTED", "OPEN"),
               ("S", "HALF_OPEN"), ("S", "HALF_OPEN"), ("S", "HALF_OPEN"), ("F", "HALF_OPEN"), ("S", "CLOSED"), ("F", "CLOSED")]
REAL_PRACTICE = [("F", "CLOSED"), ("F", "CLOSED"), ("F", "CLOSED"), ("F", "CLOSED"), ("F", "OPEN"),
                 ("S", "HALF_OPEN"), ("S", "HALF_OPEN"), ("F", "HALF_OPEN"), ("F", "HALF_OPEN"), ("F", "OPEN"), ("REJECTED", "OPEN"), ("REJECTED", "OPEN"),
                 ("S", "HALF_OPEN"), ("S", "HALF_OPEN"), ("S", "HALF_OPEN"), ("S", "HALF_OPEN"), ("S", "CLOSED")]

# ------------------------------------------------------------------ worked trace
b = Breaker()
w = []
assert run(b, ["S", "S", "F", "F"]) == REAL_WORKED[:4]
w.append(dict(cells=window_cells(b), hi={2: "r", 3: "r"}, vars={"state": b.state, "rate": b.shown_rate()},
              note="4 calls, under the minimum of 5, so the failure rate is NOT evaluated (2 of 4 = 50 % does not count yet). Still CLOSED."))
assert b.state == "CLOSED" and b.shown_rate() == "n/a"

assert run(b, ["F"]) == REAL_WORKED[4:5]
w.append(dict(cells=window_cells(b), hi={2: "r", 3: "r", 4: "r"}, vars={"state": b.state, "rate": b.shown_rate()},
              note="5 calls now. 3 of 5 failed = 60 %, which is >= 50 %: the breaker OPENS."))
assert b.state == "OPEN" and b.shown_rate() == "60%"

assert run(b, ["S", "S"]) == REAL_WORKED[5:7]
w.append(dict(cells=window_cells(b), hi={2: "r", 3: "r", 4: "r"}, state=[("callers", [("503 Retry-After: 30", "r"), ("503 Retry-After: 30", "r")])],
              vars={"state": b.state, "rejected": 2},
              note="Two calls arrive while OPEN: rejected at once, no network call. The window is frozen. The gateway answers 503 with Retry-After: 30."))

b.wait()
assert run(b, ["S", "S", "S", "F", "S"]) == REAL_WORKED[7:12]
probe_cells = ["S", "S", "S", "F", "S", "", "", "", "", ""]
w.append(dict(cells=probe_cells, hi={3: "r"}, frame=(0, 4), vars={"state": b.state, "rate": f"{b.last_probe_rate:.0f}%"},
              note="After 30 s the next call is the first probe (HALF_OPEN). 5 probes: 1 of 5 failed = 20 %, under 50 %: CLOSED with an empty window."))
assert b.state == "CLOSED" and b.last_probe_rate == 20.0

assert run(b, ["F"]) == REAL_WORKED[12:13]
w.append(dict(cells=window_cells(b), hi={0: "r"}, vars={"state": b.state, "rate": b.shown_rate()},
              note="A fresh window: 1 call, under the minimum again. The old failures are forgotten."))
assert b.state == "CLOSED" and len(w) == 5

# ------------------------------------------------------------------ practice trace (HALF_OPEN fails, then recovers)
b = Breaker()
p = []
assert run(b, ["F"] * 5) == REAL_PRACTICE[:5]
p.append(dict(vars={"state": b.state, "rate": b.shown_rate()}, key="5 of 5 failed = 100 %: OPEN at the 5th call.",
              note="Five calls, all failed. At the fifth call the window holds 5 calls and 100 % failed. State?"))
assert b.state == "OPEN"

b.wait()
assert run(b, ["S", "S"]) == REAL_PRACTICE[5:7]
p.append(dict(vars={"state": b.state, "probes": "2 of 5"}, key="Probes 1 and 2 succeed: HALF_OPEN, 2 of 5 probes used, no verdict yet.",
              note="30 s later two calls get through as probes and both succeed. State? Is there a verdict yet?"))
assert b.state == "HALF_OPEN"

assert run(b, ["F", "F", "F"]) == REAL_PRACTICE[7:10]
p.append(dict(vars={"state": b.state, "failed": f"{b.last_probe_rate:.0f}%"}, key="3 of 5 probes failed = 60 %: OPEN again, and the 30 s clock restarts.",
              note="Probes 3, 4 and 5 fail. 3 of 5 probes failed. What is the failure rate, and what is the state now?"))
assert b.state == "OPEN" and b.last_probe_rate == 60.0

assert run(b, ["S", "S"]) == REAL_PRACTICE[10:12]
p.append(dict(state=[("callers", [])], vars={"state": b.state, "rejected": 2}, key="Two calls while OPEN: rejected at once, 503 + Retry-After: 30.",
              note="Two calls arrive right after. What do the callers see, and is the network touched?"))

b.wait()
assert run(b, ["S"] * 5) == REAL_PRACTICE[12:17]
p.append(dict(vars={"state": b.state, "failed": f"{b.last_probe_rate:.0f}%"}, key="5 of 5 probes succeed = 0 % failed: CLOSED with a fresh window.",
              note="30 s later five probes all succeed. Failure rate and state?"))
assert b.state == "CLOSED" and len(p) == 5

SPEC = dict(
    n=2, name="Circuit breaker: when it opens",
    card=dict(
        mantra="Count the last calls. Open when too many fail. Wait. Let a few probe. Then trust again, or not.",
        recipe=["g|CLOSED: call it, count it",
                "t|window 10 calls, judge from 5",
                "r|>= 50 % failed  ->  OPEN",
                "r|OPEN: reject, no network call",
                "i|after 30 s  ->  HALF_OPEN",
                "t|5 probes: < 50 % fail -> CLOSED",
                "r|else  ->  OPEN again",
                "p|# caller gets 503 + Retry-After: 30"],
        cost="Cost: a rejected call is free; a wrong threshold is not.",
        questions=["How many calls before it judges?",
                   "What does the caller see while OPEN?",
                   "Who learns the state: one pod, or all?"],
        triggers=["cascading failure", "slow dependency", "fail fast", "503 Retry-After"],
        donts=["a call that must never be skipped: queue it", "a one-off batch job: a plain retry is enough"],
        bugs=["Judging on 2 calls: one blip opens it (minimum calls).",
              "slowCallRateThreshold 100: only all-slow traffic opens it.",
              "A fallback that invents a success body: return an honest 503.",
              "State is per JVM: 10 pods hold 10 separate opinions."],
    ),
    worked=dict(
        title="Circuit breaker", subtitle="window 10, judge from 5 calls, open at 50 %",
        input_label="calls: S S F F F, 2 more while OPEN, 30 s, probes S S S F S, then F",
        cells=[""] * 10, steps=w, answer="OPEN at 3 of 5, CLOSED after 4 of 5 probes ok"),
    practice=dict(
        title="Circuit breaker", subtitle="the probes fail, then recover",
        input_label="F F F F F, 30 s, S S F F F, 2 calls, 30 s, S S S S S",
        cells=[""] * 10, steps=p, answer="OPEN, HALF_OPEN, OPEN again, then CLOSED"),
    say=[("Context", "which dependency, what is its SLA, and what should the caller get when it is down?"),
         ("Naive version", "no protection: threads pile up behind a slow service, then every caller times out and the failure spreads."),
         ("The mechanism", "a count-based window of 10, judged from 5 calls, open at 50 % failed, 30 s wait, 5 probes, next to a time limiter."),
         ("Walk a failure", "S S F F F opens it (60 %); calls are rejected without a network call; after 30 s five probes decide: under 50 % closes it."),
         ("Trade-offs", "state is per JVM, thresholds need tuning, and the fallback must be honest (503 + Retry-After), never an invented success.")],
    ladder=[("M", "What are the three states and what moves it between them?"),
            ("M", "Why does it not judge on the first 2 calls?"),
            ("M", "What does the caller see while the breaker is open?"),
            ("C", "How do the time limiter, the timeouts and the breaker nest?"),
            ("C", "What does slowCallRateThreshold: 100 actually do?"),
            ("C", "Why is retrying POST /orders behind a breaker still unsafe?"),
            ("C", "Per-JVM state: what changes with 10 gateway pods?"),
            ("C", "What belongs in a fallback, and what never does?"),
            ("S", "Breaker vs bulkhead vs rate limiter: what does each stop?"),
            ("S", "How do you tune window size and the failure threshold?"),
            ("S", "A half-slow dependency never trips it. What do you do?"),
            ("S", "How would you test the HALF_OPEN path in CI?")],
)
