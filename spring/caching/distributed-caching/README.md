# Distributed Caching — Spring Cache + Redis + L1/L2 near cache

Този модул надгражда два предишни слоя:

- [`caching-strategies`](../caching-strategies/README.md) — strategy/correctness fundamentals;
- [`redis-eviction`](../redis-eviction/README.md) — memory/TTL/eviction semantics.

Тук въпросът вече е:

> **Какво се променя, когато application-ът има няколко instances и cache state вече не е само в една JVM?**

Основният видео източник е:

- SpringDeveloper — **Spring Office Hours: S4E19 - Spring & Redis with Raphael De Lio**  
  https://www.youtube.com/live/KUNNslg-RQw

---

# ВХОД В ТЕМАТА

## 1. Local Caffeine е много бърз, но е local

```text
instance A
└→ Caffeine A

instance B
└→ Caffeine B
```

Ако A cache-не Product v1, а B cache-не Product v1, после A направи write:

```text
DB = v2
A cache = ?
B cache = v1
```

B не разбира автоматично какво е станало.

Това е multi-instance invalidation problem.

---

# 2. Shared Redis L2

Една common topology е:

```text
request
↓
L1 Caffeine (per JVM)
↓ miss
L2 Redis (shared)
↓ miss
source of truth
```

В lab-а симулираме две JVM-like nodes:

```text
NearCacheNode A
NearCacheNode B
```

в един process, но всяка има собствена Caffeine instance.

Shared layer е реален Redis.

Код:

- [`NearCacheNode.java`](./src/main/java/bg/hristomanov/education/distributedcache/NearCacheNode.java)
- [`NearCacheNodeFactory.java`](./src/main/java/bg/hristomanov/education/distributedcache/NearCacheNodeFactory.java)

---

# 3. Защо L1 + L2?

## L1

Плюсове:

- без network hop;
- ultra-low latency;
- намалява Redis traffic.

Минуси:

- per-instance stale copies;
- duplicate memory;
- restart = cold.

## L2 Redis

Плюсове:

- shared between instances;
- централизирана TTL policy;
- един node може да warm-не друг.

Минуси:

- network;
- serialization;
- Redis availability/failure domain;
- distributed stampede/hot keys.

Multi-level cache комбинира плюсовете, но и consistency responsibilities.

---

# 4. Spring Cache + RedisCacheManager

[`RedisCacheConfiguration.java`](./src/main/java/bg/hristomanov/education/distributedcache/RedisCacheConfiguration.java)

конфигурира:

```text
Spring Cache abstraction
→ RedisCacheManager
→ Redis
```

с explicit:

- TTL;
- key prefix;
- null caching policy;
- key serializer;
- JSON value serializer.

Service:

- [`RedisCachedProductService.java`](./src/main/java/bg/hristomanov/education/distributedcache/RedisCachedProductService.java)

използва normal:

```java
@Cacheable(cacheNames = "products", key = "#productId")
```

но provider вече е Redis, не Caffeine.

---

# 5. Spring Cache ≠ RedisTemplate ≠ Redis Repository

Това е важен Spring mental model.

## Spring Cache

```text
@Cacheable
@CachePut
@CacheEvict
CacheManager
```

Purpose:

> cache method results / explicit cache entries.

## RedisTemplate

```text
opsForValue
opsForHash
opsForSet
pub/sub
streams
...
```

Purpose:

> general Redis data structure access.

## Spring Data Redis Repository

```text
object ↔ Redis Hash
secondary indexes
TTL
repository API
```

Purpose:

> Redis като persistence/data store abstraction.

Една Redis technology, три различни programming models.

Не ги използваме взаимозаменяемо само защото всички сочат към Redis.

---

# 6. Serialization вече е част от correctness

Caffeine държи Java object reference.

Redis държи bytes.

Flow:

```text
Product
→ serializer
→ network
→ Redis bytes
→ deserializer
→ Product
```

Spring Data Redis default `RedisCacheConfiguration` използва JDK serialization за values.

В lab-а изрично сменяме това с:

```text
GenericJacksonJsonRedisSerializer
```

който в Spring Data Redis 4.x е Jackson 3 serializer.

За typed `RedisTemplate<String, Product>` използваме:

```text
JacksonJsonRedisSerializer<Product>
```

Това е същата Jackson 3 migration линия, която видяхме и в други Boot 4 modules.

---

# 7. Защо JSON?

Плюсове:

- human-readable;
- language-neutral-ish;
- по-ясна schema evolution от Java native serialization;
- лесно inspect-ване.

Минуси:

- payload size;
- serialization CPU;
- type/version compatibility;
- field evolution policy.

Binary formats могат да са по-компактни, но пак трябва schema governance.

Няма universal serializer.

---

# 8. Test: shared L2 warm-up

`secondApplicationNodeCanWarmItsL1FromSharedRedisL2()`

Flow:

