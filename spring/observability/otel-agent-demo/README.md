# P1 — Spring Boot 4 + OpenTelemetry Java Agent: real HTTP и optional Redis

> Част от [Java Agents / Byte Buddy](../../../java/jvm/java-agent-instrumentation/README.md). Източник: [Bruce Bujon — The Magic of OpenTelemetry](https://www.youtube.com/watch?v=Lf_m5dIjjqw) + официални Java/OTel/Spring документи. Java 25, Spring Boot 4.1.2, OpenTelemetry Java Agent 2.33.1 (opt-in).

## 1. Реалният казус и решението

`GET /api/quotes/widget` прави HTTP заявка за цена и връща бизнес JSON. При Redis cache miss изпълнява HTTP и Redis SET, при Redis hit няма outbound HTTP. Как установяваме тази разлика в production без ръчни spans във всеки метод?

**Naive:** само `logger.info` в controller или Spring `@Around`. Той вижда собствения bean, но не гарантира cross-service context propagation през HTTP/Redis.

**Good:** стартираме същата app с **истински** OTel Java Agent (`-javaagent`) и export на spans към console за локална инспекция. Няма OTel SDK import в business source. Това не означава безплатен performance: agent, exporters, sampling и telemetry pipeline струват ресурси.

## 2. Карта README → код → доказателство

| Слой | Файлове | Проверка |
| --- | --- | --- |
| Spring Boot runtime | [TelemetryDemoApplication.java](src/main/java/bg/hristomanov/education/otel/TelemetryDemoApplication.java), [pom.xml](pom.xml) | [TelemetryDemoHttpTest.java](src/test/java/bg/hristomanov/education/otel/TelemetryDemoHttpTest.java) |
| Inbound HTTP и fake upstream | [QuoteController.java](src/main/java/bg/hristomanov/education/otel/QuoteController.java), [QuoteDto.java](src/main/java/bg/hristomanov/education/otel/QuoteDto.java) | [IntelliJ HTTP file](http/otel-demo.http) |
| Outbound RestClient + optional Redis | [PriceLookupService.java](src/main/java/bg/hristomanov/education/otel/PriceLookupService.java), [PriceDto.java](src/main/java/bg/hristomanov/education/otel/PriceDto.java) | [real network test](src/test/java/bg/hristomanov/education/otel/TelemetryDemoHttpTest.java), [Redis-hit unit test](src/test/java/bg/hristomanov/education/otel/PriceLookupServiceTest.java) |
| Runtime config | [application.properties](src/main/resources/application.properties), [compose.yaml](compose.yaml) | [real OTel agent smoke script](scripts/otel-smoke.sh) |

**Демо ограничение:** за лесен локален запуск fake `/internal/prices` е в същото Spring приложение. Self-HTTP е само учебна техника и е лоша production архитектура (повече latency, servlet pool contention). В JUnit теста fake upstream е **отделен JDK HTTP server на случаен порт**. Spring Boot 4 има отделен `spring-boot-starter-restclient` за auto-configured `RestClient.Builder`.

## 3. Lab 1 — бизнес baseline без агент (без Docker)

Изисквания JDK25 и Maven. От repo root:

```bash
mvn -pl spring/observability/otel-agent-demo verify
mvn -pl spring/observability/otel-agent-demo spring-boot:run

# Втори терминал:
curl -fsS http://localhost:8086/api/quotes/widget
# {"sku":"widget","totalCents":2500,"cached":false}
```

Може и директно от IntelliJ: [otel-demo.http](http/otel-demo.http).

<details>
<summary>Примерен HTTP резултат</summary>

```json
{"sku":"widget","totalCents":2500,"cached":false}
```

`/internal/prices/unknown` връща 404. Динамичните trace IDs/durations в следващия lab ще са различни за всяко стартиране.
</details>

[HTTP integration test](src/test/java/bg/hristomanov/education/otel/TelemetryDemoHttpTest.java) доказва реални inbound/outbound HTTP и договор за цена. **Той не твърди, че OTel работи**, защото не зарежда агента. [Redis-hit test](src/test/java/bg/hristomanov/education/otel/PriceLookupServiceTest.java) доказва, че кешът избягва upstream HTTP.

## 4. Lab 2 — реален OTel Java Agent с console exporter

Използваме фиксиран Maven artifact **`io.opentelemetry.javaagent:opentelemetry-javaagent:2.33.1`**. Той не е нашият учебен `PriceTimingAgent`. Изпълни от repo root:

```bash
mvn -B dependency:copy \
  -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.33.1 \
  -DoutputDirectory="$PWD/otel-artifacts"

mvn -pl spring/observability/otel-agent-demo package

OTEL_AGENT_JAR="$PWD/otel-artifacts/opentelemetry-javaagent-2.33.1.jar" \
  bash spring/observability/otel-agent-demo/scripts/otel-smoke.sh
```

[Smoke скриптът](scripts/otel-smoke.sh) стартира реалната Spring Boot JAR с `-javaagent`, `otel.traces.exporter=console`, `metrics/logs=none`, прави HTTP request и проверява бизнес output и поне два exported spans. **Без Collector/Tempo/Grafana** — това е целенасочено минимален локален proof. Изходният log остава в temp файл, чийто път се отпечатва.

Търси inbound HTTP server span, outbound RestClient client span, fake upstream HTTP span. При правилна propagation те споделят trace ID, но имат различни span ID. Конкретният span name може да се променя между OTel версии.

## 5. Lab 3 — включи Redis и сравни trace graph

```bash
cd spring/observability/otel-agent-demo
docker compose up -d redis
LAB_REDIS_ENABLED=true mvn spring-boot:run

# В друг терминал в рамките на 30 секунди:
curl -fsS http://localhost:8086/api/quotes/widget
# {"sku":"widget","totalCents":2500,"cached":false}
curl -fsS http://localhost:8086/api/quotes/widget
# {"sku":"widget","totalCents":2500,"cached":true}

# След упражнението:
docker compose down
```

За да видиш Redis spans, използвай **същия JVM agent** от Lab 2 със `LAB_REDIS_ENABLED=true` или приложи същите `-D` flags при ръчен старт. First hit: Redis GET miss → HTTP client → Redis SET. Second hit: Redis GET hit → без downstream HTTP. Когато Redis не е достъпен и `lab.redis.enabled=true`, примерът fail-ва — не е production fallback policy.

## 6. Decision contract, ограничения, финален review

| Избор | Решава | Кога да | Кога НЕ | Печалба | Цена / алтернатива |
| --- | --- | --- | --- | --- | --- |
| OTel zero-code agent | tracing през поддържани HTTP/Redis/JDBC библиотеки | много услуги, трудни cross-service инциденти | когато логове/JFR са достатъчни или privacy и overhead са непроверени | end-to-end spans/context | startup/CPU/RSS, exporter/sampling, JDK support; manual spans / Spring Observations |

**Преди:** знаехме крайния JSON, не и кой downstream е извикан. **След:** имаме сравним бизнес договор и real-agent exporter proof. **Ограничение:** console exporter smoke доказва наличие на spans, но не production latency, full coverage или correlation correctness при всяка версия.

[Измервания / senior-level trade-offs](../../../java/jvm/java-agent-instrumentation/BENCHMARK-AND-DECISIONS.md) — baseline, cold start/steady p99, OTLP срещу `none`/console, cardinality и sampling.

**Проверки:**

- [ ] Пинната ли е agent версията и потвърден ли е JDK25/Spring Boot4.1.2 runtime?
- [ ] Има ли реален downstream network call и при cache hit липсва ли този call?
- [ ] Споделят ли inbound/outbound HTTP spans trace ID? Не предполагай — виж exporter output.
- [ ] Изключени ли са request bodies, tokens, personal identifiers от telemetry?
- [ ] Отделно ли мерим instrumentation, console logging и OTLP collector cost?
- [ ] Можем ли да махнем `-javaagent` и да възстановим същото business behavior?

## Оригинални източници

- [Bruce Bujon — The Magic of OpenTelemetry](https://www.youtube.com/watch?v=Lf_m5dIjjqw) (два предоставени SRT).
- [OTel Java agent setup](https://opentelemetry.io/docs/zero-code/java/agent/getting-started/), [configuration](https://opentelemetry.io/docs/zero-code/java/agent/configuration/), [upstream implementation](https://github.com/open-telemetry/opentelemetry-java-instrumentation).
- [OpenTelemetry SDK exporters](https://opentelemetry.io/docs/languages/java/configuration/).
- [Spring Boot RestClient](https://docs.spring.io/spring-boot/reference/io/rest-client.html), [Spring Redis](https://docs.spring.io/spring-boot/reference/data/nosql.html).
