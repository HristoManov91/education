# VM-22 — JSON, ARRAY, enum, time zone и Hibernate type mapping

## Production казус

В REST API записваме metadata за риск assessment и времето на събитието. На Java страната metadata е `Map<String,Object>`, статусът е enum, а времето е `Instant`. След миграция от Oracle към PostgreSQL сериализацията изглежда същата, но JSON query операторите, индексирането и timezone semantics не са идентични.

**Цел:** да докажем целия pipeline `Java object → JDBC/Hibernate → database type → обратно към Java`, не просто че annotation компилира.

## README → код

- [DocumentMetadata](../src/main/java/bg/hristomanov/education/hperf/domain/DocumentMetadata.java) — `@JdbcTypeCode(SqlTypes.JSON)`, `EnumType.STRING`, `Instant`, explicit replacement of JSON attributes.
- [DocumentState](../src/main/java/bg/hristomanov/education/hperf/domain/DocumentState.java) — enum вместо ordinal persistence.
- [PostgresAdvancedTypesIT](../src/test/java/bg/hristomanov/education/hperf/PostgresAdvancedTypesIT.java) — **истински PostgreSQL 17**: column `jsonb`, JSON `->>` query, update/dirty tracking, enum and timestamp roundtrip, SQL JSON null distinction.
- [PostgresNativeSqlIT](../../../database/sql-labs/src/test/java/bg/hristomanov/education/sql/PostgresNativeSqlIT.java) — PostgreSQL array containment and timezone type retrieval.

## Bad → root cause → good

**Bad:** сериализираме JSON в обикновен `VARCHAR`, пишем SQL `LIKE '%status%'`, очакваме ефективно JSON indexing; enum записваме по ordinal — добавяне на enum constant може да промени значенията; `LocalDateTime` за абсолютно timestamp и предполагаме, че съдържа UTC offset.

**Good:** Hibernate `SqlTypes.JSON` за JSON column, `EnumType.STRING` (с внимателна миграционна policy при rename), `Instant` за абсолютен момент, explicit reference timezone contract, DB-specific expression/index. Заместваме целия attributes `Map` с нов immutable map, вместо да разчитаме на незабелязана вътрешна mutable промяна.

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(nullable = false)
private Map<String, Object> attributes;

@Enumerated(EnumType.STRING)
private DocumentState state;
private Instant createdAt;
```

Това е кратък excerpt от [реалния Entity](../src/main/java/bg/hristomanov/education/hperf/domain/DocumentMetadata.java).

## SQL JSON null не е SQL NULL

PostgreSQL `'null'::jsonb` е JSON scalar и **не е** SQL NULL. JSONB и `text` не са взаимозаменяеми; формата, query operators и индекси зависят от DB. H2 JSON не е пълно доказателство за PG operators. Oracle 19c използва друг SQL/JSON модел — не твърдим автоматично проверено Oracle поведение.

## Decision contract

| Въпрос | Избор |
|---|---|
| Проблем | Нееднозначен JSON storage, нестабилни enum ordinals, timezone и mutable dirty tracking |
| Кога | Полуструктурирани metadata, рядко променящи се attributes, ясна schema и query contract |
| Кога НЕ | Силно релационни и често филтрирани бизнес полета, които заслужават нормализирани таблици |
| Печалба | Коректен тип, explicit mapping, database JSON filtering |
| Загуба | Dialect dependence, JSON serialization/config, сложни migration/index стратегии |
| По-просто | Нормализирани SQL колони и `@Embeddable` за стабилна структура |
| Доказваме | [PostgreSQL интеграционен тест](../src/test/java/bg/hristomanov/education/hperf/PostgresAdvancedTypesIT.java), не H2-only предположения |

## Упражнения

1. Защо enum ordinal е опасен при reorder? Какво става при rename на STRING enum?
2. Как се различава JSON `null` от SQL `NULL`?
3. Защо `Instant` е подходящ за абсолютен момент, а `LocalDateTime` не носи timezone?
4. Промени `risk` атрибута, flush/clear и покажи новото JSONB в DB.

## Оригинални източници

- [Vlad — Hibernate JSON mapping](https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/)
- [Hibernate ORM 7.4 basic types](https://docs.hibernate.org/orm/7.4/userguide/html_single/#basic)
- [PostgreSQL 17 JSON types](https://www.postgresql.org/docs/17/datatype-json.html)
- [PostgreSQL 17 datetime](https://www.postgresql.org/docs/17/datatype-datetime.html)

`mvn -pl persistence/hibernate-performance -Ppostgres-integration verify`
