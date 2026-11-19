# SD-03/04/05 — Workload, capacity, Little's Law and scaling

> **P1 · с изпълнима Java 25 лаборатория**. Източници: [Google SRE — Non-Abstract Large System Design](https://sre.google/workbook/non-abstract-design/), [Azure — Design to scale out](https://learn.microsoft.com/en-us/azure/architecture/guide/design-principles/scale-out).

## 1. Реалният проблем

Продуктов екип иска: „системата да издържи 5× повече потребители“. Някой предлага 5× pods. Но bottleneck-ът може да е Oracle connection pool, една тежка query, непартиционирана таблица, lock contention или external API. Horizontal scaling на app instances не означава 5× usable throughput.

## 2. Изходни assumptions (пример, НЕ измервания)

| Input | Учебна стойност | Критично уточнение |
| --- | ---: | --- |
| Daily requests | 8 640 000 | обикновени API requests, не брой потребители |
| Peak / daily mean | 5× | трябва да се докаже с traffic shape |
| Mean end-to-end latency | 200 ms | не p99 |
| Primary DB write fraction | 20% | 1 modeled write на write request |
| Mean response payload | 2 KiB | без TLS/headers/compression |
| Measured sustained per-replica load-test capacity | **150 rps (hypothetical!)** | **ако** бъде измерена при representative workload |
| Target budget | 60% | headroom policy, не гаранция |

### Step 1 — Average and peak RPS

```text
8,640,000 / 86,400 seconds = 100 average RPS
100 × 5 = 500 peak RPS
```

### Step 2 — Approximate mean concurrency via Little's Law

При стабилно натоварване и дефинирана boundary, среден брой заявки в системата:

```text
L ≈ λ × W = 500 requests/sec × 0.2 sec = 100 mean requests in flight
```

Това **не** означава, че са нужни точно 100 platform threads, 100 DB connections или че p99 latency е 200 ms. Virtual threads и pooled connections имат различни lifecycles, а production traffic е bursty.

### Step 3 — Writes and egress

```text
modeled peak DB writes = 500 × 0.2 = 100 writes/sec
payload throughput = 500 × 2,048 bytes/sec = 0.9765625 MiB/sec
```

Това са *lower-fidelity estimates*: няма secondary indexes, replication, retries, query rows read, TLS, gzip, audit events, backups, network overhead или други systems.

### Step 4 — Replica count with measured capacity

**Само ако** representative load test покаже sustained 150 RPS при допустимо p99, error rate и DB usage за една replica, и използваме 60% budget:

```text
usable per replica = 150 × 0.60 = 90 RPS
ceil(500 / 90) = 6 replicas (initial estimate, not a promise)
```

Ако DB pool/store не издържа 500 RPS, шест replicas само добавят още contenders.

## 3. README → код → тест

| Проверка | Production-like модел | Тест |
| --- | --- | --- |
| Daily → peak RPS, writes, egress, headroom | [CapacityPlanner](capacity-lab/src/main/java/bg/hristomanov/education/systemdesign/CapacityPlanner.java) | [CapacityPlannerTest](capacity-lab/src/test/java/bg/hristomanov/education/systemdesign/CapacityPlannerTest.java) |
| Local concurrency/overload reject without running work | [BoundedAdmissionGate](capacity-lab/src/main/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGate.java) | [BoundedAdmissionGateTest](capacity-lab/src/test/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGateTest.java) |

```bash
mvn -f system-design/capacity-lab/pom.xml test
```

## 4. Кои ресурси са истинските ограничения?

| Resource | Какво измерваш | Какво НЕ доказва „повече pods“ |
| --- | --- | --- |
| CPU | utilization, saturation, CPU profiles, allocation pressure | Ако bottleneck е single SQL lock |
| JVM | GC pause, RSS/heap/native memory, thread contention | Ако request-ът блокира в DB pool |
| DB pool | active/pending, acquire wait, connection hold time | Ако DB иска по-малко total connections |
| Database | slow queries, plans, wait events, transactions, locks, IOPS | Ако indexes/index maintenance доминират |
| Network | bytes, RTT, retransmits, payload, timeouts | Ако downstream rate limit остава общ |
| Queue | arrival vs service rate, backlog age, oldest message | Ако consumer е без bounded retry/DLQ |

## 5. Decision cards

| Избор | Кога | Кога НЕ | Печалба / цена | Алтернатива / доказателство |
| --- | --- | --- | --- | --- |
| More replicas | App-local saturation, no shared bottleneck | DB lock/downstream quota | isolated CPU+availability; deployment+connection count | tune SQL; verify throughput/error/p99 under fixed load |
| Cache | repeated expensive reads with acceptable staleness | must-read-current transaction or invalidation unknown | lower DB reads; stale-data risk, memory cost | query/index/projection; verify hit ratio, correctness |
| Read projection | read model differs materially from write model | trivial CRUD | faster tailored reads; freshness, maintenance | DTO query; test lag and read-your-writes |
| Queue | work can be asynchronous and has bursts | user requires immediate durable outcome | smooth intake; backlog/delay, retry/DLQ burden | synchronous bounded worker; measure oldest age |
| Backpressure/load shedding | admitted work would overload the service | merely low RPS and no saturation | preserve completed goodput; explicit 429/503 failures | bounded connection/work pool; [gate test](capacity-lab/src/test/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGateTest.java) |

## 6. Самостоятелна задача

Peak същият — 500 RPS. След оптимизация request latency пада от 200 ms на 90 ms, но per-replica throughput не е повторно измерен. Пресметни новата approximate mean concurrency, **без** да твърдиш автоматично, че нужните replicas са намалели. Кои два load tests и три runtime metrics трябва да видим?

Допълнение: endpoint за export връща 100 MB вместо 2 KiB. Изчисли само raw egress и обсъди server memory/backpressure, без да го обявяваш за production network requirement.
