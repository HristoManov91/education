# Caching strategies — Cache-Aside, Write-Through, Write-Behind, Refresh-Ahead и Spring Cache

Този модул превръща видеото **[Caching Strategies Explained | How Netflix, Amazon & Uber Use Caching | #07](https://www.youtube.com/watch?v=sPA2c0DE6QY)** на Java Techie в изпълнима Spring/Java лаборатория.

Видеото дава добрата архитектурна рамка: **кога да кешираме, какво да кешираме, Cache-Aside, Write-Through, Write-Behind / Write-Back, Refresh-Ahead, freshness и consistency**. Тук надграждаме материала с нещата, които обикновено създават production проблемите:

- stale data (остарели данни);
- cache invalidation (инвалидиране на вече невалидна стойност);
- TTL, expiration и eviction;
- cache stampede / thundering herd;
- локален срещу distributed cache;
- failure windows при dual writes;
- Spring `@Cacheable`, `@CachePut`, `@CacheEvict` и `sync=true`;
- proxy/self-invocation pitfall при Spring Cache;
- observability чрез hit/miss counters.

---

# ВХОД В ТЕМАТА

## 1. Реалният казус: product catalog под натоварване

Имаме endpoint:

```text
GET /products/1
```

Продуктът се променя сравнително рядко, но се чете хиляди пъти.

Без cache flow-ът е:

```text
request
  ↓
ProductService
  ↓
database / remote store
  ↓
Product
```

Ако едно DB четене струва примерно 35 ms и получим 2 000 еднакви reads за популярен продукт, правим 2 000 почти идентични операции към source of truth (източника на истината).

На малък local demo това изглежда безобидно. В production започват:

- повече latency;
- повече database connections;
- по-нисък throughput;
- по-висока цена;
- риск от overload точно при най-популярните keys.

Логичната идея е:

> „Ще сложим cache и готово.“

Точно тук започва истинската тема, защото **cache-ът не е само по-бърз Map**. Трябва да решим кой го попълва, кой го обновява, кога стойността става невалидна и какво правим при failure.

## 2. Защо проблемът е повече от performance

Неправилният cache може да върне грешни business данни.

Пример:

```text
10:00 cache = price 199.90
10:01 DB    = price 249.90
10:02 cache = price 199.90  ← stale
```

Това може да е:

- стара цена;
- стар статус на поръчка;
- стар permission;
- вече невалидна конфигурация;
- стар inventory count.

Затова caching има две отделни цели:

1. **performance/scalability** — по-малко expensive reads;
2. **correctness/freshness** — приемливо ли е да върнем стара стойност и за колко време?

## 3. Какво ще научим

След лабораторията трябва да можем да:

- различаваме cache store от caching strategy;
- обясним read/write flow-а на основните стратегии;
- разпознаем stale-data bug;
- изберем кога invalidation е по-подходящ от direct cache update;
- разберем какво печелим и губим при write-behind;
- различаваме expiration, eviction и refresh;
- обясним защо local Caffeine cache не е distributed cache;
- разпознаем cache stampede;
- използваме Spring Cache abstraction без да приемаме, че annotation-ите решават consistency автоматично;
- избираме стратегия според business freshness requirements, а не според това кое API е най-лесно.

## 4. Mental model

Най-важното правило:

> **Cache technology казва къде държим копието. Caching strategy казва кой, кога и в какъв ред синхронизира копието със source of truth.**

Caffeine, Redis, Hazelcast и другите технологии не определят автоматично дали системата ни е Cache-Aside, Write-Through или Write-Behind.

---

# README → код

| Концепция | Production-like пример | Доказателство |
| --- | --- | --- |
| Baseline без cache | [`NoCacheProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/NoCacheProductService.java) | [`CacheAsideProductServiceTest.java`](./src/test/java/bg/hristomanov/education/cache/service/CacheAsideProductServiceTest.java) |
| BAD stale cache | [`NaiveCacheAsideProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/NaiveCacheAsideProductService.java) | [stale-data test](./src/test/java/bg/hristomanov/education/cache/service/CacheAsideProductServiceTest.java) |
| Cache-Aside | [`CacheAsideProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/CacheAsideProductService.java) | [`CacheAsideProductServiceTest.java`](./src/test/java/bg/hristomanov/education/cache/service/CacheAsideProductServiceTest.java) |
| Write-Through | [`WriteThroughProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/WriteThroughProductService.java) | [`WriteStrategiesTest.java`](./src/test/java/bg/hristomanov/education/cache/service/WriteStrategiesTest.java) |
| Write-Behind | [`WriteBehindProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/WriteBehindProductService.java) | [`WriteStrategiesTest.java`](./src/test/java/bg/hristomanov/education/cache/service/WriteStrategiesTest.java) |
| Refresh-Ahead | [`RefreshAheadProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/RefreshAheadProductService.java) | [`RefreshAheadProductServiceTest.java`](./src/test/java/bg/hristomanov/education/cache/service/RefreshAheadProductServiceTest.java) |
| Spring Cache | [`SpringCacheProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/SpringCacheProductService.java) | [`SpringCacheProductServiceTest.java`](./src/test/java/bg/hristomanov/education/cache/service/SpringCacheProductServiceTest.java) |
| Caffeine store wrapper | [`ProductCache.java`](./src/main/java/bg/hristomanov/education/cache/cache/ProductCache.java) | strategy tests |
| Observable source of truth | [`InMemoryProductRepository.java`](./src/main/java/bg/hristomanov/education/cache/repository/InMemoryProductRepository.java) | repository read/write counters |
| HTTP playground | [`CachingLabController.java`](./src/main/java/bg/hristomanov/education/cache/controller/CachingLabController.java) | [`cache-demo.http`](./http/cache-demo.http) |
| Spring/Caffeine config | [`CacheConfiguration.java`](./src/main/java/bg/hristomanov/education/cache/config/CacheConfiguration.java) | Spring integration test |

---

# Термини

| Термин | Значение |
| --- | --- |
| cache hit | key-ът е намерен в cache-а |
| cache miss | key-ът липсва и трябва да се зареди от source of truth |
| source of truth | authoritative store — обикновено DB или external service |
| stale data | cache копието вече не съответства на source of truth |
| TTL | колко дълго стойността може да остане cache-ната според time policy |
| expiration | entry става невалиден по времево правило |
| eviction | entry се премахва, например заради размер/pressure/policy |
| invalidation | приложението изрично премахва вече невалиден entry |
| refresh | подмяна на cached value с нова стойност |
| cache stampede | много callers виждат един miss едновременно и всички товарят source of truth |
| eventual consistency | различни копия могат временно да се разминават, но по-късно се изравняват |

---

# 1. Baseline: без cache

[`NoCacheProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/NoCacheProductService.java)

```text
request 1 → repository
request 2 → repository
request 3 → repository
```

Това е контролният вариант.

Важно е да не демонизираме липсата на cache. Ако операцията е евтина, рядка или freshness изискването е много строго, **no cache може да е най-доброто решение**.

Cache добавя state, invalidation, memory usage и failure modes. Не го добавяме „по принцип“.

---

# 2. Основните стратегии

| Стратегия | Read miss | Write | Freshness | Основен trade-off |
| --- | --- | --- | --- | --- |
| Cache-Aside | app чете DB и populate-ва cache | DB → invalidate cache | добра при правилна invalidation | application кодът управлява consistency |
| Write-Through | чете cache/DB | DB + cache синхронно | immediate reads са лесни за разбиране | write latency + partial failure |
| Write-Behind | обикновено cache first | cache/queue → DB по-късно | eventual | бързи writes, но durability/consistency риск |
| Refresh-Ahead | стараем се да няма cold miss | зависи от write policy | добра за predictable hot keys | background load + сложност |
| Read-Through | cache provider loader-ът зарежда source | отделна write policy | подобна на cache-aside | loading logic е зад cache API |

## Cache-Aside и Read-Through не са едно и също

При Cache-Aside application кодът прави:

```text
cache.get
  ├─ hit  → return
  └─ miss → repository.get → cache.put → return
```

При Read-Through caller-ът пита cache-а, а **cache/provider layer-ът** знае как да load-не липсващата стойност.

Conceptually са близки, но ownership-ът е различен.

---

# 3. BAD вариант: „кеширах read-а, значи сме готови“

[`NaiveCacheAsideProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/NaiveCacheAsideProductService.java)

Read flow:

```text
GET
 ↓
cache
 ├─ hit → return
 └─ miss → DB → cache.put → return
```

Изглежда добре.

После идва update:

```text
PUT
 ↓
DB updated to v2
 ↓
return
```

Но cache-ът още държи v1.

Следващият GET:

```text
cache hit → v1
DB        → v2
```

Тестът [`naiveCacheAsideReturnsStaleDataWhenWriteDoesNotInvalidateCache()`](./src/test/java/bg/hristomanov/education/cache/service/CacheAsideProductServiceTest.java) доказва точно това.

Това е добър bad example, защото кодът изглежда напълно разумен при happy-path review.

---

# 4. Cache-Aside

[`CacheAsideProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/CacheAsideProductService.java)

## Read

```text
GET product 1
    ↓
cache.get(1)
    ├─ HIT  → return cached product
    └─ MISS
         ↓
       DB read
         ↓
       cache.put
         ↓
       return
```

## Write

В лабораторията използваме:

```text
DB update
   ↓
cache.invalidate(key)
   ↓
return
```

Следващият read ще reload-не authoritative стойността.

### Защо invalidate, а не директно cache.put?

И двата варианта могат да са валидни.

Invalidate е полезен mental model:

> След write source of truth е authoritative. Старото копие се маха и следващият reader го зарежда наново.

Това намалява случаите, в които application layer трябва да поддържа два state store-а като равноправни.

Но и този подход не е магически atomic.

## Race window

По-добрият ред обикновено е:

```text
1. update DB
2. invalidate cache
```

Ако първо изтрием cache-а, а после update-нем DB:

```text
T1: delete cache
T2: reader misses
T2: loads OLD DB value
T2: caches OLD value
T1: updates DB
```

и отново имаме stale cache.

DB → invalidate намалява този проблем, но при distributed systems все още има race/failure windows. При строги изисквания се използват versioning, event-driven invalidation, CDC, outbox/reconciliation или domain-specific подход.

---

# 5. Write-Through

[`WriteThroughProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/WriteThroughProductService.java)

Нашият application-managed вариант:

```text
PUT
 ↓
DB write
 ↓
cache.put(newValue)
 ↓
ACK
```

След ACK и DB, и cache имат новата стойност.

## Плюсове

- следващият read може веднага да hit-не cache-а;
- прост mental model за caller-а;
- read-after-write поведението е лесно за демонстриране.

## Минуси

Write latency вече включва и двете операции.

И има важен failure case:

```text
DB write succeeds
cache update fails
```

Имаме partial failure.

Реалната система трябва изрично да реши:

- fail-ваме ли целия request;
- invalidираме ли cache-а;
- retry-ваме ли;
- имаме ли reconciliation;
- достатъчен ли е TTL за self-healing.

---

# 6. Write-Behind / Write-Back

[`WriteBehindProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/WriteBehindProductService.java)

Flow:

```text
PUT
 ↓
cache.put(newValue)
 ↓
enqueue pending write
 ↓
ACK
          background
              ↓
          DB write later
```

Тук ключовата идея е:

> caller-ът може да получи успех, преди source of truth да е обновен.

[`WriteStrategiesTest.java`](./src/test/java/bg/hristomanov/education/cache/service/WriteStrategiesTest.java) доказва прозореца:

```text
service.get()  → v2
repository     → още v1

flush()

repository     → v2
```

## Какво печелим

- ниска write latency;
- batching;
- по-малко write pressure върху backend store;
- подходящо за high-write workloads, когато eventual consistency е приемлива.

## Какво рискуваме

В laboratory-то queue-ът е in-memory.

Ако process-ът падне след ACK и преди flush:

```text
ACK sent
cache updated
process crash
pending write lost
DB still old
```

Това е **durability problem**.

Production write-behind често изисква durable queue/log, retries, idempotency, dead-letter handling и recovery.

---

# 7. Refresh-Ahead

[`RefreshAheadProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/RefreshAheadProductService.java)

Идеята е да не чакаме popular key да expire-не и първият unlucky request да плати reload latency.

```text
hot key in cache
      ↓
background refresh
      ↓
repository read
      ↓
cache replace
      ↓
следващият request → hit върху fresh-ish value
```

В лабораторията:

- запомняме кои product ids са били използвани;
- scheduler refresh-ва тези hot keys;
- тестът извиква refresh метода директно, за да е deterministic.

## Важна разлика спрямо Caffeine `refreshAfterWrite`

Caffeine има `refreshAfterWrite`, но semantics са по-специфични:

- entry става **eligible for refresh** след интервала;
- refresh-ът реално се стартира при следващ access;
- докато reload-ът върви, старият value може да се връща.

Тоест `refreshAfterWrite` не е „таймер винаги proactively reload-ва всичко“.

Затова в лабораторията показваме по-буквален scheduled refresh-ahead, а Caffeine behaviour е описано отделно.

---

# 8. Spring Cache abstraction

[`SpringCacheProductService.java`](./src/main/java/bg/hristomanov/education/cache/service/SpringCacheProductService.java)

Spring не е cache store.

Spring дава abstraction върху `Cache` и `CacheManager`. Реалният provider при нас е Caffeine.

## `@Cacheable`

```java
@Cacheable(cacheNames = "spring-products", key = "#productId", sync = true)
public Product get(long productId) {
    ...
}
```

Mental model:

```text
call proxy
  ↓
cache interceptor
  ├─ hit  → return cached result, method НЕ се изпълнява
  └─ miss → execute method → cache result → return
```

## `@CachePut`

Методът **винаги се изпълнява**, а result-ът се слага в cache-а.

При нас update-ът записва repository и Spring cache interceptor-ът поставя върнатия `Product` под същия key.

## `@CacheEvict`

Използваме го за explicit clear.

## Защо `sync=true` е важно

Без synchronized caching няколко threads могат едновременно да видят miss:

```text
8 callers
   ↓
all miss
   ↓
8 expensive DB loads
```

Това е cache stampede в малък мащаб.

При provider с поддръжка на `sync=true`:

```text
8 callers, same key
      ↓
1 loader computes
7 wait for same result
      ↓
cache populated once
```

[`SpringCacheProductServiceTest.java`](./src/test/java/bg/hristomanov/education/cache/service/SpringCacheProductServiceTest.java) стартира 8 concurrent callers и проверява, че repository read counter-ът е 1.

## Proxy pitfall: self-invocation

Spring annotation caching по подразбиране е proxy-based.

Това означава:

```java
public Product outer(long id) {
    return get(id); // директно this.get(id)
}
```

ако `outer()` и `get()` са в един и същи bean, вътрешното извикване не минава през Spring proxy-а.

Следствие:

> annotation-ът върху `get()` може да не се приложи при self-invocation.

Това е същият тип architectural pitfall, който познаваме от `@Transactional`.

---

# 9. TTL, expiration, eviction, invalidation и refresh са различни неща

Тези термини често се смесват.

## TTL / expiration

`expireAfterWrite(1 minute)` означава, че entry-то става невалидно според time policy след зададения интервал.

Това **не означава**:

- че данните са fresh през цялата минута;
- че точно на 60.000 s отделен thread непременно ще го изтрие;
- че external update ще invalidира local cache-а.

## Eviction

Cache може да изхвърли entry заради:

- maximum size;
- weight;
- time policy;
- memory/reference policy.

Eviction е resource-management policy.

## Invalidation

Application/domain logic знае:

> „Тази стойност вече не е валидна.“

и я маха изрично.

## Refresh

Запазваме key-а, но reload-ваме value-а.

---

# 10. Cache stampede / thundering herd

Scenario:

```text
popular key expires
        ↓
1000 requests arrive
        ↓
1000 cache misses
        ↓
1000 DB queries
```

Парадоксът е, че cache-ът, който трябва да пази DB, може точно при expiry да създаде spike.

Подходи:

- per-key single-flight / synchronized load;
- Spring `@Cacheable(sync=true)` за подходящи локални cases;
- distributed lock/coalescing при shared cache;
- refresh-ahead;
- jitter върху TTL;
- stale-while-revalidate pattern;
- request collapsing.

Не използваме distributed lock автоматично за всеки key. Той самият носи latency, availability и failure semantics.

---

# 11. Local cache срещу distributed cache

Текущият модул използва **Caffeine**.

## Caffeine

```text
Application instance A
 └─ local JVM cache

Application instance B
 └─ друг local JVM cache
```

Плюсове:

- без network hop;
- много ниска latency;
- проста deployment картина;
- отличен за small/hot reference data.

Минуси:

- всяка instance има различно копие;
- invalidation между instances не е автоматична;
- cache state се губи при restart;
- memory footprint е във всяка JVM.

## Redis / shared distributed cache

```text
instance A ─┐
instance B ─┼→ Redis
instance C ─┘
```

Плюсове:

- shared state между instances;
- централизирани TTL/policies;
- подходящ за distributed coordination/use cases.

Минуси:

- network latency;
- serialization/deserialization;
- отделна инфраструктура;
- собствен availability/failure domain;
- hot-key и network bottleneck проблеми.

Distributed cache не премахва invalidation проблема. Само променя къде живее копието.

---

# 12. Какво да кешираме

Добри кандидати:

- read-heavy data;
- expensive computations;
- reference/catalog data;
- external API responses с ясна freshness политика;
- данни с висок reuse.

Лоши кандидати:

- евтини еднократни reads;
- силно динамични данни без tolerance за staleness;
- secrets без ясна security/lifecycle политика;
- огромни payload-и без memory budget;
- данни, чиито invalidation rules не можем да дефинираме.

По-добрият въпрос не е:

> „Може ли да го кешираме?“

а:

> „Какъв staleness budget приемаме и как ще възстановим consistency след write/failure?“

---

# 13. Cache key design

Key-ът е част от correctness contract-а.

Ако method result зависи от:

```text
productId
locale
customerTier
currency
permissions
```

а key е само:

```text
productId
```

можем да върнем логически грешен result при cache hit.

Checklist:

- всички inputs, които влияят на result-а, отразени ли са в key-а;
- има ли tenant/user boundary;
- има ли version/schema prefix;
- bounded ли е cardinality-то;
- може ли attacker да създава безкрайно много keys.

---

# 14. Negative caching

Понякога има смисъл да кешираме:

```text
product 999 does not exist
```

иначе bot или broken client може да причинява безкрайни miss → DB reads.

Но negative cache обикновено има по-къс TTL:

```text
not found now
≠
will never exist
```

Spring Cache може да кешира `Optional`, но трябва съзнателно да решим дали „липсва“ е cacheable business result.

---

# 15. Observability

[`ProductCache.java`](./src/main/java/bg/hristomanov/education/cache/cache/ProductCache.java) включва Caffeine `recordStats()`.

Гледаме:

- hit count;
- miss count;
- eviction count;
- estimated size;
- repository reads/writes.

Сам по себе си висок hit ratio не означава добра система.

Пример:

```text
hit ratio = 99.9%
but cached permissions are stale
```

Performance metric е отлична, correctness е счупен.

Production dashboard-ът трябва да свързва cache metrics с:

- latency;
- DB load;
- error rate;
- freshness/reconciliation errors;
- memory usage;
- eviction rate.

---

# 16. End-to-end walkthrough

## Cache-Aside

```text
GET /api/cache-lab/cache-aside/products/1
→ CachingLabController
→ CacheAsideProductService.get(1)
→ ProductCache.get(1)
→ miss
→ ProductRepository.findById(1)
→ ProductCache.put(product)
→ response

GET same URL again
→ ProductCache.get(1)
→ hit
→ response
→ repository НЕ се извиква
```

## Write-Behind

```text
PUT /api/cache-lab/write-behind/products/1
→ controller
→ WriteBehindProductService.update(...)
→ cache.put(v2)
→ pendingWrites.add(...)
→ HTTP response v2

GET /state/1
→ repository още може да е v1

POST /write-behind/flush
→ queue drain
→ repository.save(...)
→ repository става v2
```

---

# 17. Как да стартираме

От root:

```bash
mvn -pl spring/caching/caching-strategies -am test
```

Стартиране:

```bash
mvn -pl spring/caching/caching-strategies spring-boot:run
```

Приложението е на:

```text
http://localhost:8085
```

Най-удобният вариант е IntelliJ HTTP Client:

- [`http/cache-demo.http`](./http/cache-demo.http)

## Бърз Cache-Aside пример

Reset:

```bash
curl -X POST http://localhost:8085/api/cache-lab/reset
```

Първо четене:

```bash
curl http://localhost:8085/api/cache-lab/cache-aside/products/1
```

Второ четене:

```bash
curl http://localhost:8085/api/cache-lab/cache-aside/products/1
```

State:

```bash
curl http://localhost:8085/api/cache-lab/state/1
```

<details>
<summary>Какво трябва да наблюдаваме</summary>

След двете еднакви Cache-Aside четения `repositoryReads` трябва да е 1, защото второто идва от cache-а.

</details>

---

# 18. Как го доказваме

## Cache-Aside

[`CacheAsideProductServiceTest.java`](./src/test/java/bg/hristomanov/education/cache/service/CacheAsideProductServiceTest.java)

Доказва:

- два read-а → един repository read;
- update invalidира cache-а;
- следващият read reload-ва новата версия;
- naive вариантът връща stale v1 при DB v2.

## Write strategies

[`WriteStrategiesTest.java`](./src/test/java/bg/hristomanov/education/cache/service/WriteStrategiesTest.java)

Доказва:

- write-through обновява store и cache преди следващия read;
- write-behind показва v2 през cache, докато repository още е v1;
- flush затваря eventual-consistency window-а.

## Refresh-Ahead

[`RefreshAheadProductServiceTest.java`](./src/test/java/bg/hristomanov/education/cache/service/RefreshAheadProductServiceTest.java)

Доказва:

- external write прави local cache-а stale;
- manual background refresh обновява cached value;
- следващият caller получава новата версия.

## Spring

[`SpringCacheProductServiceTest.java`](./src/test/java/bg/hristomanov/education/cache/service/SpringCacheProductServiceTest.java)

Доказва:

- `@Cacheable` пропуска повторното method execution;
- `@CachePut` обновява cache-а;
- 8 concurrent misses за един key при `sync=true` водят до един repository load.

---

# 19. Преди / след

| | Без cache | С правилно избран cache |
| --- | --- | --- |
| repeated read latency | плащаме source latency всеки път | често local/shared hit |
| source load | расте с read traffic | намалява при reuse |
| state complexity | ниска | по-висока |
| freshness | директно от source | зависи от strategy |
| failure modes | основно source failure | source + cache + consistency |
| observability | request/DB metrics | + hits/misses/evictions/staleness |
| architecture | проста | изисква explicit policy |

Cache е trade-off, не безплатна оптимизация.

---

# 20. Production considerations

## Multi-instance invalidation

Local cache A не знае автоматично, че instance B е направила write.

Решенията могат да включват:

- shared cache;
- pub/sub invalidation;
- domain events;
- CDC;
- short TTL;
- versioned values;
- combinations от горните.

## Transactions

DB transaction commit и cache update обикновено не са една atomic transaction.

Не приемай:

```text
@Transactional
+ cache.put
=
atomic DB + cache
```

Не е.

## Serialization

При Redis/Hazelcast-like remote cache имаме:

- serialization format;
- schema evolution;
- backward compatibility;
- payload size;
- deserialization cost.

## Memory budget

Local cache трябва да е bounded.

Unbounded:

```java
new ConcurrentHashMap<>()
```

използван като „cache“ без eviction policy може да се превърне в memory leak.

Caffeine wrapper-ът ни има `maximumSize(100)` именно за да запази този mental model.

## Security

Никога не използвай cache key, който може да смеси:

- users;
- tenants;
- permission contexts.

Cross-tenant cache hit е data leak, не performance bug.

---

# 21. Кога не си струва cache

Не добавяй cache, ако:

- source read е достатъчно евтин;
- reuse почти няма;
- freshness трябва да е абсолютна;
- invalidation е по-сложна от самия query;
- dataset е твърде голям спрямо memory budget;
- bottleneck-ът реално е другаде.

Преди cache първо провери:

- query/index;
- N+1;
- over-fetching;
- connection pool;
- network call;
- serialization;
- algorithmic bottleneck.

---

# ИЗХОД ОТ ТЕМАТА

## 22. Какво точно решихме

В началото имахме read-heavy product catalog, който удряше source of truth при всяко request.

Причината беше:

> нямахме reusable копие близо до application-а.

Добавихме cache, но видяхме, че това веднага създава втори проблем:

> как копието остава достатъчно близо до authoritative state-а?

Затова сравнихме няколко policies:

- Cache-Aside;
- Write-Through;
- Write-Behind;
- Refresh-Ahead;
- Spring Cache abstraction.

Тестовете доказват не само performance flow-а, а и consistency semantics:

- кога има един DB read вместо два;
- кога stale data реално се връща;
- кога caller вижда write преди DB;
- кога background refresh поправя local cache;
- кога concurrent misses се coalesce-ват.

Ограничението остава:

> текущият Caffeine cache е local to JVM. Истинска multi-instance система има допълнителни distributed consistency и invalidation проблеми.

## 23. Mental model за запомняне

1. **Cache е копие, не автоматично source of truth.**
2. **Strategy е consistency policy, не library choice.**
3. **Всеки cache трябва да има отговор на: кога стойността става stale и кой я поправя?**

---

# 24. Как да разпозная този казус в реален проект

Сигнали:

- един и същ expensive query се повтаря много пъти;
- read traffic е много по-висок от write traffic;
- DB CPU/IO е високо за еднакви lookups;
- external API има rate limit;
- имаме `Map`/Redis calls, но никой не може да обясни invalidation policy;
- update path не пипа cache-а;
- TTL е избран „на око“;
- на deploy/restart latency рязко скача заради cold cache;
- при expiry има request spike към DB;
- различни application instances връщат различни стойности.

---

# 25. Decision guide

## Cache-Aside

Използвай, когато:

- reads са dominant;
- application може ясно да управлява invalidation;
- искаш lazy population.

Внимавай за stale data и race windows.

## Write-Through

Използвай, когато:

- искаш cache-а да е warm след write;
- synchronous extra work е приемлив.

Внимавай за dual-write partial failures.

## Write-Behind

Използвай, когато:

- write throughput/latency е критичен;
- eventual consistency е приемлива;
- имаш durable buffering/retry design.

Не го използвай за critical writes само с in-memory queue.

## Refresh-Ahead

Използвай, когато:

- имаш predictable hot keys;
- miss latency е скъп;
- можеш да поемеш background refresh traffic.

Не refresh-вай безкрайно cold data.

## Spring Cache

Използвай, когато:

- method-level caching съвпада с domain boundary;
- искаш provider abstraction;
- semantics са достатъчно прости.

Не го използвай като заместител на consistency design.

---

# 26. Практичен checklist за code review

```text
[ ] Какъв е source of truth?
[ ] Какъв е cache key-ът и съдържа ли всички inputs, които влияят на result-а?
[ ] Какво става при cache miss?
[ ] Какво става при write?
[ ] Кой invalidира или refresh-ва стойността?
[ ] Колко stale може да бъде value-ът според business-а?
[ ] TTL business requirement ли е или случайно число?
[ ] Bounded ли е cache-ът?
[ ] Какво става при cache outage?
[ ] Какво става при DB success + cache failure?
[ ] Има ли cache stampede protection за hot keys?
[ ] Local или distributed е cache-ът?
[ ] Как се синхронизират няколко application instances?
[ ] Има ли hit/miss/eviction/freshness observability?
[ ] Има ли тест, който доказва stale/failure/concurrency semantics?
```

---

# 27. Какво да запомня

1. Cache ускорява reads чрез повторно използване на вече изчислена/заредена стойност.
2. Cache-ът създава копие и следователно consistency problem.
3. Cache-Aside оставя application-а да управлява miss и invalidation.
4. Write-Through плаща write latency, за да обнови store и cache синхронно.
5. Write-Behind връща по-бързо, но приема eventual consistency и durability риск.
6. Refresh-Ahead премества reload latency извън критичния request path.
7. TTL не е invalidation strategy.
8. Expiration, eviction, invalidation и refresh са различни механизми.
9. Caffeine е local JVM cache; Redis е shared remote cache.
10. `@Cacheable` не означава автоматично „правилна caching архитектура“.
11. `sync=true` може да предотврати дублирани concurrent loads за един key при подходящ provider.
12. Spring caching annotations са proxy-based и self-invocation е pitfall.
13. Cache key design е част от correctness и security.
14. Hit ratio без freshness/correctness metrics може да бъде подвеждащ.
15. Ако не можеш да обясниш кога cached value става невалиден, design-ът още не е завършен.

---

# 28. Упражнения

1. Добави `delete(productId)` и избери правилна invalidation policy за всяка стратегия.
2. Добави short-lived negative cache за missing products.
3. Добави tenant към `Product` request-а и поправи cache key-а.
4. Замени local Spring Caffeine cache с Redis и сравни latency/serialization/failure behaviour.
5. Направи cache stampede test без `sync=true` и сравни repository read count.
6. Добави TTL jitter, за да не expire-ват всички hot keys едновременно.
7. Добави simulated cache failure и реши дали приложението трябва да degrade-не към DB или да fail-не.
8. Добави Micrometer metrics за hit/miss/eviction.
9. Направи write-behind queue durable чрез Kafka/RabbitMQ-like abstraction и idempotent consumer.
10. Направи multi-instance invalidation demo с две application instances.

---

# 29. Version notes

Лабораторията е адаптирана към:

- **Java 25**;
- **Spring Boot 4.1.1**;
- **Spring Framework 7.x** чрез Boot dependency management;
- **Caffeine** версията, управлявана от Spring Boot BOM.

Не pin-ваме ръчно версията на Caffeine, за да не излизаме от tested dependency set-а на Spring Boot.

---

# 30. Оригинални източници

## Основен материал

- Java Techie — Caching Strategies Explained:  
  https://www.youtube.com/watch?v=sPA2c0DE6QY

## Spring

- Spring Boot 4.1.1 — Caching:  
  https://docs.spring.io/spring-boot/reference/io/caching.html
- Spring Framework — annotation-based caching:  
  https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html

## Caffeine

- Caffeine project / user guide:  
  https://github.com/ben-manes/caffeine
- Population:  
  https://github.com/ben-manes/caffeine/wiki/Population
- Eviction:  
  https://github.com/ben-manes/caffeine/wiki/Eviction
- Refresh semantics:  
  https://github.com/ben-manes/caffeine/wiki/Refresh

## Cache stampede / distributed example

- Redis — cache-aside and stampede protection example:  
  https://redis.io/docs/latest/develop/use-cases/cache-aside/java-jedis/

---

## Финална идея

Ако след този урок трябва да остане само един въпрос за code review, той е:

> **„Когато source of truth се промени, по какъв точно механизъм cached copy-то разбира, че вече не трябва да бъде използвано?“**

Ако отговорът е неясен, cache-ът още не е проектиран — само е добавен.
