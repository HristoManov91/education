# Redis Eviction — TTL, maxmemory, LRU, LFU и memory pressure

Този модул надгражда [`caching-strategies`](../caching-strategies/README.md) с нещо, което local Caffeine lab-ът не може да покаже добре:

> **Какво реално прави Redis, когато cache-ът има ограничен memory budget и трябва да реши кои keys да премахне?**

Основният видео източник е:

- Java Techie — **Redis Cache Eviction Explained | How TTL, LRU & LFU Actually Work #08**  
  https://www.youtube.com/watch?v=72eZWhx4mDs

---

# ВХОД В ТЕМАТА

## 1. TTL и eviction НЕ са едно и също

Това е първият mental model:

```text
TTL / expiration
→ времето казва, че entry вече е невалиден

Eviction
→ memory pressure кара Redis да освободи място
```

Key може да има още 30 минути TTL и въпреки това да бъде evict-нат сега.

И обратното:

```text
key без TTL
+
noeviction
→ може да остане
докато новите writes започнат да fail-ват
```

---

# 2. `maxmemory` е реалният memory budget

Redis не започва LRU/LFU eviction просто защото има TTL.

Memory pressure идва от:

```text
used memory > maxmemory
```

При write Redis проверява budget-а и според `maxmemory-policy`:

- evict-ва keys;
- или отказва write-а.

В CI използваме реален Redis и runtime `CONFIG SET`, за да направим memory budget-а нарочно малък.

Код:

- [`RedisAdmin.java`](./src/main/java/bg/hristomanov/education/rediseviction/RedisAdmin.java)

Tests:

- [`RedisEvictionIntegrationTest.java`](./src/test/java/bg/hristomanov/education/rediseviction/RedisEvictionIntegrationTest.java)

---

# 3. Политиките

Актуалният Redis има policy family:

```text
noeviction

allkeys-lru
allkeys-lfu
allkeys-lrm
allkeys-random

volatile-lru
volatile-lfu
volatile-lrm
volatile-random
volatile-ttl
```

`allkeys-*`:

> всички keys могат да бъдат eviction candidates.

`volatile-*`: 

> само keys с expiration/TTL са кандидати.

Това е correctness decision, не само performance tuning.

---

# 4. `noeviction`

Flow:

```text
maxmemory reached
→ Redis НЕ маха existing keys
→ write, който иска още memory, fail-ва
→ reads на existing data продължават
```

Test:

`noevictionRejectsNewWritesWhenMemoryBudgetIsExhausted()`

Доказва:

```text
existing:key остава readable
new writes → DataAccessException / Redis OOM
evicted_keys = 0
```

Това може да е правилна policy, ако Redis не е disposable cache, а държи data, която не трябва автоматично да бъде изхвърляна.

Но за типичен cache често предпочитаме eviction.

---

# 5. LRU — Least Recently Used

Идеята:

```text
кой key е използван най-отдавна?
→ той е по-добър eviction candidate
```

Policy:

```text
allkeys-lru
```

Test:

`allKeysLruEvictsEntriesInsteadOfRejectingWrites()`

слага малък `maxmemory`, записва много големи values и проверява:

```text
writes продължават
evicted_keys > 0
db size < inserted key count
```

### Важно: Redis LRU е approximate

Тестът нарочно **не проверява един конкретен victim key**.

Redis използва sampling approximation вместо perfect global LRU list.

Причината е trade-off:

```text
perfect LRU
→ повече metadata/memory/work

sampled LRU
→ по-нисък overhead
→ много добро practical approximation
```

`maxmemory-samples` влияе на това колко candidates Redis разглежда.

---

# 6. LFU — Least Frequently Used

LRU пита:

> кога последно беше използван key-ът?

LFU пита:

> колко често се използва?

Policy:

```text
allkeys-lfu
```

Redis LFU също е approximate.

Използва probabilistic frequency counter и decay, защото:

```text
key, който е бил hot вчера
не трябва задължително да бъде hot завинаги
```

Relevant config:

```text
lfu-log-factor
lfu-decay-time
```

Test:

`lfuTracksHotterKeysWithHigherApproximateFrequency()`

прави стотици reads на `lfu:hot` и един read на `lfu:cold`, после сравнява `OBJECT FREQ`.

Това показва самия signal, по който LFU policy взима решение.

---

# 7. LRU vs LFU

Пример workload:

```text
A беше използван 10 000 пъти вчера, но не и днес
B е използван всяка минута днес
```

LRU може да предпочете B, защото B е recent.

LFU може да пази A по-дълго, докато decay постепенно намалява historical popularity.

Изборът зависи от access distribution.

Не съществува универсално:

> LFU винаги е по-добро от LRU.

Измерваме hit ratio, latency и memory behavior върху реалния workload.

---

# 8. `volatile-ttl`

Тази policy разглежда само keys с TTL и предпочита keys с по-кратък remaining TTL.

В lab-а имаме:

