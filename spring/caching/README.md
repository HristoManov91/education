# Caching curriculum — from local copies to semantic similarity

Този раздел е организиран като progression, а не като един огромен Redis/Spring README.

```text
1. Caching fundamentals / consistency
   ↓
2. Redis memory / eviction
   ↓
3. Distributed multi-instance caching
   ↓
4. Semantic caching for AI
```

## 1. Fundamentals

- [Caching Strategies](./caching-strategies/README.md)

Покрива:

- Cache-Aside;
- Write-Through;
- Write-Behind;
- Refresh-Ahead;
- Spring Cache;
- local Caffeine;
- stale data;
- TTL / expiration / eviction concepts;
- cache stampede;
- key design;
- local vs distributed cache mental model.

## 2. Redis memory policy

- [Redis Eviction](./redis-eviction/README.md)

Покрива:

- `maxmemory`;
- `noeviction`;
- LRU;
- LFU;
- `volatile-ttl`;
- approximate eviction algorithms;
- TTL vs eviction;
- memory-pressure integration tests against real Redis.

## 3. Distributed caching

- [Distributed Caching](./distributed-caching/README.md)

Покрива:

- Spring `RedisCacheManager`;
- Jackson 3 serialization;
- RedisTemplate;
- L1 Caffeine + L2 Redis;
- multi-instance invalidation;
- Redis Pub/Sub;
- local vs distributed single-flight;
- cold-key distributed stampede;
- Redis outage policy;
- hot keys;
- prefetch + CDC design.

## 4. AI semantic caching

- [Semantic Caching](../ai/semantic-caching/README.md)

Покрива:

- exact vs semantic cache;
- embeddings;
- cosine similarity;
- threshold tuning;
- false semantic hits;
- context isolation;
- tenant/system-prompt/model/knowledge-base versions;
- TTL;
- semantic cache vs agent memory;
- mapping към Spring AI 2.0.1 + Redis Semantic Cache.

---

# Един общ mental model

На всяко ниво от curriculum-а задаваме едни и същи въпроси:

```text
Какво е source of truth?
Какво точно означава cache hit?
Кога cached result вече е невалиден?
Кой го invalidира/refresh-ва?
Какво става при failure?
Какво става при concurrency?
Какво става при multiple JVMs?
Как измерваме correctness, а не само hit rate?
```

Semantic caching добавя още един въпрос:

> **Семантично сходният input наистина ли допуска същия correct output?**

Това е caching curriculum-ът от local performance optimization до distributed и AI correctness problem.
