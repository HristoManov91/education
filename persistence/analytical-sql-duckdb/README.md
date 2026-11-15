# P2 · From Laptop to Lakehouse — Analytical SQL with DuckDB (Java 25)

> **Изпълним Java 25 + DuckDB 2.0 JDBC + JUnit урок**. Източник: [Oliver Geisser — From Laptop to Lakehouse: Analytical SQL with DuckDB](https://www.youtube.com/watch?v=B5-4mDLrWjM), предоставен SRT (~29 мин, автоматичен). Кодът и 10-редовите GPS fixtures са **наша Education адаптация**, не данните/проектът на лектора.  
> Използваме `org.duckdb:duckdb_jdbc:2.0.0.1`, [официален installation guide](https://duckdb.org/docs/2.0/installation), без Docker, credentials, server и външни услуги. Основният Java25 Maven reactor остава съвместим.

## 1. Production-like казус: аналитичен отчет без тежък OLTP query

Бекенд събира GPS координати в Oracle/PostgreSQL. Нужна ни е аналитична справка за исторически събития: брой точки и уникални автомобили по час, статус движение, филтър около фискална точка и последно наблюдение на всеки регистрационен номер. Понякога е по-разумно да експортираме **одобрен snapshot** в Parquet, вместо да пускаме тежък ad-hoc анализ в оперативната база.

**Bad:** DuckDB да замести OLTP транзакциите; или автоматично да пуснем недоверен SQL към live Oracle; или да прочетем огромен CSV в Java List, без лимити/измерване.  
**Good:** SQL върху локален snapshot в embedded DuckDB, проверен резултат с JUnit и измерими I/O/memory/CPU ограничения.

**Важно:** DuckDB е **in-process аналитичен SQL engine**, не просто Java библиотека. **DuckLake** е каталог + Parquet lakehouse формат; **Quack** е различен remote protocol. Не приемай, че инсталирането на JDBC автоматично конфигурира многопотребителски lakehouse.

## 2. Какво действително показва лекцията (ориентировъчни SRT времена)

| Време | Демонстрация | Извлечен принцип |
| --- | --- | --- |
| 00:30–04:30 | DuckDB CLI; in-memory vs file DB; column-oriented OLAP | embedded analytics vs remote transactional server |
| 04:30–07:20 | Java JDBC, Python; native C++ engine, row-based JDBC и columnar chunks | Java JDBC лаб, Arrow/chunk API само P3 |
| 07:20–10:55 | CSV/Parquet, auto type inference, 40 млн. реда в представената демонстрация, larger-than-memory | direct file SQL, explicit schema; **не твърдим 40 млн. benchmark при нас** |
| 10:55–13:05 | remote HTTP/S3, persistent secrets извън код | security checklist, никакви реални credentials |
| 13:05–16:10 | `ATTACH` към SQLite/PostgreSQL и federated joins | cross-source consistency/cost |
| 16:10–21:40 | DuckLake: SQL catalog и local/S3 Parquet файлове | architecture/metadata catalog |
| 21:40–27:45 | native file exclusive writer lock; Quack server/client в ранна alpha версия | multi-client design & version update |
| 27:45–28:45 | resources, WebAssembly browser shell | optional exploration, не production claim |

**Корекция след лекцията:** [DuckDB 2.0.0 LTS е публикуван на 21.10.2026](https://duckdb.org/2026/10/21/announcing-duckdb-200) и включва Quack и `CONNECT`. Към ноември 2026 DuckLake 1.1 работи с DuckDB 2.0+. Alpha статусът в записа **не е текущият release статус**.

## 3. OLTP срещу OLAP — decision contract

| | Типичен Oracle/PostgreSQL OLTP | DuckDB OLAP |
| --- | --- | --- |
| Цел | транзакции, state changes, конкурентни writes | scans, joins, aggregations, window functions |
| Рънтайм | отделен server/cluster + clients | embedded engine в Java/CLI процес |
| Съхранение | таблици, индекси, журнал/транзакции | DuckDB file, директен CSV/Parquet или DuckLake |
| Печалба | integrity/concurrent operational requests | columnar/vectorized SQL върху files без отделен server |
| Цена | operational DB ресурс при тежък отчет | export/refresh lag, local memory/I/O, JVM native dependency, data governance |

**Кога:** offline/batch/ad-hoc read analytics, разграничени от live OLTP натоварване. **Кога НЕ:** primary многопотребителска transactional база, point lookup който вече има добър индекс, real-time single source of truth. **По-просто:** оптимизиран индекс/SQL отчет в съществуващата база, ако е евтин и безопасен.

## 4. README → реален код → 12 JUnit проверки

| Пример | Файл/доказателство |
| --- | --- |
| Embedded JDBC `jdbc:duckdb:` без сървър | [TrafficAnalytics.java](src/main/java/bg/hristomanov/education/duckdb/TrafficAnalytics.java) |
| `read_csv` с типизирани timestamp, plate, decimal, boolean, tracking_code | [10 synthetic GPS records](src/test/resources/traffic-events.csv) |
| `GROUP BY date_trunc('hour', ...)`, `count(distinct plate)`, moving filter | [JUnit tests](src/test/java/bg/hristomanov/education/duckdb/TrafficAnalyticsTest.java) |
| JDBC `PreparedStatement` за фискална точка и радиус | еднозначно очаквани counts с exact decimal |
| CSV leading zeros `00001` | изричен `VARCHAR`, без silent numeric conversion |
| `COPY (...) TO ... (FORMAT PARQUET, COMPRESSION ZSTD)` | **реален** Parquet export в `@TempDir` |
| `read_parquet` + `row_number() OVER (PARTITION BY plate...)` | последна точка за всеки от 4 камиона |
| `EXPLAIN` / file vs in-memory база | executable план + reopen persisted file |
| Negative / safety | missing CSV, неверен output format, overwrite, negative distance |

Стартирай от root с Java25/Maven:

~~~bash
mvn -pl persistence/analytical-sql-duckdb test
mvn -pl persistence/analytical-sql-duckdb -Dtest=TrafficAnalyticsTest test
mvn --no-transfer-progress verify
~~~

Само първото dependency resolution изисква достъп до Maven Central. Няма външен контейнер, HTTP download на extensions, Postgres, S3 или telemetry.

### Примерни SQL резултати, *не performance benchmark*

~~~sql
SELECT date_trunc('hour', event_time) AS bucket,
       count(*) AS points,
       count(DISTINCT plate) AS trucks,
       count(*) FILTER (WHERE moving) AS moving_points
FROM read_csv('traffic-events.csv', header = true, auto_detect = false,
  columns = {'event_time':'TIMESTAMP', 'plate':'VARCHAR',
             'nearest_fp':'INTEGER', 'distance_km':'DECIMAL(9,3)',
             'moving':'BOOLEAN', 'tracking_code':'VARCHAR'})
GROUP BY 1 ORDER BY 1;
~~~

10:00 → **4 GPS точки и 3 камиона**; 11:00 → 4/3; 12:00 → 2/2. Това са изчисления от [нашата фикстура](src/test/resources/traffic-events.csv), не измерена скорост на real production dataset.

~~~sql
SELECT plate, event_time, distance_km FROM (
    SELECT plate, event_time, distance_km,
           row_number() OVER (
              PARTITION BY plate ORDER BY event_time DESC
           ) AS rn
    FROM read_parquet('events.parquet')
) WHERE rn = 1 ORDER BY plate;
~~~

В истински ingest поток, където има еднакви timestamps, добави **tie-breaker** (event ID, sequence), deduplication/late arrival policy. Съществуващият ни SQL/QueryDSL материал е за OLTP/query composition; тук **съзнателно** разширяваме към analytical SQL.

## 5. CSV typing, Parquet pushdown и `EXPLAIN`

DuckDB CSV sniffer може да открива delimiter, headers, types. Но `00001` не е непременно числото `1` — понякога е идентификатор. Затова в реалния ни query задаваме `columns=...`, вместо да оставяме бизнес schema inference на heuristics.

Parquet пази колонни данни и статистика. SQL engine може да push-не projection/filters и да skip-не segments, но **само ако planner, data layout и stats го позволяват**. Проверяваме реален `EXPLAIN`, без тест „трябва да съдържа точно този физически оператор“. Не заключавай, че е 5×/10× по-бързо без представителен benchmark.

[Hardwood/Parquet Case 005](../../docs/case-studies/005-hardwood-ai-parquet-engineering.md) разглежда page-level decode/parallelism/pruning. **Нашият DuckDB модул е SQL consumer на Parquet**, не нов Parquet parser.

## 6. Федерация и lakehouse — от CLI към production архитектура

~~~text
Java / CLI
   |
embedded DuckDB SQL
   |---- local CSV / Parquet (тествано)
   |---- ATTACH SQLite / PostgreSQL (manual)
   |---- HTTP / S3 object storage (manual, credentials required)
   |
   +---- DuckLake = SQL metadata catalog + Parquet files
                       |
                    Quack = optional remote DuckDB client/server
~~~

Важните trade-offs (описани в [отделната архитектурна бележка](MANUAL-LAKEHOUSE.md)):

- **Native DuckDB file** може да налага exclusive writer lock между процеси. Не го третирай като shared multi-writer Postgres.
- **DuckLake каталогът** може да бъде DuckDB (single local process), SQLite (multi-process с ограничения), PostgreSQL (remote multi-user), или Quack-based подход; Parquet е data storage, каталогът е metadata. 
- **Quack** е protocol, а не еквивалент на DuckLake. Не гарантира автоматично HA, security или horizontal scale.
- **Federated JOIN** към remote DB не дава обща distributed transaction или идентичен snapshot на всички източници.
- **Larger-than-memory** SQL използва spill/temp и disk I/O, но disk quota, memory pressure и query shape могат да причинят отказ.

## 7. Сигурност

DuckDB има достъп до локалната файлова система на процеса. С потребителски SQL или произволни paths могат да се прочетат нежелани файлове. **Тук входовете са application-selected local files**; file path е canonicalized и SQL-quoted, а филтрите са PreparedStatement параметри. Това **не** е security sandbox за недоверени файлови пътища.

DuckDB persistent secrets се пазят извън Git source, но **не са криптирани на диска** според документацията. За cloud избери ограничен IAM/credential chain scope. Преглеждай extensions, S3 access, private data export, query/resource limits, network egress. Нито един CI тест не прави cloud calls.

## 8. Decision contract и самопроверка

**Проблем:** ad-hoc аналитични SQL справки върху CSV/Parquet, изолирани от OLTP workload.

**Кога:** local experiment, batch reporting, export/reconciliation, read-only snapshots.

**Кога НЕ:** replacement на OLTP, live multiwriter sharing на един файл, неподписан/неконтролиран SQL или невъзможна законна експортна политика.

**Печалби:** SQL without server, columnar scan, JDBC, open formats.

**Загуби:** native dependency, export lag, additional data copies, memory/temp storage and governance.

**Алтернатива:** добър SQL/index в Oracle/Postgres; за micro dataset — CSV + Java.

**Proof:** JUnit тестове и файл Parquet от действителен `COPY`. **Не твърдим:** 40 млн. реда benchmark, S3 performance, production DuckLake/Quack deployment или production миграция.

Провери се: Какъв е difference между DuckDB и DuckLake? Кога read-only snapshot е по-добър от federation? Защо `COUNT(DISTINCT plate)` не е равен на `COUNT(*)`? Как пречи CSV type inference? Какво доказва `EXPLAIN`, но не доказва JUnit? Какъв е operational cost на spills? Защо Quack не е просто „втори JDBC connection“?

## Източници

- [Oliver Geisser — Devoxx](https://www.youtube.com/watch?v=B5-4mDLrWjM), предоставен SRT (00:00–28:50).
- [DuckDB JDBC](https://duckdb.org/docs/2.0/clients/java/overview), [DuckDB 2.0.0 release](https://duckdb.org/2026/10/21/announcing-duckdb-200), [2.0.0.1 installation](https://duckdb.org/docs/2.0/installation).
- [CSV](https://duckdb.org/docs/2.0/data/csv/overview), [Parquet](https://duckdb.org/docs/2.0/data/parquet/overview), [Security](https://duckdb.org/docs/2.0/operations_manual/securing_duckdb/overview).
- [DuckLake](https://ducklake.select/docs/stable/), [Catalog choice](https://ducklake.select/docs/stable/duckdb/usage/choosing_a_catalog_database).
