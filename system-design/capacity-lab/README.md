# P1 executable lab — Capacity estimates and bounded admission

> Java 25 · JUnit 5 · Maven · **не** production simulator, benchmark, rate limiter или working Spring endpoint. Lab-ът проверява чисти изчисления и определени concurrency invariants; **не** доказва реален SLO или Oracle throughput.

## 1. Входен production казус

Системата обслужва 8.64M API requests/day, 5× peak, 200 ms mean latency. Очаква се повече трафик и твърде много едновременни export requests. Екипът предлага 6 pods, без да е доказал дали bottleneck е JVM, Oracle, storage или network. Ако няма ясни assumptions и bounded admission, overload може да превърне timeout-ите в retry storm.

**Първо изчисляваме**, после решаваме дали трябва scaling, query optimization, request shedding или queue.

## 2. Decision contracts

| Техника | Решава | Кога да / кога НЕ | Печалба / цена | По-просто / test |
| --- | --- | --- | --- | --- |
| Workload estimate | sizes order of magnitude; highlights unknowns | при архитектурен преглед и измерени inputs; НЕ за твърдение „6 pods гарантират 500 RPS“ | shared numeric model / false precision при лоши assumptions | spreadsheet/whiteboard math + [JUnit](src/test/java/bg/hristomanov/education/systemdesign/CapacityPlannerTest.java) |
| Little's Law approximation | mean in-flight work from stable arrival & mean latency | за stable class/workload; НЕ като p99/thread/DB pool bound | clarifies queueing pressure / ignores burst shape | observed concurrent requests metrics |
| Bounded admission gate | keeps local concurrent work under limit | при per-instance saturation; НЕ като global distributed quota или rate limiter | fail fast and preserve accepted work / rejected requests | [BoundedAdmissionGateTest](src/test/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGateTest.java) |
| Queue-based load leveling | separates arrival and service rate in durable async flows | ако business outcome позволява status + later completion; НЕ за instant consistency | absorbs spikes / backlogs, retries, durability, operational cost | use [Azure pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/queue-based-load-leveling); няма queue тук |

## 3. README → source/test

| Concept | Source | Test |
| --- | --- | --- |
| Average RPS → peak; DB write pressure; mean in-flight; replica budget | [CapacityPlanner.java](src/main/java/bg/hristomanov/education/systemdesign/CapacityPlanner.java) | [CapacityPlannerTest.java](src/test/java/bg/hristomanov/education/systemdesign/CapacityPlannerTest.java) |
| Fast rejection at capacity, avoid running unadmitted work | [BoundedAdmissionGate.java](src/main/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGate.java) | [BoundedAdmissionGateTest.java](src/test/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGateTest.java) |
| Capacity recovered after a failed request | [BoundedAdmissionGate.java](src/main/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGate.java) | negative exception-release test |

## 4. Reproduce

```bash
mvn -B --no-transfer-progress -f system-design/capacity-lab/pom.xml test
```

No Docker, Redis, cloud service or DB needed. JDK25 and Maven required.

## 5. What the tests actually prove

- 8,640,000 daily requests → 100 average RPS; 5× peak → 500 peak RPS.
- `λ × W` for stable/mean values → ~100 in-flight at 200 ms.
- 20% writes → 100 modeled primary DB writes/s.
- 2 KiB payload → ~0.977 MiB/s raw **payload**.
- **If** load test establishes sustained 150 RPS/replica and policy budgets 60%, simple ceil estimate gives **6 replicas**. NOT a measured number of required pods.
- Invalid/huge inputs fail fast.
- With concurrency cap of 1, a second task is **rejected without any side effect** while first is active.
- When work throws, capacity is **returned**.
- No proof about p95/p99 latency, measured goodput, actual DB locks, multi-instance quotas, queue durability or network reliability.

## 6. Practical tasks (without solutions)

1. Change peak multiplier to 10×, compare mean concurrency and capacity estimate. Identify which assumptions remain unchanged.
2. Keep peak 500 RPS but decrease mean latency to 100 ms. What changes, and what cannot be concluded about *measured per-replica throughput*?
3. Modify gate to record admitted/rejected counters. Test no counter leakage when work fails.
4. Propose a queue for long-running exports; write the **state machine, duplicate processing, status SLO and old-job retention** before writing code.
5. Explain why 6 pods each with a local 20-permit gate could allow up to 120 active jobs, NOT a 20-job global quota. What design could enforce cross-pod capacity?

## 7. Original references

- [Google SRE — Non-Abstract Large System Design](https://sre.google/workbook/non-abstract-design/) (resource estimates and iterative design)
- [AWS Builders' Library — Load Shedding](https://builder.aws.com/content/3Eun1EEyX6p2e3VYNyRLSJzLuMV/using-load-shedding-to-avoid-overload) (protect goodput during overload)
- [AWS Well-Architected REL05](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel05.html) (throttle, fail fast, bounded queues, retry)
- [Microsoft — Queue-Based Load Leveling](https://learn.microsoft.com/en-us/azure/architecture/patterns/queue-based-load-leveling)

**Related chapter:** [Workload & Capacity](../02-CAPACITY-PERFORMANCE.md) · [Failure/Operations](../05-FAILURE-AND-OPERATIONS.md).
