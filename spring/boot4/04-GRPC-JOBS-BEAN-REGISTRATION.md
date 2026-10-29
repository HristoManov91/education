# P2 — gRPC, JobRunr vs Quartz и BeanRegistrar: инженерни избори

> Статус: **архитектурно обучение + план за отделни runnable labs**. В основния P1 модул няма включен gRPC server или JobRunr runtime. Следващите code fragments и test strategies са примери за дизайн, **не** претенция за преминал интеграционен тест.

## 1. Spring gRPC + Protocol Buffers

**Проблемът:** множество вътрешни услуги обменят структурирани данни с типизиран контракт; REST/JSON може да е достатъчен, но понякога е важно автоматично генериране на клиенти за различни езици, бинарен протокол и streaming.

| Избор | Преценка |
| --- | --- |
| Кога | Вътрешна service-to-service комуникация със schema-first договор и добър tooling. |
| Кога НЕ | Елементарен публичен CRUD API, browser-facing JSON, инфраструктура без HTTP/2/gRPC tooling или нужда от лесен ръчен debugging. |
| Печелим | Ясен `.proto` контракт, generated stubs, unary/server/client/bidirectional streaming. |
| Губим | Кодогенерация, versioning на полета, по-трудно ръчно инспектиране и инфраструктурни изисквания. |
| Алтернатива | Spring MVC + OpenAPI за публични REST клиенти. |
| Доказателство, което изискваме | Генерирани stubs + истински gRPC test за serialization, неизвестни полета, failure status, deadline/cancellation. |

**Илюстративен proto contract** (не е регистриран като runnable gRPC server в този PR):

```proto
syntax = "proto3";
package dogs;
option java_multiple_files = true;
option java_package = "bg.hristomanov.education.grpc.proto";

message ListDogsRequest {}
message Dog {
  int32 id = 1;
  string name = 2;
  string description = 3;
}
message ListDogsResponse {
  repeated Dog dogs = 1;
}
service DogService {
  rpc List(ListDogsRequest) returns (ListDogsResponse);
}
```

**Защо числата имат значение:** field numbers са част от wire contract; не преназначавай използван number на несъвместимо ново поле. При изтриване резервирай number/name. Ново поле обикновено се добавя с нов number, а не се преномерират старите.

**Тестови сценарии за отделен lab:** ① gRPC client извиква `List`; ② unknown field се игнорира/запазва според реалното proto/runtime поведение; ③ deadline прекратява чакащ caller; ④ status error се мапва; ⑤ две версии на schema комуникират коректно. Изискват реален server/client, не само unit test на mapper.

**Забележка за версиите:** Spring gRPC 1.0.x поддържа Spring Boot 4.1.x. Josh показва codegen и демонстрация с `grpcurl` около 29–35 мин. Грешно е да приемем, че gRPC е „винаги по-бързо“ — мерим wire size, p95 latency, CPU и proxy overhead за конкретни payload-и.

