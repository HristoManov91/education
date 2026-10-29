# 02 — Noisy Neighbor, Fair Queues, Cells and Shuffle Sharding

> **P2 / runnable Java 25 laboratory / one worker per simulated queue**  
> Follows the **fourth part** of [Integration patterns for distributed systems](https://www.youtube.com/watch?v=_CtnBnmYQC0) (~36:45–48:50). The user-supplied SRT is **Gemini-generated and not fully verified against audio**. Concepts are also compared with current [AWS SQS fair queue documentation](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues-detailed.html).

## 1. Real case: 20 SaaS tenants, one queue, one noisy customer

An application serves many tenants using shared message processing workers. Normally each tenant submits five events, but tenant `noisy` suddenly submits a burst of 80. We have one worker; messages take one tick each.

```text
One GLOBAL FIFO:
[noisy × 80] [quiet × 5] --> one worker (one job / tick)
                          `quiet` waits 80–84 ticks before starting

Per-TENANT round robin:
noisy job, quiet job, noisy job, quiet job, ...
                          `quiet` starts at ticks 1,3,5,7,9
```

Both models execute **the same 85 jobs** and require the **same 85 ticks** for a single worker. Fairness does not conjure up capacity; it changes **who waits** when resources are congested.

This is one of the lecture's strongest senior engineering lessons: a system can have good **overall throughput** and terrible **tenant-level experience**.

### Exact metrics — deterministic model, not AWS benchmarks

<text>Simulation assumptions:</text> All jobs are already queued at tick 0; one non-preemptive worker; each job runs for one tick; the noisy tenant's 80 jobs arrive before the quiet tenant's 5. **Dwell** here is time *in queue until processing starts*, not end-to-end response time. p95 uses **nearest-rank**; for five quiet jobs this is the worst observed value.

| Policy | Quiet tenant p95 dwell | Quiet worst dwell | Total makespan | Jobs processed |
| --- | ---: | ---: | ---: | ---: |
| Global FIFO | **84 ticks** | 84 ticks | 85 ticks | 85 |
| Per-tenant round robin | **9 ticks** | 9 ticks | 85 ticks | 85 |

Proof: [QueueFairnessSimulationTest](src/test/java/bg/hristomanov/education/integrationpatterns/queue/QueueFairnessSimulationTest.java) and the [FairnessDemo CLI](src/main/java/bg/hristomanov/education/integrationpatterns/queue/FairnessDemo.java).

**Caution:** this is *our simplified educational example*, not an empirical claim that Amazon SQS returns these exact numbers, and not an estimate of real-world p95 latency.

## 2. README → code → observable proof

| Lesson | Java code | Executable test |
| --- | --- | --- |
| Work item and contract | [QueuedJob](src/main/java/bg/hristomanov/education/integrationpatterns/queue/QueuedJob.java), [QueueScheduler](src/main/java/bg/hristomanov/education/integrationpatterns/queue/QueueScheduler.java) | [QueueFairnessSimulationTest](src/test/java/bg/hristomanov/education/integrationpatterns/queue/QueueFairnessSimulationTest.java) |
| Naive global FIFO | [GlobalFifoScheduler](src/main/java/bg/hristomanov/education/integrationpatterns/queue/GlobalFifoScheduler.java) | Same test: late quiet p95 |
| Fair tenant turns | [TenantRoundRobinScheduler](src/main/java/bg/hristomanov/education/integrationpatterns/queue/TenantRoundRobinScheduler.java) | Same test: quiet p95=9, per-tenant order |
| Deterministic dwell measurement | [QueueSimulation](src/main/java/bg/hristomanov/education/integrationpatterns/queue/QueueSimulation.java) | Same test: total jobs and makespan preserved |
| No silent drop on full queue | [BoundedAdmissionQueue](src/main/java/bg/hristomanov/education/integrationpatterns/queue/BoundedAdmissionQueue.java) | Same test: explicit rejection |
| Isolated resource cells | [CellRouter](src/main/java/bg/hristomanov/education/integrationpatterns/queue/CellRouter.java) | [CellIsolationTest](src/test/java/bg/hristomanov/education/integrationpatterns/queue/CellIsolationTest.java) |
| 2-of-N shuffle pairs + probability | [ShufflePairAssigner](src/main/java/bg/hristomanov/education/integrationpatterns/queue/ShufflePairAssigner.java) | [ShufflePairAssignerTest](src/test/java/bg/hristomanov/education/integrationpatterns/queue/ShufflePairAssignerTest.java) |

### Run — zero cloud costs, no Docker

```bash
# Run ONLY the new Java/JUnit module:
mvn -pl spring/messaging/integration-patterns test

# After compilation, run a tiny console demonstration:
mvn -pl spring/messaging/integration-patterns compile
java -cp spring/messaging/integration-patterns/target/classes bg.hristomanov.education.integrationpatterns.queue.FairnessDemo

# Full education Maven reactor:
mvn --no-transfer-progress verify
```

The CLI prints the two deterministic quiet p95 dwell values and the identical makespan. No Spring context, external broker, credentials or AWS resources are started.

## 3. Four separate policies — don't confuse the terms

| Policy | Behavior | Helps with | Cost / boundary |
| --- | --- | --- | --- |
| Global FIFO | Oldest work first, irrespective of tenant | Simple sequential backlog | Loud tenant can dominate |
| **Round robin by tenant** | Take one pending job per active tenant, preserving per-tenant FIFO | Small tenants get turns | Expensive single jobs still block; active tenant count matters |
| **Weighted fair scheduling** | Allocate capacity according to business weights or job cost | VIP priorities and variable-cost tasks | More complex metering, may starve tenants if implemented incorrectly |
| **Strict isolation / cell queues** | Route tenants to separate worker/queue resource pools | Limit blast radius | More infrastructure, capacity fragmentation, hot cells |
| **Shuffle sharding** | Each tenant receives a small shard subset | Reduce fully coincident failure groups | Routing/failover/partial overlap complexities |
| **Dedicated tenant queue** | Strongest tenant-specific queue boundary | Known critical/noisy or premium tenant | Queue count, deployments, ownership, cost |

The source lecture describes **dedicated queues**, **cells**, **shuffle sharding**, **hybrid dedicated tiers**, **dynamically created overflow queues**, and finally **managed Fair Queues**. Our code models *part* of those ideas; we do not claim to implement dynamic real queue provisioning or cross-queue failover.

### Why job-count round robin is not enough for slow jobs

If noisy's **first job needs 50 ticks** and quiet has one one-tick job, the quiet job still waits 50 ticks, even with round robin — the scheduler does not preempt work that is already running.

[Test — expensive job](src/test/java/bg/hristomanov/education/integrationpatterns/queue/QueueFairnessSimulationTest.java) proves this limitation. For variable processing cost, consider estimated-job-cost/deficit-round-robin, actual concurrent capacity and per-tenant quotas. Those algorithms and realistic scheduling are **not** implemented in this lab.

## 4. Backpressure / bounded admission: no "ACK success" for rejected work

A real queue cannot absorb unlimited sustained input if the drain rate remains lower than arrivals. Flow-control choices:

- Slow producers, with an explicit throttle/retry contract.
- Bounded pending queue: **reject** when full, returning a meaningful signal. The source of critical work must retain it for later retry.
- Expire **only replaceable**, business-authorized requests using TTL (stale refresh, obsolete suggestions); never silently expire legal events, payments or auditing.
- Scale worker capacity, but bound the downstream dependency and database too.

[BoundedAdmissionQueue](src/main/java/bg/hristomanov/education/integrationpatterns/queue/BoundedAdmissionQueue.java) shows `ACCEPTED` and `REJECTED_CAPACITY` as **different outcomes**; queue admission isn't business completion or durable storage. It only counts pending work, not in-flight work.

For a realistic Clock-based replacement policy, read [Production API Resilience — Load Shedding](../../reliability/fault-tolerance/PRODUCTION-API-RESILIENCE-P1.md). We do **not** copy that entire existing implementation.

## 5. Cell-based sharding: capacity must actually be independent

With four independent cells:

```text
                  ┌─ Cell 0: queue + worker ─ tenant(s)
tenant -> router ─├─ Cell 1: queue + worker ─ tenant(s)
                  ├─ Cell 2: queue + worker ─ tenant(s)
                  └─ Cell 3: queue + worker ─ tenant(s)
```

A noisy tenant only queues **behind the tenants in its own cell**, *if* each cell has independent workers/capacity and the dependencies that matter are also isolated. If all cells call the same saturated database, bottleneck or global throttling service, isolation can disappear.

[CellIsolationTest](src/test/java/bg/hristomanov/education/integrationpatterns/queue/CellIsolationTest.java) builds **four separate FIFO schedulers** and routes tenants with [CellRouter](src/main/java/bg/hristomanov/education/integrationpatterns/queue/CellRouter.java):

- `noisy` has 64 jobs;
- quiet tenant in **same** cell starts only after 64 ticks;
- quiet tenant in **another** cell starts at **tick 0**.

Total 66 jobs are executed; **no messages are discarded**. This is a model of resource-isolated scheduling, not a cloud fault-isolation proof.

### The "25% of tenants" simplification

The presenters use a **four-cell → 25%** illustration and an eight-cell → 12.5% illustration for **evenly distributed** tenants. These are **not guaranteed upper bounds** for arbitrary hashing: unequal tenant populations, correlated workloads and shared dependencies can produce a much larger real blast radius. Record actual tenant-per-cell distribution and per-cell capacity rather than assuming the math alone isolates production.

Our `CellRouter` uses simple deterministic Java string hashing as a *teaching-only placement policy*. Changing `cellCount` changes assignment and requires state/backlog migration planning; do not treat a hash modulo as a production consistent-hashing protocol.

## 6. Shuffle sharding: 1/28 is NOT total blast-radius probability

Lecture ~42:00–43:25: if each tenant gets **two queues from a pool of eight**, the number of unordered unique pairs is:

```text
C(8,2) = 8 × 7 / 2 = 28 possibilities
Exact SAME pair, ideal independent uniform assignment = 1/28 ≈ 3.57%

Pairs with NO shared queue: C(6,2) = 15
ANY shared queue: 1 - 15/28 = 13/28 ≈ 46.43%
```

The **1/28** number is the probability of an **identical pair**, not the chance of *any* resource overlap or a hard limit on affected tenants. Sharing only one of two queues may still cause interference, depending on which queue each sender uses, fallback policy and how workloads are routed.

For **100 queues, two chosen per tenant**, there are 4950 distinct pairs. Exact matching is ~0.0202%, but **any overlap is ~3.98%**, not zero. Both percentages are *idealized* combinatorics, not guarantees for a particular hash function or deployment.

We preserve this important correction in [ShufflePairAssignerTest](src/test/java/bg/hristomanov/education/integrationpatterns/queue/ShufflePairAssignerTest.java). The [ShufflePairAssigner](src/main/java/bg/hristomanov/education/integrationpatterns/queue/ShufflePairAssigner.java) can assign the same stable 2-of-N index set to a tenant; it intentionally **does not** implement message routing, replication, failover or independent random uniform tenant allocations. Those are separate engineering problems.

### Before claiming a real sharding blast radius

Define failure domain (whole cell vs queue vs consumer vs downstream DB), tenant-to-queue routing and ownership, backlog resharding, failover trigger, data durability, per-aggregate ordering, idempotency and how to avoid splitting one business operation over inconsistent shards.

You should be able to explain how a tenant continues service when *one* of its two queues is slow or unavailable. A two-number assignment function alone does not answer that.

## 7. Amazon SQS Fair Queues — a managed analogy, not our algorithm

The talk ends (~47:05) with a managed feature that protects quiet tenants. The independently verified **current AWS SQS docs** clarify:

- SQS Fair Queues apply to **Standard** queues and use `MessageGroupId` as a **tenant identifier**.
- Fairness uses detection of noisy tenants and **delivery prioritization**, considering in-flight concurrency share and processing-time share; it is **not our strict round-robin algorithm** and not literally a per-tenant physical overflow queue.
- Standard-queue `MessageGroupId` **does not promise FIFO order**. The similarly named property in **FIFO queues** has different ordering semantics.
- It improves **dwell time** for quiet tenants; does not magically add consumer capacity, eliminate duplicates or implement business idempotency.
- No AWS account, SDK, infrastructure or AWS costs are needed for this learning module.

Official docs: [SQS fair queue guide](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues.html), [detection behavior](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues-detailed.html), [AWS FAQ](https://aws.amazon.com/sqs/faqs/).

## 8. Observability: throughput isn't tenant experience

Queue-level total throughput can stay constant while customers time out. Measure at least:

| Metric | Scope / purpose |
| --- | --- |
| Queue depth and oldest message age | All traffic + per tenant/cell if available |
| **p50/p95/p99 dwell time per tenant** | Detect noisy-neighbor interference |
| In-flight count and average processing time | Detect slow consumer / costly tenant |
| Admission rejects, retry age, DLQ, poison message count | Avoid silent work loss |
| Per-cell load and downstream saturation | Confirm actual capacity isolation |
| Accepted vs completed business operations | Don't confuse broker ACK with successful side effects |

A small tenant may have very few samples; interpret its p95 cautiously. Our five-message p95 simply selects the worst of five values. In production, use adequate sample sizes, windows, error budgets and histograms.

## 9. Exercises and code-review challenge

1. Predict `quiet` p95 for **80 noisy + 5 quiet** under FIFO and round robin. Run the CLI and explain **why makespan is unchanged**.
2. Change the noisy processing time to 50 ticks. Which property of round robin fails? What is the correct next scheduling technique?
3. Enqueue one more critical job into a queue with capacity two. Does `REJECTED_CAPACITY` mean you may lose it? Who owns retry/durable persistence?
4. Route quiet tenants to the same and a different cell. Under which *explicit isolation assumption* is a different cell unaffected?
5. For two of eight queues, calculate **both** exact-pair collision and any-overlap probabilities. Explain why neither is identical to production blast radius.
6. Why can't you assume `MessageGroupId` in an **SQS Standard Fair Queue** enforces FIFO ordering?
7. A new premium tenant has a predictable large load. Compare shared fairness, dedicated tenant capacity and shuffle sharding, including operational cost.
8. What breaks when queueCount/cellCount changes and the routing hash moves tenants with unprocessed messages?

### Senior checklist

- [ ] Chosen business service-level objective measured **per tenant**, not only globally.
- [ ] Capacity bound accounts for pending + in-flight processing, downstream limits, worker crashes and retries.
- [ ] A rejection is NOT acknowledged as business success.
- [ ] Any TTL/drop policy is business-approved, never generic.
- [ ] Resource isolation is verified beyond queue naming (workers, DB, quota, infrastructure).
- [ ] Shuffle probability refers to **identical shard pairs** vs **any overlap** correctly.
- [ ] Ordering is evaluated per aggregate and across retries; fairness may reorder.
- [ ] Stable queue mapping and resharding/recovery plans exist.
- [ ] Real provider semantics are verified against **provider docs**, not copied from a Java model.
- [ ] Tests demonstrate both benefits **and limits** of our selected scheduler.

**Mental model:** *Fairness protects who gets to make progress under load; sharding protects who shares failure; neither automatically increases capacity or guarantees correctness.*

## Sources

1. [Integration patterns for distributed systems — Dirk Fröhner, Sabarish Sekar](https://www.youtube.com/watch?v=_CtnBnmYQC0), Devoxx Belgium 2026, ~36:45–48:50 (Gemini-produced SRT, unverified word-for-word).
2. [AWS — SQS Fair Queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues.html).
3. [AWS — How SQS Fair Queues Work](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues-detailed.html).
4. [AWS — SQS Fair Queue FAQs](https://aws.amazon.com/sqs/faqs/).
5. [AWS Builders' Library — Workload isolation using shuffle sharding](https://aws.amazon.com/builders-library/workload-isolation-using-shuffle-sharding/).
6. Related local labs: [Outbox/Inbox](../outbox-inbox/README.md), [Production API Resilience](../../reliability/fault-tolerance/PRODUCTION-API-RESILIENCE-P1.md), [Idempotency](../../reliability/idempotency/README.md).

**Scope boundary:** Educational, deterministic, single-process simulation. **Not** a durable/HA queue, real multithreaded worker pool, SQS API client, or proof of production SLOs.
