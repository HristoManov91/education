# Retry + Timeout + Circuit Breaker + Bulkhead

Този модул е reliability laboratory върху **един и същ controllable downstream service**.

Целта е да не научим четири annotations, а да можем да отговорим:

```text
Какъв точно failure mode имам?
Кой pattern го адресира?
Какво НЕ решава?
Как взаимодейства с останалите policies?
Какъв resource/time budget изразходва?
```

Използваме Resilience4j 2.4.0 директно чрез Java decorators, а не annotations, за да бъде composition order-ът видим в кода.

---

# ВХОД В ТЕМАТА

## Реалният казус

Нашият service извиква downstream dependency.

Dependency-то може:

- временно да fail-не;
- да fail-ва постоянно;
- да стане много бавно;
- да приема твърде много concurrent calls;
- да остане unhealthy достатъчно дълго, че ние сами да го претоварим с retries.

Една дума `resilience` не описва всички тези проблеми.

```text
transient failure      → Retry
slow call / time budget → Timeout
repeated unhealthy dependency → Circuit Breaker
resource/concurrency saturation → Bulkhead
```

---

# Един downstream за всички експерименти

[`ControllableDownstreamService.java`](./src/main/java/bg/hristomanov/education/reliability/downstream/ControllableDownstreamService.java) може да бъде конфигуриран да:

```text
fail first N calls
fail permanently
sleep for N milliseconds
measure active concurrent calls
record interruption
```

Така сравняваме policy semantics върху един и същ dependency.

---

# README → код

| Pattern | Код | Test |
| --- | --- | --- |
| Downstream fixture | [`ControllableDownstreamService.java`](./src/main/java/bg/hristomanov/education/reliability/downstream/ControllableDownstreamService.java) | всички tests |
| Retry | [`RetryPolicyService.java`](./src/main/java/bg/hristomanov/education/reliability/service/RetryPolicyService.java) | [`RetryPolicyServiceTest.java`](./src/test/java/bg/hristomanov/education/reliability/RetryPolicyServiceTest.java) |
| Timeout | [`TimeoutPolicyService.java`](./src/main/java/bg/hristomanov/education/reliability/service/TimeoutPolicyService.java) | [`TimeoutPolicyServiceTest.java`](./src/test/java/bg/hristomanov/education/reliability/TimeoutPolicyServiceTest.java) |
| Circuit Breaker | [`CircuitBreakerPolicyService.java`](./src/main/java/bg/hristomanov/education/reliability/service/CircuitBreakerPolicyService.java) | [`CircuitBreakerPolicyServiceTest.java`](./src/test/java/bg/hristomanov/education/reliability/CircuitBreakerPolicyServiceTest.java) |
| Bulkhead | [`BulkheadPolicyService.java`](./src/main/java/bg/hristomanov/education/reliability/service/BulkheadPolicyService.java) | [`BulkheadPolicyServiceTest.java`](./src/test/java/bg/hristomanov/education/reliability/BulkheadPolicyServiceTest.java) |
| Composition | [`CombinedReliabilityService.java`](./src/main/java/bg/hristomanov/education/reliability/service/CombinedReliabilityService.java) | [`CombinedReliabilityServiceTest.java`](./src/test/java/bg/hristomanov/education/reliability/CombinedReliabilityServiceTest.java) |
| Configuration | [`ReliabilityConfiguration.java`](./src/main/java/bg/hristomanov/education/reliability/config/ReliabilityConfiguration.java) | tests |
| HTTP playground | [`ReliabilityLabController.java`](./src/main/java/bg/hristomanov/education/reliability/api/ReliabilityLabController.java) | [`reliability-demo.http`](./http/reliability-demo.http) |

---

# 1. Retry — transient failure recovery

## Problem shape

```text
attempt 1 → temporary network/service failure
attempt 2 → temporary failure
attempt 3 → success
```

Ако failure е transient, повторен attempt има реален шанс да успее.

[`RetryPolicyService.java`](./src/main/java/bg/hristomanov/education/reliability/service/RetryPolicyService.java) използва `maxAttempts=3`.

Важно:

> `maxAttempts=3` включва първоначалния call. Това НЕ означава 1 initial + 3 retries.