```text
persistent:reference-data
→ няма TTL

volatile:0..N
→ имат TTL
```

При memory pressure:

```text
volatile keys → candidates
persistent key → НЕ е candidate
```

Test:

`volatileTtlNeverEvictsPersistentKeys()`

доказва, че persistent key остава.

Това може да е полезно, когато в един Redis instance съзнателно смесваме:

- persistent-ish keys;
- disposable cache entries.

Но смесването на различни durability/memory semantics в един instance трябва да е много съзнателно.

---

# 9. TTL expiration без memory pressure

Test:

`ttlExpirationDoesNotNeedMemoryPressure()`

прави:

```text
SET key TTL=150ms
→ wait
→ key disappears
→ evicted_keys остава 0
```

Това директно доказва:

```text
expiration
≠
eviction
```

---

# 10. Passive и active expiration

Expired key не означава задължително:

> отделен thread го изтрива точно на 150.000 ms.

Redis комбинира:

- passive expiration — key се открива като expired при access;
- active expiration — Redis периодично sample-ва expiring keys.

От application perspective:

> след expiration key не трябва да се счита за valid.

Но internal cleanup timing и physical memory reclamation не са същото като business TTL contract.

---

# 11. `allkeys-*` vs `volatile-*`

Въпросът е:

> Кои keys е позволено Redis автоматично да жертва?

## allkeys

```text
всички keys
→ eviction candidates
```

Добро за dedicated cache instance.

## volatile

```text
само keys с TTL
→ eviction candidates
```

Keys без TTL са protected от тази policy.

Риск:

ако почти няма TTL keys, Redis може да няма какво полезно да evict-не.

---

# 12. Eviction policy не поправя лош memory model

Ако cache държи:

- огромни values;
- unbounded key cardinality;
- per-user keys без lifecycle;
- duplicated payloads;

LRU/LFU само определят **как Redis ще страда**, не премахват проблема.

Преди tuning:

```text
measure key count
measure average/p95 value size
measure hit ratio
measure churn
measure evicted_keys
measure memory fragmentation
```

---

# 13. Hot keys

Eviction policy решава memory capacity.

Не решава автоматично:

```text
един key получава 500k requests/sec
```

Hot key може да създаде:

- CPU pressure;
- network bottleneck;
- shard imbalance.

Тоест:

```text
memory eviction problem
≠
traffic distribution problem
```

---

# 14. Cluster/shard nuance

В clustered Redis memory pressure е per shard/node.

Може:

```text
cluster overall memory = 60%
но one hot/large shard = 100%
→ eviction там
```

Не гледаме само global memory graph.

---

# 15. Как да стартираме

Локално трябва Redis:

```bash
docker run --rm -p 6379:6379 redis:8.10.2
```

После:

```bash
mvn -pl spring/caching/redis-eviction -am test
```

Ако Redis не е наличен, integration test-ът се skip-ва локално.

GitHub Actions стартира Redis service, така че CI винаги изпълнява реалните scenarios.

---

# Mental model за запомняне

1. **TTL решава freshness lifetime; eviction решава memory pressure.**
2. **`maxmemory` активира memory budget-а.**
3. **`noeviction` пази existing keys, но може да отказва writes.**
4. **LRU гледа recency; LFU гледа frequency.**
5. **Redis LRU/LFU са approximate algorithms.**
6. **`volatile-*` policy може да evict-ва само expiring keys.**
7. **Eviction policy е workload decision, не checkbox.**
8. **Memory policy не решава hot keys, bad key design или stale-data correctness.**

---

# Code-review checklist

```text
[ ] Redis instance dedicated cache ли е или смесва durable/cache data?
[ ] maxmemory configured ли е?
[ ] Каква maxmemory-policy използваме и защо?
[ ] TTL и eviction разграничени ли са?
[ ] allkeys или volatile family е правилната?
[ ] LRU/LFU workload assumption валидна ли е?
[ ] evicted_keys observable ли е?
[ ] OOM/noeviction behavior тествано ли е?
[ ] value/key cardinality bounded ли е?
[ ] memory budget включва ли replication/persistence overhead?
[ ] cluster/shard imbalance наблюдава ли се?
```

---

# Оригинални източници

- Java Techie — Redis Cache Eviction Explained | How TTL, LRU & LFU Actually Work #08:  
  https://www.youtube.com/watch?v=72eZWhx4mDs
- Redis — Key eviction:  
  https://redis.io/docs/latest/develop/reference/eviction/
- Redis — EXPIRE:  
  https://redis.io/docs/latest/commands/expire/
- Spring Data Redis 4.1.1:  
  https://docs.spring.io/spring-data/redis/reference/

---

# Изходен въпрос

Когато някой каже:

> сложили сме TTL, значи Redis сам ще управлява memory-то

питай:

> **TTL казва кога key е остарял; какъв е `maxmemory` budget-ът, коя eviction policy действа при pressure и кои keys изобщо имат право да бъдат изхвърлени?**
