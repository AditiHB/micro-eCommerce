"""Trace sheet spec: topic 03, ThreadPoolExecutor (core, queue, max, reject): the executors of this repo are Spring ThreadPoolTaskExecutor beans
(services/*/config/AsyncConfig.java, e.g. cacheEvictionExecutor core 2 / max 4 / queue 100), which wrap java.util.concurrent.ThreadPoolExecutor.

The trace comes from a small model of the pool's admission rule and is asserted against what the REAL JDK class printed for the same submissions
(PoolTrace.java on OpenJDK 21: threads and queue length after every submit of a never-finishing task). The oracle lists below are copied from that run."""


class Pool:
    def __init__(self, core, maximum, queue):
        self.core, self.max, self.qcap = core, maximum, queue
        self.threads, self.queue, self.rejected = [], [], []

    def submit(self, task):
        if len(self.threads) < self.core:          # 1. fewer than core threads: start a thread, even if others are idle
            self.threads.append(task)
            return "thread"
        if len(self.queue) < self.qcap:            # 2. otherwise queue it
            self.queue.append(task)
            return "queue"
        if len(self.threads) < self.max:           # 3. queue FULL: only now more threads, up to max
            self.threads.append(task)
            return "thread"
        self.rejected.append(task)                 # 4. the rejection policy (AbortPolicy: RejectedExecutionException)
        return "REJECTED"

    def cells(self):
        t = self.threads + [""] * (self.max - len(self.threads))
        q = self.queue + [""] * (self.qcap - len(self.queue))
        return t + q


# ---- the oracle: what the real java.util.concurrent.ThreadPoolExecutor did (result, threads, queued) after each submit
REAL_WORKED = [("thread", 1, 0), ("thread", 2, 0), ("queue", 2, 1), ("queue", 2, 2), ("queue", 2, 3), ("thread", 3, 3), ("thread", 4, 3), ("REJECTED", 4, 3)]       # core 2, max 4, queue 3
REAL_PRACTICE = [("thread", 1, 0), ("queue", 1, 1), ("queue", 1, 2), ("thread", 2, 2), ("thread", 3, 2), ("REJECTED", 3, 2)]                                         # core 1, max 3, queue 2


def submit_all(pool, names):
    out = []
    for n in names:
        r = pool.submit(n)
        out.append((r, len(pool.threads), len(pool.queue)))
    return out


# ------------------------------------------------------------------ worked trace: core 2, max 4, queue 3, eight tasks that never finish
pool = Pool(2, 4, 3)
w = []
assert submit_all(pool, ["t1", "t2"]) == REAL_WORKED[0:2]
w.append(dict(cells=pool.cells(), hi={0: "g", 1: "g"}, frame=(4, 6), vars={"threads": len(pool.threads), "queued": len(pool.queue)},
              note="t1 and t2: fewer than core (2) threads, so each starts a new thread. The queue is still empty."))

assert submit_all(pool, ["t3", "t4", "t5"]) == REAL_WORKED[2:5]
w.append(dict(cells=pool.cells(), hi={4: "y", 5: "y", 6: "y"}, frame=(4, 6), vars={"threads": len(pool.threads), "queued": len(pool.queue)},
              note="t3, t4, t5: both core threads are busy, so the tasks WAIT in the queue. Still 2 threads. The queue (3) is now full."))

assert submit_all(pool, ["t6"]) == REAL_WORKED[5:6]
w.append(dict(cells=pool.cells(), hi={2: "t"}, frame=(4, 6), vars={"threads": len(pool.threads), "queued": len(pool.queue)},
              note="t6: the queue is FULL, so only now the pool grows beyond core: a 3rd thread runs t6 (it does not wait for the queue)."))

assert submit_all(pool, ["t7"]) == REAL_WORKED[6:7]
w.append(dict(cells=pool.cells(), hi={3: "t"}, frame=(4, 6), vars={"threads": len(pool.threads), "queued": len(pool.queue)},
              note="t7: queue still full, threads 3 < max 4, so a 4th thread starts. The pool is now at max."))

assert submit_all(pool, ["t8"]) == REAL_WORKED[7:8]
w.append(dict(cells=pool.cells(), frame=(4, 6), state=[("rejected", [("t8: RejectedExecutionException", "r")])],
              vars={"threads": len(pool.threads), "queued": len(pool.queue)},
              note="t8: 4 threads busy, queue full: the rejection policy runs. AbortPolicy throws RejectedExecutionException to the caller."))
assert len(w) == 5 and pool.rejected == ["t8"]

# ------------------------------------------------------------------ practice trace: core 1, max 3, queue 2, six tasks
pool = Pool(1, 3, 2)
p = []
assert submit_all(pool, ["t1"]) == REAL_PRACTICE[0:1]
p.append(dict(vars={"threads": len(pool.threads), "queued": len(pool.queue)}, key="t1: below core (1): a new thread. threads 1, queued 0.",
              note="Submit t1. Which slot does it take?"))
assert submit_all(pool, ["t2", "t3"]) == REAL_PRACTICE[1:3]
p.append(dict(vars={"threads": len(pool.threads), "queued": len(pool.queue)}, key="t2, t3: the core thread is busy: both queue. threads 1, queued 2 (full).",
              note="Submit t2 and t3. Where do they go, and what is the queue now?"))