Тестът доказва:

```text
2 transient failures
+ maxAttempts=3
→ third call succeeds
→ downstream call count = 3
```

## Какво НЕ трябва да retry-ваме

В lab-а `PermanentDownstreamException` е ignored от Retry.

Примерни permanent/business errors:

- validation error;
- unauthorized;
- invalid request;
- resource definitely not found;
- business rule violation.

```text
permanent failure
→ fail fast
→ call count = 1
```

Retry върху permanent failure само:

- увеличава latency;
- увеличава load;
- скрива root cause;
- може да усили outage.

---

# 2. Backoff и jitter

Нашият lab използва малко fixed wait време, за да е бърз и deterministic.

Production retry често трябва да използва:

```text
exponential backoff
+ jitter
```

Причината:

```text
100 clients fail at T0
all retry exactly at T0 + 1s
→ retry storm
```

Jitter разпръсква attempts във времето.

Retry policy винаги трябва да има **retry budget**:

- max attempts;
- max elapsed time/deadline;
- bounded backoff;
- classification на retryable failures.

---

# 3. Retry и Idempotency

Предишният module е:

- [`Idempotency`](../idempotency/README.md)

Той идва преди Retry нарочно.

```text
Retry
+ non-idempotent side effect
= duplicate-risk multiplier
```

При write operation първо трябва да знаем:

> Повторният attempt може ли безопасно да се изпълни?

---

# 4. Timeout — caller time budget

Timeout отговаря на различен въпрос:

> **Колко дълго сме готови да чакаме този dependency?**

[`TimeoutPolicyService.java`](./src/main/java/bg/hristomanov/education/reliability/service/TimeoutPolicyService.java) използва 75 ms time budget.

```text
downstream delay = 300 ms
caller budget = 75 ms
→ timeout
```

Тестът доказва, че caller-ът се освобождава значително преди 300 ms.

---

# 5. Timeout НЕ означава, че side effect-ът е спрян

Това е критично.

При blocking Future cancellation:

```text
timeout
→ cancel(true)
→ interrupt worker
```

Но interruption е cooperative signal.

Ако downstream code:

- игнорира interrupt;
- вече е изпратил remote request;
- remote server вече processing-ва payment;

caller timeout НЕ връща света назад.

Затова:

```text
timeout
≠
remote operation definitely cancelled
```

Това директно се връзва с Idempotency.

---

# 6. Connect timeout, read timeout и operation deadline

В реален HTTP client може да имаме няколко budgets:

```text
connection establishment timeout
read/response timeout
overall operation deadline
```

Те не са взаимозаменяеми.

Една request операция с 4 downstream calls трябва да има и **global request budget**, а не всеки call да може независимо да изхарчи целия upstream timeout.

Вече имаме подобен mental model в Structured Concurrency labs.

---

# 7. Circuit Breaker — stop calling an unhealthy dependency

Retry казва:

> пробвай пак.

Circuit Breaker казва:

> recent evidence показва, че dependency-то е unhealthy; временно изобщо не го викай.

Resilience4j normal states:

```text
CLOSED
  ↓ failure/slow-call threshold
OPEN
  ↓ wait duration
HALF_OPEN
  ↓ probes
CLOSED or OPEN
```

Lab configuration:

```text
sliding window = last 4 calls
minimum calls = 4
failure threshold = 50%
half-open probes = 2
```

Тестът нарочно прави 4 transient failures.

След това:

```text
circuit = OPEN
next caller
→ CallNotPermittedException
→ downstream call count DOES NOT increase
```

Това е short-circuiting.

---

# 8. Minimum sample е важен

Circuit Breaker не трябва да open-ва след един случаен failure само защото threshold е 50%.

Затова имаме:

```text
minimumNumberOfCalls
```

Докато sample-ът е твърде малък, failure rate не се използва за state transition.

Production стойностите трябва да съответстват на traffic volume, а не да се копират от tutorial.

---

# 9. Slow-call rate

Circuit Breaker може да отчита не само exceptions, а и **бавни calls**.

Това е полезно, защото dependency може да бъде technically successful, но operationally unhealthy:

```text
HTTP 200
after 25 seconds
```

