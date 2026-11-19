# SD-10/11/12/13 — Failure modeling, overload, availability and operations

> **P1 core, P2 security extension.** Основни източници: [AWS Well-Architected REL05](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel05.html), [AWS Builders' Library — load shedding](https://builder.aws.com/content/3Eun1EEyX6p2e3VYNyRLSJzLuMV/using-load-shedding-to-avoid-overload), [Google SRE — Implementing SLOs](https://sre.google/workbook/implementing-slos/).

## 1. Реалният проблем — защо просто „още retries“ може да срине услугата

При 500 RPS DB pool има 20 connections. Една тежка query държи connection 5 секунди, клиентите timeout-ват и опитват отново. Наивната схема:

```text
slow DB → long lock/connection hold → queue of request threads
→ client timeout → retries → even more DB pressure
→ timeouts cascade
```

Добавянето на още application pods може да увеличи броя на чакащите connections и да влоши bottleneck-а. Това е **система с положителна обратна връзка**, не просто exception за „failed SQL“.

## 2. Причина → защитен механизъм → цена

| Mechanism | Проблем / кога | Кога НЕ / по-просто | Печалба / загуба | Доказателство |
| --- | --- | --- | --- | --- |
| Client timeout + end-to-end deadline | висящ downstream, bounded waiting | твърде кратък timeout, който унищожава полезна работа | ограничен latency; false timeout при burst | failed/slow dependency test |
| Retry with capped backoff+jitter | transient failure, idempotent operation | overload, auth failure, invalid data, non-idempotent write без ключ | recover transient; extra load | count attempts & success/fail classification |
| Circuit breaker | unhealthy dependency, limit repeated calls | малък service без evidence за breaker policy | fail fast; possible false OPEN | state transition test |
| Bulkhead / bounded concurrency | shared pool saturation, noisy neighbor | предполагаме, че per-pod лимит = глобален quota | isolation; explicit rejects | [BoundedAdmissionGateTest](capacity-lab/src/test/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGateTest.java) |
| Queue-backed worker | bursty, asynchronous business process | user needs committed synchronous result | smooth intake; lag + durable infra cost | backlog age, duplicates, DLQ/replay |
| Multi-zone / redundant replicas | zone-level failure resilience | single shared DB/network dependency remains SPOF | fault tolerance; operational cost | fault injection, actual RTO/RPO |
| Cache + degraded read | lookup tolerates known staleness | irreversible/signature action needs authority | read availability; stale data | stale-read test |
| Graceful shutdown | rolling deploy with in-flight requests | simple `kill -9` benchmark pretends normal shutdown | less lost work; draining cost | probe and in-flight drain tests |

## 3. Load shedding vs rate limiting vs queue

- **Admission gate / concurrency limit:** максимум `N` активни работи в един instance; `tryAcquire` може да откаже излишните. [Lab](capacity-lab/README.md) показва това; той **не** е distributed rate limiter.
- **Rate limiter:** `N` requests за период / token bucket, често per-client/per-route и distributed counters. Не е заместител на downstream capacity control.
- **Queue:** request се приема за **асинхронно обработване**, може да остане backlog; приемането не е final business success.
- **Load shedding:** контролирано отказване на нископриоритетна/излишна работа, за да запази goodput за приетите. Требва observable rejection policy, без retry storms.

### Защо няма универсален HTTP status?

Търси semantics на API: `429` при rate quota, `503` при обща unavailable/overload услуга, `202` при durable accepted async job (не completed), `409` при бизнес конфликт. Не приравнявай всички rejects.

## 4. Reliability: dependency math and shared fate

Две услуги с 99.9% independent availability в series биха имали `0.999² ≈ 99.8001%` обща наличност **само** ако опростеният модел е валиден. Реалните зависимостите са correlated, а critical journey може да има fallback, caching или parallel calls. Никога не получавай реална system SLA само чрез умножаване на vendor SLA.

**RTO** = максимално допустимото време за възстановяване. **RPO** = максимално допустимата загуба на данни, измервана във време. Multi-AZ ≠ автоматично zero data loss или RTO=0. Реплика, backup, failover runbook и recovery tests са различни неща.

## 5. Operability is architecture

Преди go-live трябва да има: SLI/error budget, golden signals (rate, errors, latency, saturation), traces по критични boundaries, DB acquisition/wait metrics, oldest queue message, schema compatibility, rollout/rollback и on-call runbook. За security: threat modeling на user/object authorization, tenant isolation, sensitive logging, secrets/PII и audit retention. Не приемай, че access-control checks в gateway заменят checks в owning service.

## 6. Сценарий за диагноза

На 4 pods CPU=15%, p99=5s, Hikari pending=100, Oracle row lock wait висок; Redis hit ratio=92%, request retries=3. Екип предлага:
1. 20 pods;
2. увеличаване Hikari pool 5×;
3. още Redis;
4. trace + SQL/lock diagnosis, bounded concurrency и safe retry policy.

**Задача:** сравни всички, изведи 2 измерими хипотези, избери най-безопасния първи експеримент и rollback trigger. Не твърди improvement без load test.

**Практически вече готови доказателства:** [Fault Tolerance](../spring/reliability/fault-tolerance/README.md) · [Idempotency](../spring/reliability/idempotency/README.md) · [Kubernetes probes & rollout](../infrastructure/kubernetes/README.md) · [Threat Modeling](../spring/security/threat-modeling/README.md).
