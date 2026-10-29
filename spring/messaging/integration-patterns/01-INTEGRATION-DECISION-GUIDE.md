# 01 — Integration Patterns: choosing communication by coupling, flow and business semantics

> **P2 / Devoxx 2026 / Java & Spring engineering decision guide**  
> Lecture: [Integration patterns for distributed systems — Dirk Fröhner & Sabarish Sekar](https://www.youtube.com/watch?v=_CtnBnmYQC0).  
> Based on the **user-supplied, Gemini-generated SRT**, which has not been independently checked word for word. Timestamps are approximate; concrete claims about AWS services are separately checked against AWS's primary documentation.

## 1. The real question: "Should we replace REST with Kafka?"

Our **Booking** backend integrates with Payments, Loyalty, Notifications, Analytics and one external SaaS provider. Today Booking calls each dependency synchronously.

```text
Booking -> Payment API -> external processor
        -> Loyalty API
        -> Notifications API
        -> Analytics API
```

Business requires payment confirmation before confirming a paid booking. Loyalty points, analytics and the confirmation email may be asynchronous. During a slow downstream incident, the entire synchronous chain starts to fail or consume its request deadline.

An engineer proposes: "Put everything on Kafka; distributed communication will be decoupled." That proposal is not yet a design. It does not answer which service **owns the business decision**, who needs to know the receiver, whether a response is required, and which failures we must handle.

**Goal:** choose an integration pattern that reduces a *specified* dependency without claiming it eliminates all coupling. A fast broker does not make semantic dependencies disappear.

## 2. The four dimensions of coupling — lecture ~05:30–09:50

| Dimension | What must one side know / wait for? | Example |
| --- | --- | --- |
| **Location coupling** | Address, endpoint, or the broker/topic/queue identity | Booking knows `payment.example/api`, or the command queue name |
| **Format coupling** | Data format, envelope, protocol and schema/version compatibility | JSON command, CloudEvents envelope, Avro/Payload V2 |
| **Temporal coupling** | **Availability** of the other side, **and** waiting for completion | HTTP requires Payment up *now* and reply before timeout; durable queue lets consumer process *later* |
| **Domain coupling** | Business semantics and contract that a sender or receiver must understand | Booking constructs `CapturePayment` vs Loyalty chooses to react to `RideBooked` |

The talk importantly splits temporal coupling into **availability dependence** and **completion wait**. An async HTTP call removes the caller's wait for completion *only if* its application protocol actually returns before completion; it still needs the receiving HTTP server to be reachable during submission. A durable queue lets producer and consumer be available at different times, **but the producer now depends on the broker** being available or having a separate buffering policy.

**Mental model:** changing transport redistributes dependencies; it does not automatically remove them.

## 3. Decision table — REST / asynchronous command / queue / pub-sub

| Approach | Where is the domain coupling? | Success / failure contract | Strength | Important cost |
| --- | --- | --- | --- | --- |
| Synchronous REST request/reply | Caller knows downstream operation | Explicit response, HTTP error and a client deadline | Immediate answer; direct use-case boundary | Latency/availability coupling; cascading timeouts |
| Async HTTP callback / fire-and-forget | Caller still knows receiver/operation | Acceptance != business completion; callback or status API needed | Early return without a broker | Receiver must accept request now, recovery and duplicate handling |
| Command through durable queue | Sender specifies **recipient-specific intent** | Broker acceptance != business success; eventual result needs separate mechanism | Buffers bursts; consumer pulls at own pace | Broker, poison messages, queue age, retries, idempotency |
| Event through pub/sub topic | **Consumer** chooses to depend on producer's published fact | Producer knows event publication, not whether every subscriber's business action succeeded | New consumers without modifying producer | Event contract evolution, fan-out, privacy, eventual outcomes |
| Topic -> dedicated queue(s) -> consumers | Event contract at producer; each subscribed group owns durable queue | Publication + per-subscriber durable backlog/retries | Fan-out **and** buffering / independent consumer pace | Multiple queues, subscriber-specific DLQ/recovery/monitoring |

**Nuance:** "Every subscriber receives every message" is a conceptual fan-out description, not a universal infrastructure guarantee. Real services may filter or drop events, use at-most/at-least-once semantics, enforce TTL/retention, and have their own subscriber delivery contract.

### Commands vs events — producer vs consumer ownership (~12:00–17:00)

```text
Command: CapturePayment(paymentId, amount)
Booking -> payment command queue -> Payment
Booking understands the desired action and must handle its outcome.

Event: RideBooked(bookingId, occurredAt)
Booking -> topic -> Loyalty / Analytics / Notifications
Consumers decide whether/how to react; Booking does NOT code their reactions.
```

- A **command** describes a requested action, normally with a clearly responsible handler and success/error status.
- An **event** describes a past business fact owned by its publisher; it does not command every consumer to do the same thing.
- A **Saga** can combine commands and events for a long-running use case. Publishing a `RideBooked` event is not proof that payment has settled.
- If Booking absolutely requires payment approval *before* confirming, choose a synchronous call, or design an asynchronous **pending/confirmed/failed state machine** with a clear product contract. Do not silently substitute eventual confirmation.

We already demonstrate [Domain Events](../../events/domain-events/README.md) and [Saga](../saga/README.md), so this lesson adds a **decision guide**, not a second implementation of those techniques.

## 4. Two arrows: data flow vs control flow (~17:50–25:00)

```text
PUSH webhook:
Producer --data/control--> Consumer (sender initiates delivery)

PULL queue:
Producer --push message--> Broker
Broker   <--poll/receive-- Consumer (consumer initiates read)
Broker   --message data--> Consumer
```

**Data flow** describes where payloads travel. **Control flow** describes who initiates the next interaction. They may point in different directions. Pull can reduce inbound network exposure and give consumers demand control, batching and independent retry timing; it can add polling latency. Push can be fast and avoid idle polling but must enforce downstream capacity, timeout and retry limits.

Choose deliberately:
- Need low-latency browser notification? A supported push channel/webhook can fit; its receiver still needs authentication, retry and dedupe.
- Third-party periodically produces 100k integration updates while our workers are slow? A durable queue with bounded consumption can flatten the **peak**, but its backlog still grows if arrival rate stays above service rate.
- Need isolated consumer pace and replay? Give each independent subscriber a durable subscription/queue rather than tying their acknowledgement lifecycle together.

### Backpressure, bounded capacity and TTL (~19:50–21:50)

For a fixed processing budget, **producer rate above consumer rate for long enough means unbounded queue growth unless there is a limit**. Backpressure is a protocol for slowing/deferring submissions; bounded admission can explicitly reject excess requests. TTL can expire genuinely replaceable work (stale periodic refresh, superseded recommendations), but **is not a universal solution for finance, audit, legal obligations or payments**.

Our [BoundedAdmissionQueue](src/main/java/bg/hristomanov/education/integrationpatterns/queue/BoundedAdmissionQueue.java) rejects a third job when the queue holds two. The caller must **retain and retry rejected critical work**; it is not considered successfully accepted.

We already have a richer [Production API Resilience / Load Shedding](../../reliability/fault-tolerance/PRODUCTION-API-RESILIENCE-P1.md) lab. This lightweight new example merely shows how admission control connects to queue fairness.

## 5. Delivery and ordering — intentionally linked, not duplicated (~25:00–36:30)

Three ideas must never be conflated:

1. **Delivery guarantee** — at-most-once can lose messages, at-least-once can redeliver, and "exactly once" requires a precisely stated **transactional scope**.
2. **Effect idempotency** — repeated attempts must not repeat a non-repeatable business effect. Unique key/transaction is often the correct barrier.
3. **Ordering** — duplicate detection does not ensure `OrderCancelled v2` is applied after `OrderCreated v1`. Per-aggregate versions and partition-local ordering matter.

**Our previous runnable material:**
- [Transactional Outbox + Inbox](../outbox-inbox/README.md): DB+outbox atomicity, relay publish gaps, idempotent consumers, per-aggregate ordering and Kafka transaction scope.
- [HTTP Idempotency](../../reliability/idempotency/README.md): ambiguous response, retry keys and replay.
- [Event Sourcing](../../architecture/event-sourcing/README.md): streams, projections and global positions.

Never translate Kafka's scoped read-process-write exactly-once processing guarantee into "our external bank API and Oracle row have an automatic single atomic business transaction." The technical boundary is different.

## 6. Four engineering decisions — work through the constraints

| Use case | Candidate integration | Why | What must be verified |
| --- | --- | --- | --- |
| User presses "Pay" and UI must show final approval | Request/reply, or explicit pending workflow | Product expects a timely definite answer | Deadlines, idempotency, unknown outcome, provider retry behavior |
| Loyalty reacts to a confirmed booking | Domain integration event -> subscriber queue | Producer does not own loyalty policy | Outbox, event schema, idempotent loyalty consumer |
| Multiple analytic consumers want booking facts | Topic/fan-out -> independent queues | Consumer-specific pace and retention | Monitoring, DLQ, isolation, data minimization |
| Tenant A generates 80x traffic and quiet tenants miss SLA | Fair scheduling / cells / sharding | Protect quiet-tenant dwell times | Per-tenant dwell p95, backlog and capacity; see [02 — Queue Fairness](02-QUEUE-FAIRNESS-NOISY-NEIGHBOR.md) |

### When direct coupling is the better choice

If the operation is small, cohesive and the caller **must** know the result in the same invariant, a direct method call or synchronous API may be simpler, cheaper and easier to reason about. A broker adds retention, consumer recovery, schema evolution, observability and eventually consistency considerations.

Do not add a queue merely to avoid two packages having a direct dependency. First identify the ownership boundary, and whether it is truly an independent deployment/failure domain.

## 7. How to use this on a real code review

Write a short Architecture Decision Record:

```text
Context: Booking must approve card payment before "Confirmed";
         Loyalty and Email are independent reactions.
Options: synchronous all; queued commands for all; sync payment + RideBooked topic.
Decision: payment request/reply with explicit timeout and retry semantics;
          publish durable RideBooked integration event via Outbox;
          independent subscriber queues for Loyalty and Email.
Consequences: payment availability still gates confirmation;
              loyalty/email can lag and require dedupe + monitoring.
Revisit if: product permits "PendingPayment", or payment provider semantics change.
```

### Checklist / active recall

- What is the business invariant, and who owns it?
- Which coupling dimensions are we deliberately reducing? Which remain?
- Is this a **command** (requested action) or an **event** (published fact)?
- Does broker ACK mean *accepted for delivery* or *business processed successfully*?
- Who initiates delivery; who controls pressure and batching?
- Where are delivery/retry/idempotency/ordering semantics specified?
- Is the event contract discoverable, versioned and privacy-safe?
- What happens if the consumer stays down longer than retention/TTL?
- Does one tenant's traffic alter other tenants' dwell time?
- What can the team operate and debug at 03:00?

**Practical action:** open the [runnable fairness lesson](02-QUEUE-FAIRNESS-NOISY-NEIGHBOR.md) and predict the numbers before running the tests.

## Original / primary sources

1. [Integration patterns for distributed systems — Dirk Fröhner and Sabarish Sekar, Devoxx](https://www.youtube.com/watch?v=_CtnBnmYQC0), ~05:30–25:00 and ~25:00–36:30. The provided SRT is machine-generated and **not independently verified word for word**.
2. [AWS SQS — fair queues detailed behavior](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues-detailed.html) — external confirmation of queue semantics, not a verbatim transcript.
3. [AWS — SQS message groups in standard and FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues.html).
4. [Martin Fowler — What do you mean by Event-Driven?](https://martinfowler.com/articles/201701-event-driven.html) — independent conceptual reference.
5. [Microservices.io — Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html).
6. [AWS Builders' Library — retries and idempotency](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/).

**Learning boundary:** This is a decision-oriented document, not proof that a particular AWS service, Spring adapter or distributed deployment has been built here.