може да е също толкова разрушително за нашите ресурси.

---

# 10. Circuit Breaker НЕ е concurrency limit

Това е изрично важно и в Resilience4j документацията.

Sliding window size = 10 означава:

> пазим metrics за 10 calls.

Не означава:

> позволяваме максимум 10 concurrent calls.

При CLOSED circuit 100 callers могат да минат едновременно.

За concurrency isolation имаме Bulkhead.

---

# 11. Bulkhead — resource isolation

Името идва от корабните прегради:

```text
един наводнен compartment
≠
целият кораб потъва
```

Software mental model:

```text
Dependency A
→ own concurrency budget

Dependency B
→ own concurrency budget
```

Ако A стане slow, не искаме да изяде всички threads/connections/permits за B.

[`BulkheadPolicyService.java`](./src/main/java/bg/hristomanov/education/reliability/service/BulkheadPolicyService.java) позволява максимум **2 concurrent calls**.

Concurrency test:

```text
6 simultaneous callers
→ 2 admitted
→ 4 BulkheadFullException
→ max actual downstream concurrency <= 2
```

---

# 12. Semaphore vs Thread-Pool Bulkhead

Resilience4j има два practically relevant variants:

## Semaphore Bulkhead

- caller thread/virtual thread изпълнява operation-а;
- semaphore ограничава concurrent executions;
- няма допълнителна work queue по подразбиране.

Това използваме в lab-а.

## Fixed Thread-Pool Bulkhead

- отделен fixed thread pool;
- bounded queue;
- отделна scheduling/isolation boundary.

Не избираме thread-pool bulkhead автоматично.

При Java virtual threads semaphore bulkhead често е много естествен: не pool-ваме virtual threads, а **ограничаваме scarce downstream resource**.

---

# 13. Virtual Threads НЕ премахват Bulkhead нуждата

```text
1,000,000 cheap virtual threads
≠
1,000,000 DB connections
≠
1,000,000 downstream concurrent requests
```

Virtual threads решават JVM thread scalability.

Bulkhead решава external/resource concurrency budget.

Това се връзва директно с Project Loom bounded-concurrency lab-а.

---

# 14. Как ги комбинираме в този lab

[`CombinedReliabilityService.java`](./src/main/java/bg/hristomanov/education/reliability/service/CombinedReliabilityService.java) използва:

```text
Retry
  → CircuitBreaker
     → Bulkhead
        → TimeLimiter
           → downstream
```

Execution започва отвън навътре.

Следователно **всеки retry attempt**:

1. иска permission от Circuit Breaker;
2. иска Bulkhead permit;
3. получава собствен timeout;
4. извиква downstream.

---

# 15. Consequence: Circuit Breaker вижда physical attempts

Scenario:

```text
logical request
attempt 1 → transient failure
attempt 2 → transient failure
attempt 3 → success
```

При нашия order Circuit Breaker metrics виждат:

```text
failure
failure
success
```

Тестът проверява `recordedFailedCalls == 2`.

Това е съзнателна policy.

---

# 16. Alternative: Circuit Breaker outside Retry

Ако направим:

```text
CircuitBreaker
  → Retry
     → downstream
```

Circuit Breaker вижда **един logical outcome** след retry exhaustion/success.

Тогава transient failures, които retry-ът е recover-нал, могат да бъдат скрити от breaker failure rate.

Нито един order не е универсално правилен.

Въпросът е:

> Circuit Breaker health metrics трябва да описват physical attempts или logical operations?

---

# 17. Per-attempt timeout vs global timeout

Нашият order има TimeLimiter **inside Retry**.

Следователно:

```text
75 ms timeout
× up to 3 attempts
+ retry wait time
```

може да надхвърли 75 ms общо.

Тестът умишлено доказва, че slow downstream се извиква три пъти.

## Ако имаме upstream deadline

Често искаме:

```text
Global Deadline
  → Retry
     → per-attempt call
```

или remaining-time budget да се предава към всеки child attempt.

Това предотвратява:

```text
3 × local timeout > caller total budget
```

---

# 18. Защо Bulkhead е близо до downstream