assert submit_all(pool, ["t4"]) == REAL_PRACTICE[3:4]
p.append(dict(vars={"threads": len(pool.threads), "queued": len(pool.queue)}, key="t4: queue full, threads 1 < max 3: a 2nd thread runs t4.",
              note="Submit t4. The queue is full."))
assert submit_all(pool, ["t5"]) == REAL_PRACTICE[4:5]
p.append(dict(vars={"threads": len(pool.threads), "queued": len(pool.queue)}, key="t5: queue still full, threads 2 < max 3: a 3rd thread. At max.",
              note="Submit t5."))
assert submit_all(pool, ["t6"]) == REAL_PRACTICE[5:6]
p.append(dict(state=[("rejected", [])], vars={"threads": len(pool.threads), "queued": len(pool.queue)}, key="t6: 3 threads and a full queue: REJECTED (RejectedExecutionException).",
              note="Submit t6. Threads are at max and the queue is full."))
assert len(p) == 5

# ---- the repo's own pool, by the same rule (card 5 of deck t03): cacheEvictionExecutor core 2 / max 4 / queue 100
big = Pool(2, 4, 100)
res = submit_all(big, [f"t{i}" for i in range(1, 51)])
assert (res[-1][1], res[-1][2]) == (2, 48)                              # 50 slow tasks: 2 threads run, 48 wait (the real JDK printed threads=2 queued=48)
res = submit_all(Pool(2, 4, 100), [f"t{i}" for i in range(1, 106)])
assert res[102] == ("thread", 3, 100) and res[103] == ("thread", 4, 100) and res[104] == ("REJECTED", 4, 100)      # real JDK: t103 thread 3, t104 thread 4, t105 rejected

SPEC = dict(
    n=3, name="Thread pool: core, queue, max, reject",
    card=dict(
        mantra="Core threads first. Then the QUEUE. Only a FULL queue grows the pool toward max. Then reject.",
        recipe=["submit(task):",
                "g|  threads < core?  start a thread",
                "t|  else queue has room?  enqueue",
                "i|  else threads < max?  start a thread",
                "r|  else  REJECT  (the policy decides)",
                "p|# Spring: ThreadPoolTaskExecutor",
                "p|# default queue: Integer.MAX_VALUE"],
        cost="Cost: a deep queue hides latency; a tiny one rejects.",
        questions=["How many threads run after N submits?",
                   "When does the pool grow past core?",
                   "What happens when everything is full?"],
        triggers=["pool size vs queue", "RejectedExecutionException", "tasks wait, pool small", "latency in a burst"],
        donts=["CPU-bound work: pool = cores, not 16", "unbounded queue + max: max is ignored"],
        bugs=["Queue 100 and max 16: the pool never grows past core.",
              "AbortPolicy throws: catch it and map it to a 503.",
              "One shared pool for slow and fast work: slow starves fast.",
              "Thread-locals (security context, MDC) do not follow the task."],
    ),
    worked=dict(
        title="Thread pool", subtitle="core 2, max 4, queue 3: eight tasks that never finish",
        input_label="slots 0-3 = threads (max 4), slots 4-6 = queue (3), core = 2",
        cells=[""] * 7, steps=w, answer="t1-t7 accepted, t8 REJECTED"),
    practice=dict(
        title="Thread pool", subtitle="core 1, max 3, queue 2: six tasks that never finish",
        input_label="slots 0-2 = threads (max 3), slots 3-4 = queue (2), core = 1",
        cells=[""] * 5, steps=p, answer="t1 thread, t2 t3 queue, t4 t5 thread, t6 REJECTED"),
    say=[("Context", "is the work blocking I/O or CPU, how bursty is it, and what should the caller get when we are full?"),
         ("Naive version", "a new Thread per task, or one big shared pool: no limit on threads, and slow work starves fast work."),
         ("The mechanism", "a bounded pool per concern: core threads, a queue, threads up to max only when the queue is full, then a rejection policy."),
         ("Walk a failure", "core 2 / queue 100 / max 4 and 50 slow tasks: 2 threads run, 48 wait; threads 3 and 4 only appear once 100 are waiting."),
         ("Trade-offs", "a deep queue turns overload into latency; a tiny one into 503s; size to the downstream, and map the rejection to an honest error.")],
    ladder=[("M", "In what order does a ThreadPoolExecutor use core, queue and max?"),
            ("M", "Why does core 2 / max 4 / queue 100 never grow past 2?"),
            ("M", "What happens to the 105th task of that pool?"),
            ("C", "Which rejection policies exist, and when is CallerRuns right?"),
            ("C", "Why does this repo use one pool per concern?"),
            ("C", "What is Spring's default queue capacity, and why is it risky?"),
            ("C", "How do you pick core and max for blocking HTTP calls?"),
            ("C", "What do thread-locals do when a task changes thread?"),
            ("S", "Platform threads vs virtual threads for blocking I/O?"),
            ("S", "How do you shut a pool down without losing work?"),
            ("S", "What does a SynchronousQueue change about the rules?"),
            ("S", "How would you expose pool saturation as a metric and alert?")],
)
