# P1 Production API Resilience — когато чуждо API повали нашите workers

> **Devoxx Belgium 2026 / Ines Panker — [Their API Went Down. So Why Is My App on Fire?](https://www.youtube.com/watch?v=PGpKfdXZrpc)**  
> **Позиция:** разширение на [Retry / Timeout / Circuit Breaker / Bulkhead](./README.md), не нов курс и не замяна на вече написаните Resilience4j лаборатории.  
> **Технологии:** Java 25, Spring Boot 4.1.1, Redis (shared state), JDBC + H2 (тест за transactional checkpoints), JUnit 5.  
> **Приоритет:** P1 за production reliability; диагностика, не автоматично усвоени знания.  
> **Източник на конкретните patterns:** шестте статии в [авторската API Resilience серия](https://www.ines-panker.com/talk/api-resilience/). Тук адаптираме Python/Celery примерите към Java; НЕ претендираме за буквален превод или идентични production семантики.

## ВХОД: „API-то им падна“ не трябва да означава „нашата система спря“

Нашият SaaS синхронизира данни от различни third-party API-та за множество tenants. Имаме 20 background workers. При нормален HTTP response (200 ms) те могат теоретично да приключат около 100 calls/sec, ако работата се състои само от едно такова блокиращо извикване. Ако downstream latency стане 8 s, capacity пада до около 2.5 calls/sec. При 20 нови calls/sec backlog-ът расте приблизително с 17.5 calls/sec (~1050/min).

**Важно:** това е опростен капацитетен модел, не измерен резултат; реалните HTTP timeouts, queueing, retries, scheduling и DB операции могат да променят числата.

```text
Scheduler / broker → 500 background tasks ─┬─ Worker 1 → slow GitHub API
                                            ├─ Worker 2 → slow GitHub API
                                            ├─ ...
                                            └─ Worker 20 → slow GitHub API
                    ↓
            all workers occupied
                    ↓
          unrelated jobs cannot run
```

`try/catch` предотвратява необработен exception, но не освобождава автоматично worker. Общият retry budget, deadline, Circuit Breaker и Bulkhead вече са обяснени в [основния курс](./README.md). Сега решаваме **други четири** production проблема, които остават дори след тези механизми.

## README → production-like код → изпълними доказателства

| Сценарий | Код | Проверка |
| --- | --- | --- |
| HTTP 429, tenant isolation, Retry-After | [SharedRateLimitClient.java](./src/main/java/bg/hristomanov/education/reliability/externalapi/SharedRateLimitClient.java), [RedisCooldownStore.java](./src/main/java/bg/hristomanov/education/reliability/externalapi/RedisCooldownStore.java) | [SharedRateLimitClientTest.java](./src/test/java/bg/hristomanov/education/reliability/externalapi/SharedRateLimitClientTest.java), [RedisCooldownStoreTest.java](./src/test/java/bg/hristomanov/education/reliability/externalapi/RedisCooldownStoreTest.java) |
| Resumable pagination, atomic checkpoint | [ResumableSync.java](./src/main/java/bg/hristomanov/education/reliability/externalapi/ResumableSync.java), [JdbcSyncCheckpointStore.java](./src/main/java/bg/hristomanov/education/reliability/externalapi/JdbcSyncCheckpointStore.java) | [ResumableSyncTest.java](./src/test/java/bg/hristomanov/education/reliability/externalapi/ResumableSyncTest.java) |
| Stale tasks / bounded queue | [LoadSheddingQueue.java](./src/main/java/bg/hristomanov/education/reliability/externalapi/LoadSheddingQueue.java) | [LoadSheddingQueueTest.java](./src/test/java/bg/hristomanov/education/reliability/externalapi/LoadSheddingQueueTest.java) |
| Revoked token, manual recovery | [CredentialRecovery.java](./src/main/java/bg/hristomanov/education/reliability/externalapi/CredentialRecovery.java), [RedisCredentialSuspensionStore.java](./src/main/java/bg/hristomanov/education/reliability/externalapi/RedisCredentialSuspensionStore.java) | [CredentialRecoveryTest.java](./src/test/java/bg/hristomanov/education/reliability/externalapi/CredentialRecoveryTest.java), [RedisCooldownStoreTest.java](./src/test/java/bg/hristomanov/education/reliability/externalapi/RedisCooldownStoreTest.java) |

**Извикванията са production-like Java classes, а не готов интеграционен SDK:** не са автоматично включени в примерните REST endpoints. Inject-вай конкретните adapters в своя application service и конфигурирай provider policies. Няма нов публичен HTTP endpoint, затова проверките са runnable JUnit тестове.

---

# 1. HTTP 429 — shared cooldown без blocking sleep

## Наивно, но изкушаващо

```java
if (response.statusCode() == 429) {
    Thread.sleep(60_000);  // 500 tasks * 1 min = заети worker slots
}
```

Всеки worker вижда собствения си 429 и заема ресурси, докато чака. Ако имаме много JVM instances, всички повтарят независимо. Още по-лошо: един клиент може да е rate-limited, но други имат свободна квота.

## Good: кеширай СЪСТОЯНИЕТО на 429, не целия HTTP response

```text
Worker A / tenant1-tokenX   ──request──▶ provider
                                    ◀──429 / Retry-After: 60
                                      ↓
                              Redis key(scope hash)
                              TTL = 60 seconds
                                      ↓
Worker B / tenant1-tokenX → Redis PTTL > 0 → SKIP remote → synthetic 429
Worker C / tenant2-tokenX → different key → remote CALL continues
```

[`SharedRateLimitClient.call(...)`](./src/main/java/bg/hristomanov/education/reliability/externalapi/SharedRateLimitClient.java) първо чете [`CooldownStore.remaining(...)`](./src/main/java/bg/hristomanov/education/reliability/externalapi/SharedRateLimitClient.java). Ако има положителен TTL, връща **синтетичен 429** и задава `fromSharedCooldown=true`; caller-ът решава да отложи задачата, не да спи.

При реален 429 парсваме `Retry-After` (целочислени секунди **или** HTTP-date), при невалиден header използваме bounded fallback и прилагаме maximum TTL. Не кешираме body, cookies, request headers или сурови credentials.

`Scope.of(provider, tenantId, credential)` изгражда SHA-256 fingerprint; Redis key не съдържа token. **Provider + tenant + конкретна квота** се смятат за обща единица на rate limiting. В реален provider лимитът може да е per-user, per-installation, per-app, per-IP или комбинация — identity се адаптира според API contract-а. Token hash в ключа намалява експозицията, но не е криптографска защита на цялата система; Redis ACL/TLS и secret hygiene остават важни.

[`RedisCooldownStore`](./src/main/java/bg/hristomanov/education/reliability/externalapi/RedisCooldownStore.java) използва кратък **atomic Lua script**: при два едновременни 429 запазва по-дългия оставащ cooldown. Слепият `SET key EX ttl` може да скъси вече съществуващото изчакване.

**Какво доказваме:** два workers споделят 429, друг tenant/token не се блокира, header date/delta/fallback/maximum са ясни, Redis TTL не се скъсява. Виж [реалния Redis test](./src/test/java/bg/hristomanov/education/reliability/externalapi/RedisCooldownStoreTest.java).

### Ограничения

- Това **не е строг rate limiter**. Два едновременни първи request-а може да излязат преди който и да е да види 429. За строг quota enforcement ни трябва proactive distributed token bucket/permit.
- Redis outage: adapter-ът **не маскира exception** и не прави silent fail-open. Caller избира explicit политика — fail-open с наблюдение и риск от допълнителни 429, или fail-closed за строги quotas.
- Синтетичният 429 е **local control signal**, не гаранция, че provider в този момент пак би върнал 429. Не го отчитай като истински provider failure за Circuit Breaker health metrics.
- Автоматичен retry НЕ трябва да игнорира `Retry-After`, общите deadlines, max attempts и idempotency.
- TTL зависи от Redis server clock; в тестовете реалният clock се използва само за expiration, без `sleep` assertions.

---

# 2. Възстановяване от страница 47 — checkpoint и data в една транзакция

## BAD: всеки retry започва от първата страница

```java
for (int page = 1; page <= 50; page++) {
    fetchAndSave(page);
}
```

Ако страница 47 се провали (429/timeout), следващият worker може да започне от 1, да изхарчи rate limit и да дублира вече обработени записи.

## Good: durable page checkpoint

```text
Job starts: next_page=1
fetch(1) → atomically persist records + next_page=2
...
fetch(46) → commit records + next_page=47
fetch(47) → HTTP 429 → no commit
process restart → read next_page=47 → fetch(47)
...
fetch(50, hasMore=false) → commit data + complete=true
```

[`ResumableSync.run(...)`](./src/main/java/bg/hristomanov/education/reliability/externalapi/ResumableSync.java) не носи page number като локално state между рестарти. [`JdbcSyncCheckpointStore.commitPage(...)`](./src/main/java/bg/hristomanov/education/reliability/externalapi/JdbcSyncCheckpointStore.java) прави:

1. `SELECT ... FOR UPDATE` върху реда на job-а; един worker е owner на commit в даден момент;
2. `UPDATE` или `INSERT` на items с `PRIMARY KEY (job_id, external_id)`;
3. в същата DB транзакция запис на `next_page` и `complete`;
4. **commit**; при SQL error или race — **rollback**.

**Защо checkpoint не трябва да се запише първи?** Ако процесът умре след `next_page=48`, но преди item insert, записите от страница 47 ще бъдат пропуснати. И обратното: ако имаме записани items, но стар checkpoint, unique key/idempotent upsert прави повторната обработка безопасна.

[`ResumableSyncTest`](./src/test/java/bg/hristomanov/education/reliability/externalapi/ResumableSyncTest.java) доказва с реални H2 транзакции: failure на page 47 → точно там започва повторният run; SQL exception във втория item връща **всички** rows и checkpoint за страницата; stale конкурентен commit не напредва два пъти; повторна external identity не генерира duplicate.

### Коректност в production

- Page-number pagination **не гарантира stable snapshot**: insert/delete между retries може да разместват страниците. Предпочитай provider-supported stable cursor, snapshot ID или watermark, когато са налични. При cursor-based API checkpoint трябва да пази **точния next cursor**.
- `createJob(jobId)` тук е explicit setup, не пълна multi-worker job-claim система. В production добави lease/ownership, expiration, operator actions, retry scheduling, metrics и DB уникалност.
- Item writes и checkpoint са в **една база**. Ако item side effect е външен HTTP/Kafka, трябва idempotency key/Outbox, защото не можеш да го атомизираш чрез локален JDBC commit.
- H2 доказва basic rollback/locking semantics; SQL dialect, isolation, deadlocks, timestamp mapping и lock behavior се проверяват отделно за PostgreSQL/Oracle.

---

# 3. Load shedding — кога е правилно да НЕ изпълним задача

## BAD: „първо обработваме всяка стара периодична задача“

След outage имаме 10 000 изчакващи `refresh current metrics` jobs. Когато API оздравее, workers наваксват вчерашните показатели, докато актуалните стоят отзад.

**Въпросът:** остарява ли стойността на задачата и ще я замести ли следващата периодична задача?

[`LoadSheddingQueue`](./src/main/java/bg/hristomanov/education/reliability/externalapi/LoadSheddingQueue.java) моделира две категории:

- `REPLACEABLE_REFRESH` — задача с explicit `expiresAt`; при изтичане се изхвърля, без да изпълнява external call.
- `CRITICAL` — никога не се изхвърля без ясно решение на caller-а. При пълна опашка може да измести заменим refresh; ако няма място, връща `REJECTED_CRITICAL`, **не ACK success**.

```text
Queue capacity 2:
  refresh(metrics) + critical(order-A)

new critical(order-B)
→ evict refresh(metrics) [shedForCapacity++]
→ queue: critical(order-A), critical(order-B)

new critical(order-C)
→ REJECTED_CRITICAL [upstream must retain/retry durably]
```

При проверката преди изпълнение task expiry се проверява **отново**. Има observability counters: `expired`, `shedForCapacity`, `rejectedCritical`, `executed`.

Виж [LoadSheddingQueueTest](./src/test/java/bg/hristomanov/education/reliability/externalapi/LoadSheddingQueueTest.java) за deterministic `Clock`-based тестове без реални изчаквания.

### Ограничения

Това е **in-memory учебен queue**, не production broker. Accepted CRITICAL jobs могат да се изгубят при crash на процеса, освен ако реалният caller ги държи в durable storage до acknowledgement. В production приложи същата classification/TTL policy в scheduler/queue/consumer с durable acknowledgements. Не expire-вай payments, legal events, audit или задължителни business operations само за да освободиш workers.

Също така пропуснатата задача не е „изпълнена успешно“ — изисква own status, logs/metrics и допустима бизнес семантика.

---

# 4. Счупени credentials — прекрати безсмислените retries и поискайте човешко действие

## Наивен подход

```text
external provider → 401
Retry 1 → 401
Retry 2 → 401
next scheduled job → 401
... potentially forever
```

Ако token е revoked/expired и е необходима **ръчна reauthorization**, retries не го поправят. Но **не всеки 401/403 е dead credential**, а refreshable access tokens често се възстановяват автоматично; provider-specific response semantics са задължителни.

[`CredentialRecovery`](./src/main/java/bg/hristomanov/education/reliability/externalapi/CredentialRecovery.java) получава explicit `Predicate<Reply> providerSpecificInvalidToken` (provider-aware класификатор). Само ако той разпознае доказан, нерешим автоматично проблем:

1. `SuspensionStore.suspend(connection)` извършва atomic state transition по provider + tenant + integration;
2. [`RedisCredentialSuspensionStore`](./src/main/java/bg/hristomanov/education/reliability/externalapi/RedisCredentialSuspensionStore.java) споделя флага между workers, без автоматичен TTL;
3. уведомява operator/customer чрез `ActionRequiredNotifier` **само при първата transition**;
4. последващи calls връщат explicit degraded state `503`, без да викат remote;
5. `credentialsReplacedAndVerified` отключва само след проверена подмяна.

```text
Tenant A / integration 1:
  invalid_token confirmed
   → suspension + one-time action-required signal
   → next calls: local 503, no repeated unauthorized remote attempts

Tenant B / integration 1:
  unrelated identity → continues calling remote
```

[`CredentialRecoveryTest`](./src/test/java/bg/hristomanov/education/reliability/externalapi/CredentialRecoveryTest.java) доказва one-shot alert, изолация по tenant, липса на блокиране при generic 401/403/429/503 и explicit recovery. Реалният Redis test доказва cross-instance видимост на flag-а.

### Коректност и безопасност

- **False-positive** отключването/блокирането е опасно: provider може да върне 401 от временна грешка. Препоръчваме verified classification, token refresh ако е поддържан, наблюдение на provider-wide spikes и manual override.
- `Redis SETNX → notifier` **не е atomic** спрямо email/broker. Ако JVM падне между тях, сигналът може да се изгуби. В production използвай persistent incident record + [Transactional Outbox](../outbox-inbox/README.md) (виж правилния линк към [Outbox/Inbox module](../../messaging/outbox-inbox/README.md)) или надеждна re-notification reconciliation.
- Redis без TTL не значи гарантирана вечна устойчивост: configure Redis persistence/replication и reconciliation. Няма магически exactly-once notifications.
- Customer-facing съобщение трябва да казва **какво да направи и къде да подмени token**, без да показва credentials. Не позволявай на произволен HTTP caller да извика `credentialsReplacedAndVerified` — това е privileged operation след verify.

---

# 5. Как четирите сценария се комбинират

```text
Scheduled job / durable queue
   ↓ classification + expiration (load shedding)
   ↓ credential recovery guard (confirmed invalid token => suspended)
   ↓ distributed 429 cooldown (local synthetic 429 if TTL active)
   ↓ Bulkhead / global deadline / bounded Retry + Circuit Breaker
   ↓ Remote API
   ↓ Sync worker: each successful page + checkpoint in one transaction
   ↓ Ack / schedule retry with correct delay and idempotency
```

**Порядъкът не е автоматичен:** ако synthetic 429 преминава през Circuit Breaker като remote failure, ще отвори breaker по грешна причина. Реалните network errors, local rate-limit skips и permanent invalid credentials трябва да са отделни категории с различни retry/metric policies. Retry, който моментално повтаря при `fromSharedCooldown=true`, също е bug.

## Как да стартираме лабораториите

**Prerequisites:** JDK 25, Maven, за Redis тестовете — Redis на `localhost:6379`. [GitHub CI](../../../.github/workflows/verify.yml) вече стартира Redis service и задава `RUN_REDIS_TESTS=true`. Локално без тази environment променлива Redis tests се маркират SKIPPED, не PASS.

```bash
# Без нужда от Redis server: обикновените тестове + H2/JDBC
mvn -pl spring/reliability/fault-tolerance -am test

# С реален локален Redis service на localhost:6379:
RUN_REDIS_TESTS=true mvn -pl spring/reliability/fault-tolerance -am test
```

Тези класове **не са свързани към реално GitHub API** и не създават скрити външни заявки. Redis тестовете ползват random scope/integration identifiers, за да не пресичат други namespaces. Няма credentials в тестовете.

**Какво да гледаш в test output:**

| Тест | Проверка |
| --- | --- |
| `SharedRateLimitClientTest` | 429 → second worker skips; друг tenant/token минава |
| `RedisCooldownStoreTest` | Redis atomic TTL max и shared credential state между два adapters |
| `ResumableSyncTest` | failure page 47 → resume page 47; partial SQL failure rolls back |
| `LoadSheddingQueueTest` | expired metrics skip, critical не се губи тихо |
| `CredentialRecoveryTest` | confirmed invalid token → one-shot signal и scoped suspension |

Ако CI е зелен, това доказва **тези сценарии** в тестовата среда. Не доказва production readiness на Redis persistence, provider semantics, distributed jobs или бизнес policy-та.

## Production observability checklist

- [ ] `remote_requests_total` по provider, status, outcome: real vs skipped; **без token в tags**.
- [ ] `rate_limit_active`, remaining TTL, Redis errors, invalid/missing headers.
- [ ] sync checkpoint age, current page/cursor, retries, failed pages, processed records, lag.
- [ ] queue depth/age, shed expired/capacity, rejected critical work, durable queue ACK state.
- [ ] confirmed invalid credentials, manual action required, false positives, repaired integrations.
- [ ] upstream global deadline, worker saturation, Bulkhead rejections, Circuit Breaker metrics.
- [ ] tenant-level диагностика в защитени logs, не безконтролно high-cardinality metric labels.
- [ ] safe retries при POST/side effects и ясна degraded-mode политика.
- [ ] test за outage, recovery и повторна обработка при concurrent workers.

## Кога да НЕ въвеждаме тези механизми

- Single-node integration с десетина редки requests и без реален rate-limit не се нуждае задължително от Redis.
- Pagination с един файл и възможност за евтин clean restart може да няма нужда от DB checkpoint.
- Load shedding е неприемливо за critical non-replaceable business work без durable ownership.
- Suspension на credentials без надеждна класификация и manual recovery UX може да причини повече вреди от самата повреда.

## Финал: отговорът за Senior code review

> „Не лекувам чужд outage само с catch и retry. Изчислявам нашите worker и downstream capacity budgets, класифицирам HTTP грешките, споделям rate-limit cooldown по правилната quota identity, осигурявам атомарни checkpoints за дълги синхронизации, изхвърлям само заменими остарели задачи и прекъсвам доказано непоправими auth retries с видим recovery flow. Отделям локалното ограничение от реалния health на provider-а и доказвам поведението с интеграционни и failure тестове.“

### Recall (за бъдеща проверка, без да отбелязваме „усвоено“)

1. Защо `Retry-After` трябва да се пази по **quota scope**, а не на ниво цялото приложение?
2. Защо `SELECT FOR UPDATE` + checkpoint в същата транзакция предотвратяват загуба на страница, но не и offset pagination drift?
3. Защо `SHED_EXPIRED` е допустимо за периодични метрики, но недопустимо за payment?
4. Защо 401 не трябва да спира автоматично интеграцията и как трябва да изглежда reliable incident notification?

## Оригинални и официални източници

- [Ines Panker — Devoxx Belgium 2026, *Their API Went Down. So Why Is My App on Fire?*](https://www.youtube.com/watch?v=PGpKfdXZrpc)
- [Devoxx — официална програма/описание](https://m.devoxx.com/events/dvbe26/talks/7035/their-api-went-down-so-why-is-my-app-on-fire)
- [Авторска серия с всички шест публикации](https://www.ines-panker.com/talk/api-resilience/)
- [Caching HTTP 429 in Redis](https://www.ines-panker.com/2026/02/06/api-resilience-caching-429.html)
- [Resumable pagination / page 47](https://www.ines-panker.com/2026/02/24/api-resilience-resumable-pagination.html)
- [Load shedding, task expiry](https://www.ines-panker.com/2026/09/23/api-resilience-load-shedding.html)
- [Expired/revoked token and human recovery](https://www.ines-panker.com/2026/09/24/api-resilience-expired-tokens.html)
- [RFC 9110 — HTTP semantics; `Retry-After`](https://www.rfc-editor.org/rfc/rfc9110.html)
- [Spring Data Redis — reference](https://docs.spring.io/spring-data/redis/reference/)
- [Spring Boot 4.1 — Redis autoconfiguration](https://docs.spring.io/spring-boot/4.1/reference/data/nosql.html)
- [Resilience4j reference](https://resilience4j.readme.io/)
- [PostgreSQL — explicit locking](https://www.postgresql.org/docs/current/explicit-locking.html)
- [Oracle — `SELECT ... FOR UPDATE`](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/SELECT.html)