Искаме permit-ът да защитава **реалния scarce operation**, а не целия logical retry lifecycle.

Ако caller чака backoff между attempts, няма причина през цялото време да държи downstream concurrency permit.

Нашата composition естествено освобождава permit след всеки attempt.

---

# 19. Какви exceptions брои Circuit Breaker

В lab-а Circuit Breaker брои:

- transient downstream failure;
- timeout.

Не брои:

- permanent/business failure;
- Bulkhead saturation.

Защо?

```text
invalid request
≠ downstream unhealthy

our local bulkhead is full
≠ downstream unhealthy
```

Failure classification е част от reliability design-а.

---

# 20. Bulkhead rejection retry-able ли е?

В lab-а: **не**.

Ако локалният Bulkhead е full, автоматичен immediate retry често само увеличава contention-а.

Може да има случаи с delayed retry/backpressure, но това трябва да е explicit capacity policy.

---

# 21. Fallback

Circuit Breaker примери често веднага показват fallback.

Но fallback има смисъл само ако имаме **семантично валиден алтернативен резултат**.

Добри кандидати:

- stale cache за non-critical catalog;
- degraded optional feature;
- default recommendation.

Опасни fallback-и:

- `return emptyList()` при critical authorization;
- `return 0` при account balance;
- скриване на payment failure като success.

Fallback е business decision, не задължителна част от Circuit Breaker.

---

# 22. Observability

За production искаме metrics/events за:

## Retry

- attempts;
- successes after retry;
- exhausted retries;
- wait/backoff duration.

## Timeout

- timeout count;
- latency percentiles;
- cancelled/interrupted work.

## Circuit Breaker

- state transitions;
- failure rate;
- slow-call rate;
- rejected calls.

## Bulkhead

- max concurrent use;
- rejected calls;
- wait duration;
- queue depth при thread-pool variant.

Pattern без observability е труден за tuning.

---

# 23. Configuration numbers не се копират от tutorial

Нашите values са малки, за да имаме бързи tests:

```text
75 ms timeout
4-call circuit window
2 bulkhead permits
3 retry attempts
```

Production config трябва да идва от:

- latency SLO;
- downstream capacity;
- request deadline;
- error distribution;
- traffic rate;
- recovery characteristics;
- connection-pool size.

Tutorial threshold не е production recommendation.

---

# 24. Spring Cloud vs direct Resilience4j

Spring Cloud CircuitBreaker 5.0.x предлага abstraction върху circuit-breaker implementations и поддържа Resilience4j, Spring Retry и Spring Framework Retry.

Този lab умишлено използва direct Resilience4j core APIs.

Причината:

> Искаме decorators и composition order-ът да се виждат директно, преди framework abstraction да ги скрие.

След като mental model-ът е ясен, Spring Cloud abstraction е много по-лесен за разбиране.

---

# 25. Resilience4j version note

Lab-ът използва Resilience4j **2.4.0**, текущия latest GitHub release към момента на урока.

Покрити core modules:

- `resilience4j-retry`;
- `resilience4j-timelimiter`;
- `resilience4j-circuitbreaker`;
- `resilience4j-bulkhead`.

Не използваме starter/AOP annotations, за да не смесваме pattern semantics с framework proxy semantics.

---

# Как да стартираме

От root:

```bash
mvn -pl spring/reliability/fault-tolerance -am test
```

Стартиране:

```bash
mvn -pl spring/reliability/fault-tolerance spring-boot:run
```

HTTP scenarios:

- [`http/reliability-demo.http`](./http/reliability-demo.http)

---

# Какво доказват тестовете

## Retry

```text
2 transient failures
→ third attempt succeeds

permanent failure
→ one attempt only
```

## Timeout

```text
300 ms downstream
→ 75 ms budget exceeded
→ caller released early
→ cooperative interruption observed
```

## Circuit Breaker

```text
4 failed calls
→ OPEN
→ next call rejected before downstream

HALF_OPEN + healthy probes
→ CLOSED
```

## Bulkhead

```text
6 concurrent callers
→ 2 admitted
→ 4 rejected
→ downstream max concurrency <= 2
```

## Combined

```text
Retry outside CircuitBreaker
→ breaker sees every physical attempt

TimeLimiter inside Retry
→ timeout is per attempt
```