```text
Node A L1 miss
→ Redis L2 miss
→ source read #1
→ Redis L2 put
→ A L1 put

Node B L1 miss
→ Redis L2 HIT
→ B L1 put
```

Assertion:

```text
source reads = 1
A L1 = warm
B L1 = warm
```

Това е първата реална полза от shared cache.

---

# 9. Write + cross-instance invalidation

В lab-а write flow-ът е:

```text
Node A update
→ source of truth updated
→ Redis L2 updated
→ publish invalidation message
→ Redis Pub/Sub
→ A/B L1 invalidate
```

Bus:

- [`RedisInvalidationBus.java`](./src/main/java/bg/hristomanov/education/distributedcache/RedisInvalidationBus.java)

Subscription:

- [`RedisPubSubConfiguration.java`](./src/main/java/bg/hristomanov/education/distributedcache/RedisPubSubConfiguration.java)

Test:

`redisPubSubInvalidatesIndependentL1CopiesAfterWrite()`

доказва:

```text
A and B both cache v1 locally
A writes v2
B receives invalidation
B next read -> shared Redis v2
source reads остава 1
```

---

# 10. Pub/Sub е invalidation signal, не durable event log

Redis Pub/Sub е useful за low-latency invalidation.

Но:

```text
subscriber offline
→ message can be missed
```

Това означава:

> Pub/Sub сам по себе си не е durable consistency protocol.

Production mitigations:

- short L1 TTL;
- versioned values;
- reconnect invalidation/clear;
- Redis Streams;
- domain events;
- CDC;
- shared L2 as authoritative cache copy;
- combinations.

Near-cache implementation трябва да дефинира reconnect semantics.

---

# 11. Local `sync=true` ≠ distributed single-flight

Предишният Caffeine/Spring Cache lab показа:

```text
8 callers
same JVM
same key
@Cacheable(sync=true)
→ 1 loader
```

Сега:

```text
Node A L1 miss
Node B L1 miss
shared Redis L2 cold
```

и двата могат едновременно да направят:

```text
source load
```

Test:

`twoColdNodesCanStillCreateDistributedStampede()`

използва deterministic barrier и доказва:

```text
two nodes
one cold shared key
→ source reads = 2
```

Това е ключов урок:

> synchronization вътре в една JVM не координира други JVM-и.

---

# 12. Distributed stampede solutions

Options:

- distributed lock;
- Redis `SET NX` lease;
- request coalescing service;
- refresh-ahead;
- stale-while-revalidate;
- probabilistic early refresh;
- durable worker ownership.

Но distributed lock не е free.

Трябва да дефинираме:

- lock TTL;
- owner token;
- timeout;
- unlock safety;
- failure/retry;
- what happens when loader dies.

Не добавяме lock автоматично за всеки cache miss.

---

# 13. Cache key namespace

Lab uses:

```text
edu:products::1
edu:l2:product:1
```

Production key namespace често включва:

```text
application
cache name
schema version
tenant
business key
```

Например:

```text
catalog:v3:tenant-42:product:1
```

Schema/version prefix позволява controlled cache migration вместо risky in-place deserialization.

---

# 14. TTL в distributed cache

TTL вече има няколко нива:

```text
L1 TTL
L2 TTL
source freshness
```

Типичен design:

```text
L1 shorter
L2 longer
```

за да ограничим stale local copies.

Но exact values трябва да идват от business staleness budget.

Не:

> 5 минути, защото така пишеше в tutorial.

---

# 15. TTL jitter

Ако 100k keys са заредени в един и същ момент с еднакъв TTL:

```text
all expire around T+5m
→ source spike
```

Jitter:

```text
base TTL ± random window
```

разпределя refresh/load pressure.

Това е capacity technique, не consistency guarantee.

---

# 16. Redis outage: fail-open или fail-closed?

При Redis unavailable:

## Fail-open

```text
cache error
→ go to source
```

Плюс:

- application остава functional.

Риск:

- DB може изведнъж да получи целия traffic;
- cache outage да причини DB outage.

## Fail-closed

```text
cache error
→ request fails
```

Може да е оправдано, ако cache държи essential derived state или source не може да поеме load.

Policy-то е domain/capacity decision.

---

# 17. Cache outage load shedding

Fail-open често трябва да се комбинира с:

- rate limit;
- bulkhead;
- concurrency cap;
- circuit breaker;
- degraded response;
- stale local copy.

Тук caching се връзва директно с Reliability module-а.

---

# 18. Hot keys

Shared Redis решава duplication на cache data, но създава central hotspot possibility.

```text
one product key
→ huge traffic
```

може да saturate:

- Redis CPU;
- network;
- one cluster shard.

L1 near cache често е полезен именно за hot reads.

Но invalidation и memory duplication стават по-сложни.

---

# 19. Prefetch / cache-as-primary-read-path

Има architecture, различна от Cache-Aside:

