# P2 — OpenTelemetry, SSRF защита и JVM/Leyden: измервания преди обещания

## 1. Observability: „работи“ не означава „можем да го диагностицираме“

**Проблем:** latency на API расте. Без наблюдаемост не знаем дали причината е DB pool, HTTP retry, CPU или lock contention.

| Решение | Кога | Кога не | Печалба | Цена/риск |
| --- | --- | --- | --- | --- |
| Actuator + Micrometer | за Spring production APIs | като подмяна на бизнес диагностика | готови health/metrics hooks | cardinality, overhead, sensitive endpoints |
| OpenTelemetry | когато трябва end-to-end trace между услуги | за микроскопичен локален demo без collectors | correlation между spans/metrics/logs | exporter, sampling, collector и cost |
| Grafana dashboards | за SLO/incident response | ако няма надежден telemetry pipeline | проследими промени, trends | dashboard maintenance |

Josh демонстрира starter за OpenTelemetry и готов Grafana setup (около 27–28 мин.), **но това не гарантира автоматично production tracing** при всяка deployment среда. Конфигурирай сигурен endpoint, exporter, auth, sampling и cardinality budget.

### Какво да наблюдаваме в истинския lab

- HTTP p95/p99, error rate, активни заявки.
- Hikari active/idle/pending, connection acquisition latency.
- Retry attempts per result, timeout и error classification.
- JVM threads (platform/virtual), heap, GC pauses, CPU load.
- Span propagation през HTTP/gRPC и пренасяне на trace context в background jobs.

Нашият P1 lab има [TransactionBoundaryProbe](src/main/java/bg/hristomanov/education/boot4/TransactionBoundaryProbe.java) и [Hikari test](src/test/java/bg/hristomanov/education/boot4/LazyConnectionsTest.java) за точно едно resource behavior. **Той не съдържа collector/Grafana или end-to-end trace assertion.** Production OTel е отделен интеграционен lab. За диагностични JVM примери виж [JFR](../../java/jvm/jfr-troubleshooting/README.md).

[Spring Boot observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html) · [OpenTelemetry concepts](https://opentelemetry.io/docs/concepts/).

## 2. SSRF: изборът на HTTP client не е security boundary

**Проблем:** нападател контролира URL, който backend извиква, например `http://169.254.169.254` или частен адрес.

**Naive:** приемаме, че `RestClient`/`@HttpExchange` прави URL безопасен сам по себе си.

**Good:** фиксирана trusted destination, `InetAddressFilter` / network destination filtering, egress firewall, validation на scheme/host/redirects, наблюдение на blocked attempts. Spring Boot 4.1 предоставя механизми за address filtering, но те се **конфигурират**, не се появяват автоматично като пълна защита.

| Вариант | Кога | Ограничение |
| --- | --- | --- |
| Allowlist на trusted hosts | фиксирани външни зависимости | lifecycle на hostname/DNS/redirects |
| Забрана за private/link-local адреси | untrusted URLs/SSRF threat | не замества egress policies |
| Network egress deny | defense-in-depth на инфраструктурно ниво | нужда от platform team и observability |
| Без server-side fetch от user URL | когато функционално е възможно | отказ от част от продукта |

**Proof за отделен lab:** local mock server като разрешен target; loopback/private IP и redirect към забранен target трябва да бъдат блокирани от реално конфигурирания HTTP client. Не твърди успех без тези тестове.

[Spring Boot HTTP clients](https://docs.spring.io/spring-boot/reference/io/rest-client.html) · [OWASP SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html).

## 3. Java 25/27, Project Leyden и AOT cache

Лекцията показва Java 27, compact object headers и AOT cache (около 47–49 мин.). **Тези JVM техники не са Spring Boot 4 изисквания.** Нашата runnable база остава Java 25; експериментите с Java 27 трябва да са в отделна версия/матрица.

| Избор | Решава | Кога | Кога не | Печалба / загуба |
| --- | --- | --- | --- | --- |
| CDS/AOT cache | startup class loading / linking overhead | след измерен cold-start проблем | ако startup не е SLO | потенциално по-бърз startup; нов build/training step |
| Compact object headers | част от object-header memory overhead | workload с много обекти и измерена memory pressure | ако bottleneck е native memory или DB | потенциално по-малък heap; резултатът зависи от workload |
| GraalVM Native Image | startup/footprint цел | подходящ deployment и проверена reflection config | при сложна dynamism/maintenance цена | performance потенциал, build и compatibility trade-offs |

### Reproducible experiment design

1. Pin Java version, OS, CPU limit, heap, warmup policy, image/container и seed данните.
2. 20+ cold start runs поотделно за baseline и AOT; дефинирай startup readiness, а не само времето до `main`.
3. Мери median, p95, RSS/heap и throughput **след** startup.
4. Съхрани build flags, cache training profile и как се инвалидира cache след dependency/code changes.
5. Сравни CPU-bound и I/O-bound натоварвания; не пренасяй „10–20% RAM“ от сцената като универсална гаранция.

Това е **методика за benchmark**, не твърдение, че вече сме измерили резултат. В Education има готови [JVM performance evolution](../../java/jvm/performance-evolution/README.md) и [JFR](../../java/jvm/jfr-troubleshooting/README.md); използвай тях за по-подробните лаборатории.

## 4. Практичен review checklist

- [ ] Какво е SLO: startup, latency, throughput, memory или recovery?
- [ ] Имаме ли baseline, стабилна версия и повторяеми условия?
- [ ] Какво е business value, а не само benchmark speedup?
- [ ] Събираме ли само необходимата telemetry без secrets/PII?
- [ ] SSRF protection е приложена и доказана на client + network ниво?
- [ ] Назовани ли са overhead, costs и operational constraints?

## 5. Изход

Обяснихме как да диагностицираме проблем и какви тестове са необходими, преди да обявим OTel/SSRF/Leyden готови за production. Основният P1 lab демонстрира **API contracts, JDBC acquisition и resilience**; пълни Grafana/SSRF/AOT integration tests остават отделни P2 задачи.

## Оригинални източници
- [Josh Long — Bootiful Spring Boot 4](https://www.youtube.com/watch?v=1PtdpItmzTo).
- [Spring Boot 4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes).
- [Spring Boot observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html).
- [Project Leyden — OpenJDK](https://openjdk.org/projects/leyden/).
- [JDK 25 — JEP 519 Compact Object Headers](https://openjdk.org/jeps/519).