---

# Mental model за запомняне

1. **Retry дава втори шанс на transient failure.**
2. **Timeout ограничава caller waiting budget.**
3. **Circuit Breaker спира calls към доказано unhealthy dependency.**
4. **Bulkhead ограничава колко от нашите ресурси може да изяде една dependency.**
5. **Circuit Breaker не е concurrency limiter.**
6. **Virtual threads не увеличават downstream capacity.**
7. **Policy order променя semantics.**
8. **Per-attempt timeout не е global deadline.**
9. **Retry трябва да е съобразен с idempotency.**
10. **Failure classification е част от design-а.**

---

# Как да разпозная казуса

```text
[ ] Има ли transient failure, който често изчезва при повторен call?
[ ] Има ли caller deadline?
[ ] Slow dependency задържа ли ресурси?
[ ] Продължаваме ли да hammer-ваме dependency по време на outage?
[ ] Една dependency може ли да изчерпи всички concurrency permits/connections?
[ ] Retry-ваме ли permanent errors?
[ ] Retry operation idempotent ли е?
[ ] Circuit failure metrics броят ли client/business errors?
[ ] Local overload брои ли се погрешно като downstream failure?
[ ] Общият retry time budget влиза ли в upstream deadline-а?
```

---

# Code-review checklist

```text
[ ] Какъв failure mode решава всеки policy?
[ ] Retryable exceptions/results explicit ли са?
[ ] Има ли max attempts + backoff + jitter strategy?
[ ] Има ли global request deadline?
[ ] Timeout cancellation cooperative ли е?
[ ] Circuit minimum sample разумен ли е за traffic-а?
[ ] Slow calls наблюдават ли се?
[ ] Circuit Breaker и Bulkhead бъркат ли се като едно и също?
[ ] Bulkhead limit свързан ли е с real downstream capacity?
[ ] Virtual threads пазят ли external resource limit?
[ ] Decoration/order semantics документирани ли са?
[ ] Fallback business-correct ли е?
[ ] Има ли metrics за tuning?
```

---

# Упражнения

1. Смени Retry wait-а с exponential backoff + jitter.
2. Постави Circuit Breaker outside Retry и сравни metrics.
3. Добави global deadline around Retry и сравни с per-attempt timeout.
4. Добави fallback за non-critical catalog response.
5. Направи permanent error да не влияе на circuit state.
6. Сравни Semaphore Bulkhead и ThreadPoolBulkhead.
7. Вържи Bulkhead limit с simulated DB connection pool size.
8. Добави Micrometer metrics.
9. Направи slow-call threshold да отвори breaker без exceptions.
10. Свържи Retry с Idempotency lab-а и докажи safe write retries.

---

# Оригинални източници

- Resilience4j Retry: https://resilience4j.readme.io/docs/retry
- Resilience4j TimeLimiter: https://resilience4j.readme.io/docs/timeout
- Resilience4j CircuitBreaker: https://resilience4j.readme.io/docs/circuitbreaker
- Resilience4j Bulkhead: https://resilience4j.readme.io/docs/bulkhead
- Resilience4j releases: https://github.com/resilience4j/resilience4j/releases
- Spring Cloud CircuitBreaker: https://docs.spring.io/spring-cloud-circuitbreaker/reference/index.html
- AWS Builders Library — Timeouts, retries and backoff with jitter: https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/
- AWS Builders Library — Avoiding insurmountable queue backlogs: https://aws.amazon.com/builders-library/avoiding-insurmountable-queue-backlogs/
- Microsoft Azure Architecture Center — Circuit Breaker: https://learn.microsoft.com/azure/architecture/patterns/circuit-breaker
- Microsoft Azure Architecture Center — Bulkhead: https://learn.microsoft.com/azure/architecture/patterns/bulkhead

---

# Изходен въпрос

Когато downstream стане unreliable, не питай:

> Коя resilience annotation да сложа?

Питай:

> **Failure-ът transient ли е, slow ли е, dependency-то доказано unhealthy ли е, или ние просто сме изчерпали собствения си concurrency budget — и какъв е общият time/resource budget на операцията?**