```text
startup/bulk sync
→ Redis populated

reads
→ Redis only

DB changes
→ CDC/change stream
→ Redis update
```

Тогава cache miss не означава:

> fallback to DB.

А:

> synchronization pipeline вероятно е broken/incomplete.

Това е силно различен consistency contract.

Подходящо е, когато:

- working set може да бъде материализиран;
- Redis read path трябва да е predictable;
- CDC pipeline е надежден/observable.

---

# 20. CDC и вече наученото

Flow:

```text
DB
→ change log
→ Debezium/CDC
→ Redis updater
```

веднага ни връща към:

- consumer idempotency;
- ordering;
- lag;
- replay;
- schema evolution;
- observability.

Тоест advanced caching не живее изолирано от distributed-systems patterns.

---

# 21. Spring Data Redis repositories

Spring Data Redis Repositories могат да map-ват objects към Redis Hashes, да поддържат TTL и indexes.

Но това **не е същото** като Spring Cache.

Още един важен current detail:

> Redis Repositories не работят с Redis transactions според Spring Data Redis документацията.

Това трябва да се знае преди да ги използваме като generic substitute за JPA repository semantics.

---

# 22. Observability

За L1:

- hit/miss;
- size;
- eviction;
- invalidation count.

За L2 Redis:

- hit/miss;
- latency;
- connection pool;
- memory;
- evictions;
- command errors;
- hot keys.

За source:

- fallback load count;
- latency;
- saturation.

За invalidation:

- published signals;
- consumer lag/misses;
- reconnect clears.

Един общ hit ratio вече не е достатъчен.

---

# Какво доказват тестовете

[`DistributedCachingIntegrationTest.java`](./src/test/java/bg/hristomanov/education/distributedcache/DistributedCachingIntegrationTest.java)

## RedisCacheManager

```text
two method calls
→ source read = 1
→ Redis key has TTL
```

## Shared L2

```text
A loads source
B loads Redis
→ source read = 1
```

## Pub/Sub invalidation

```text
A/B local v1
A writes v2
→ both L1 invalidated
B reloads v2 from Redis
```

## Distributed cold miss

```text
A and B miss shared key simultaneously
→ source reads = 2
```

---

# Как да стартираме

Локално:

```bash
docker run --rm -p 6379:6379 redis:8.10.2
```

После:

```bash
mvn -pl spring/caching/distributed-caching -am test
```

Redis unavailable локално → integration test се skip-ва.

CI стартира real Redis service и изпълнява тестовете.

---

# Mental model за запомняне

1. **Local cache state не се споделя между JVM-и.**
2. **Shared Redis намалява source loads, но добавя network/serialization/failure domain.**
3. **L1/L2 near cache изисква cross-instance invalidation policy.**
4. **Redis Pub/Sub е fast signal, не durable event log.**
5. **Local single-flight не е distributed single-flight.**
6. **Serialization format е част от cache compatibility contract-а.**
7. **Spring Cache, RedisTemplate и Redis Repository решават различни problems.**
8. **Fail-open към DB може да превърне Redis outage в DB outage.**
9. **Hot keys и eviction са различни capacity problems.**
10. **Distributed cache correctness остава business consistency design.**

---

# Code-review checklist

```text
[ ] L1 local ли е? L2 shared ли е?
[ ] Как се invalidират всички JVM local copies?
[ ] Какво става при missed Pub/Sub message?
[ ] Redis outage fail-open или fail-closed е?
[ ] Source може ли да понесе fail-open traffic?
[ ] Serialization/schema version explicit ли е?
[ ] Key namespace има ли tenant/schema boundary?
[ ] Distributed cold miss coalescing нужно ли е?
[ ] TTL jitter нужен ли е?
[ ] Hot-key risk анализиран ли е?
[ ] Redis memory/eviction policy наблюдава ли се?
[ ] L1/L2/source metrics разделени ли са?
[ ] CDC/prefetch pipeline има ли lag/replay semantics?
```

---

# Оригинални източници

- SpringDeveloper — Spring Office Hours: S4E19 - Spring & Redis with Raphael De Lio:  
  https://www.youtube.com/live/KUNNslg-RQw
- Spring Boot — Caching:  
  https://docs.spring.io/spring-boot/reference/io/caching.html
- Spring Data Redis 4.1.1:  
  https://docs.spring.io/spring-data/redis/reference/
- Spring Data Redis — Redis Repositories:  
  https://docs.spring.io/spring-data/redis/reference/repositories.html
- Spring Data Redis — Jackson 3 migration:  
  https://docs.spring.io/spring-data/redis/reference/upgrading.html
- Redis — key eviction:  
  https://redis.io/docs/latest/develop/reference/eviction/

---

# Изходен въпрос

Когато local cache работи отлично на една instance, питай:

> **Какво точно се случва с cache correctness, stampede protection и source load, когато deployment-ът стане 5 JVM-и вместо една?**