[Официално Spring gRPC Getting Started](https://docs.spring.io/spring-grpc/reference/getting-started.html) · [Protobuf compatibility guide](https://protobuf.dev/programming-guides/proto3/#updating).

## 2. Background jobs — JobRunr vs Quartz

**Проблемът:** request завършва, но фоновата задача трябва да се довърши дори след рестартиране или при няколко application instances. Простият `@Scheduled` без durable state не дава тези гаранции.

| Измерение | Quartz (JDBC clustering) | JobRunr |
| --- | --- | --- |
| Модел | Triggers/jobs, durable job store, мисфайър правила | Persisted background jobs, job handlers, retries |
| Кога е естествен | Сложни cron/calendars/triggers и съществуващ Quartz job store | Бизнес фонови задачи, за които job dashboard и persistable invocation са удобни |
| Кога не | За единична локална фоновa операция без durability | Ако Quartz вече покрива изискванията или edition/лицензът е проблем |
| Печалба | Детайлен scheduling control и зрелост | Сравнително лесен background execution model |
| Цена | Сложен setup, cluster maintenance и misfire semantics | Нови таблици, version/edition constraints, monitoring и migration risk |
| Общ риск | **At-least-once** execution е възможно; дублажите изискват idempotency | Същото — retry ≠ exactly once |

**Не мигрирай Quartz, защото е показан JobRunr в конференция.** Предварително измери:

1. Cron semantics, timezone/DST, misfires, pause/resume, failover и transactional enqueuing.
2. Jobs с външни side effects: idempotency key, recovery след partial success, observation/trace.
3. Нужен ли е paywalled feature? Провери актуалната **конкретна OSS/Pro версия** на JobRunr — dashboard/transaction интеграции и enterprise функции не се предполагат без лицензна проверка.
4. Ако вече използваш Quartz JDBC clustering, изчисли разхода от миграция спрямо реален проблем.

**Тестова матрица за бъдещ интеграционен lab** (с истински PostgreSQL и два worker процеса): persistent recurring job, два възела без едновременно ownership върху един fire, crash след side effect преди ack, повторно изпълнение и дедупликация, missed trigger след restart, timezone/DST. Това е по-ценен тест от „job се изпълни веднъж“.

Свързани готови примери: [Idempotency](../reliability/idempotency/README.md), [Transactional Outbox/Inbox](../messaging/outbox-inbox/README.md), [Saga](../messaging/saga/README.md).

[Quartz — Official documentation](https://www.quartz-scheduler.org/documentation/) · [JobRunr documentation](https://www.jobrunr.io/en/documentation/) · [Josh Long — лекцията](https://www.youtube.com/watch?v=1PtdpItmzTo).

## 3. BeanRegistrar срещу @Component и @Bean

**Проблемът:** трябва да регистрираме условен/динамичен брой beans — напр. един adapter за всеки конфигуриран tenant. Обикновен `@Bean` метод е лесен за статичен набор, но не създава динамично нови bean definitions в цикъл.

| Подход | Кога | Кога НЕ | Печалба | Загуба |
| --- | --- | --- | --- | --- |
| `@Component` | собствен клас със стандартно component scan | външни класове без контрол над анотациите | най-малко configuration | имплицитна регистрация |
| `@Bean` | контролираме изграждането на фиксиран брой зависимости | динамична регистрация на много имена | ясно lifecycle/constructor wiring | повече boilerplate |
| `BeanRegistrar` (Framework 7) | условни/programmatic bean definitions | само един нормален adapter | динамична registration logic | трудна диагностика и hidden config |

Сценичният demo (около 38–45 мин.) показва `BeanRegistrar` за JobRunr classes, но самият `JobRunr` **не изисква** `BeanRegistrar`. Не вкарвай нов API за DI без причина.

**План за proof:** register два именувани beans по конфигурация, assert на тип/брой/identity и negative test при duplicate name или грешна конфигурация. За прост статичен bean, `@Bean` остава предпочитаният вариант.

[Spring Framework — BeanRegistrar documentation](https://docs.spring.io/spring-framework/reference/core/beans/java/programmatic-bean-registration.html) · [@Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html).

## 4. Какво решихме / какво не

Тази глава **помага да изберем архитектура и правилни regression proofs**, без да въвеждаме нови зависимости в основния lab. Не сме инсталирали JobRunr, не сме мигрирали Quartz и не сме изградили gRPC integration. Това са **отделни P2 runnable labs**, не скрити „готови“ функции.

### Production review checklist
- [ ] Договорът и версията му са независимо публикувани?
- [ ] Нужен ли е streaming или REST е достатъчен?
- [ ] Job може ли безопасно да се изпълни два пъти?
- [ ] Кой владее lifecycle при restart/failover?
- [ ] Сравнихме ли разхода за инфраструктура и лицензи?
- [ ] Защо динамична DI регистрация вместо обикновено `@Bean`?

