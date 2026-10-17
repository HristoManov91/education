# Case Study 002 — Commerzbank: 11 TB Oracle Exadata, cold cache и архитектурен инцидент

> **Тип:** реален production инцидент и последващо архитектурно преосмисляне (документация, не изпълнима лаборатория).  
> **Първичен източник:** Martin Ladecký (Commerzbank), *10 Billion Records vs. The Regulator: An Architectural Post-Mortem of an 11TB Exadata Failure*, Devoxx Belgium 2026, 9 октомври 2026 г.  
> **Приоритет:** **Tech Radar → P1 — Real-World Engineering / Database Performance / System Design**; пряка връзка с P0 SQL/Oracle и Reliability.  
> **Статус:** документирано по лекцията и официални технически източници на 17.10.2026; **не е възпроизведено, нямаме изпълним тест и не твърдим, че бъдещата миграция е приключила**.  
> **Ключови думи:** `Exadata`, `cold cache`, `Smart Flash Cache`, `SQL`, `ROW_NUMBER`, `anti-join`, `query window`, `read-side deduplication`, `write-side integrity`, `dual-write`, `Oracle`, `PostgreSQL`, `CDC`, `canary migration`.

## 0. Quick retrieval card — какъв е урокът?

**Симптом:** клиентите не успяват да работят с банковото приложение, но Oracle продължава да изпълнява заявките и системното наблюдение вижда предимно успешни HTTP отговори.  
**Trigger:** след disaster-recovery тест и рестартиране/прехвърляне на инстанция системата трябва да обслужва натоварването със студени кешове.  
**Основна уязвимост:** много скъпа заявка над огромни таблици извършва дедупликация и сверяване на pending/booked транзакции при всяко четене; отпаднал преди години времеви филтър позволява прекомерна обработка.  
**First aid:** временно увеличаване на frontend timeout — **засяга симптома, не причината**.  
**Tactical fix:** възстановяване на валидиран **10-дневен прозорец** за търсене.  
**Strategic direction:** дедупликация и проверка на целостта при импорт, по-прост read model, две целеви таблици и постепенна миграция към PostgreSQL.  
**Mental model:** силният хардуер и топлият кеш могат да маскират алгоритмична/архитектурна цена; **същият execution plan ≠ същата реална производителност**.

**Защо е в нашата библиотека:** това е пример как един екип управлява едновременно operational incident, ограничена възможност за експерименти в production, изисквания на регулатор, техдълг и рискована миграция — не просто „как да оптимизирам SQL“.

## 1. Контекст и мащаб

Според лектора системата обслужва транзакции и салда на голяма германска банка, с приблизително:

| Показател | Спомената стойност / контекст |
| --- | --- |
| Обем на Oracle Exadata по време на инцидента | **11 TB** |
| Обем към представянето | около **14 TB** |
| Размер на основните данни | порядък **10 милиарда реда** към инцидента; растяща история |
| Голям индекс | около **700 GB** |
| Таблици/потоци с pending състояния | стотици милиони записи; един от споменатите източници е около **500–700 млн.** |
| Трафик | приблизително **20 млн. read** и **40 млн. write операции дневно**, както са представени от лектора |
| Нормална латентност | около **20–40 ms** за обичайните заявки |
| По време на инцидента | от стотици милисекунди до **20 минути** при някои големи сметки |
| Видим клиентски проблем | timeout/lockout при **30 секунди** на фронтенда |
| Някои заявки | връщат едва около **40 транзакции** след огромна междинна работа |

Числата са **ориентири от лекцията**, не измервания от наше приложение; отделни размери се отнасят за различни таблици и моменти. Не ги събирай механично.

**Ключов контекст:** банковата система подлежи на строги ограничения за сигурност, промени, достъп до инфраструктура и наличност. Екипът не може просто да рестартира production, да инсталира произволен агент или да изпита „какво ще стане“, като изтрие кеша.

## 2. Incident timeline — симптом → последователност → причина

| Стъпка | Какво описва лекторът | Инженерно значение |
| --- | --- | --- |
| 1. Сигнал | Потребителите не могат нормално да влязат/видят транзакции; на инцидентния разговор има стотици участници | Работоспособността не се определя само от липсата на exception |
| 2. Наблюдение | APM/HTTP статусите не показват явен масов failure; натоварването и I/O в базата са много високи | Трябва да се измерва **end-to-end business transaction**, не само HTTP 2xx |
| 3. Външен timeout | Фронтендът отхвърля потребителска сесия, ако необходимите данни не пристигнат до 30 s | Timeout на един слой може да скрие резултата на по-бавен downstream слой |
| 4. Emergency mitigation | Timeout е увеличен така, че около **90%** от потребителите да могат да изчакат отговор — deployment според лектора е отнел около **15 min** | Възстановяване на функционалността преди пълен RCA |
| 5. Връзка с DR | Установяват скорошен disaster-recovery тест/рестартиране и промяна на обслужващата инстанция | След рестарт/прехвърляне **кешовете може да са студени** |
| 6. Проверка | Execution plan изглежда непроменен, но I/O и време са далеч по-лоши | Plan hash сам не обяснява cache residency, physical reads и latency |
| 7. Решение | Избират **контролирано изчакване и наблюдение**, вместо рисково ново превключване или агресивно cache warming | „Не променяме системата“ може да е обоснована **активна** оперативна стратегия |
| 8. Tactical fix | Връщат 10-дневно ограничение, отпаднало при стар refactoring; сверяват бизнес времевите сценарии | Премахват прекомерното количество кандидати за обработка |
| 9. Следващ DR | По разказа следващият DR тест преминава без предишния проблем | Положителна реална проверка, **не** универсално доказателство за всички натоварвания |
| 10. Redesign / migration | Преместват част от дедупликацията на write side и проектират Oracle → PostgreSQL migration | Това са **етапни промени**, не приключена история |

**Неизвестно:** от публичния разказ не разполагаме с оригиналните production SQL, AWR/ASH снимки, графики на cell cache hit rate или идентични изпитателни данни. Не можем да докажем точно кой cache layer е доминирал. Лекторът специално споменава columnar cache като своя хипотеза, а не като окончателно доказан RCA.

## 3. Оригиналният data flow и скъпото четене

Упростена схема, **реконструкция по описанието**, не оригинална инфраструктурна диаграма:

~~~mermaid
flowchart LR
  A[~15 upstream източника: API / Kafka / DB / файлове] --> I[Importers]
  I --> B[(Booked transactions: огромна история)]
  I --> P[(Local pending / audit-like changes)]
  X[(Външен екип: pending view с исторически състояния)] --> Q[Read-time merge и latest-row избор]
  P --> Q
  Q --> D[Read-time deduplication]
  B --> D
  U[Клиент: покажи транзакции / салдо] --> D
  D --> O[Filter / JOIN / ORDER: малък резултат]
  O --> U
~~~

При всяко четене приблизително се случва:

1. Определяме актуалното състояние от повтарящи се промени в pending източниците — например **`ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...)`**.
2. Обединяваме pending записи от два източника, в които една логическа транзакция може да се повтори.
3. Проверяваме кои pending транзакции вече са **booked**, за да ги изключим.
4. Филтрираме, подреждаме и връщаме малък набор транзакции на потребителя.

Това звучи логично като бизнес алгоритъм, но при милиарди записи **цената на изчисляване на текущото състояние се заплаща при всяко четене**. Подходящ кеш и мощен Exadata хардуер могат дълго да прикриват проблема.

### Схематичен SQL — НЕ е оригиналната заявка

~~~sql
WITH latest_pending AS (
    SELECT p.transaction_id,
           p.account_id,
           p.changed_at,
           ROW_NUMBER() OVER (
               PARTITION BY p.transaction_id
               ORDER BY p.changed_at DESC, p.event_id DESC
           ) AS rn
    FROM pending_events p
)
SELECT p.transaction_id
FROM latest_pending p
WHERE p.rn = 1
  AND p.account_id = :account_id
  AND NOT EXISTS (
      SELECT 1
      FROM booked_transactions b
      WHERE b.transaction_id = p.transaction_id
  );
~~~

**Учебна илюстрация на опасния клас заявки:** междинните данни, оптимизаторските трансформации, наличието на подходящи индекси и изпълнителният план определят реалната цена. Не твърдим, че показаната форма винаги сканира всички записи. Реалният SQL от лекцията е по-сложен и е бил анонимизиран.

### Защо „`200 OK`“ не означава „успешна система“

~~~mermaid
sequenceDiagram
  participant U as Потребител
  participant F as Frontend / session
  participant A as API
  participant D as Oracle Exadata
  U->>F: Login + показване на транзакции
  F->>A: Fetch transactions
  A->>D: Скъпа read-side заявка
  Note over D: Студен кеш + много I/O<br/>заявката продължава да работи
  F-->>U: Timeout / прекъсната сесия след 30 s
  D-->>A: SQL success, но твърде късно
  A-->>F: HTTP 200 (може вече да не помага)
~~~

**Измерване:** добави synthetic journey / real-user metric „вход → транзакции се виждат“, latency по размер на акаунта, frontend timeouts, съотношение заявки завършили *преди* клиентския deadline и late downstream completions. Отделно наблюдавай SQL duration и ресурсите на базата. Не се доверявай само на response status.

## 4. Cold cache — как да мислим технически

Oracle Exadata има няколко нива на кеширане и storage offload. Smart Flash Cache пази често използвани данни във flash; определени конфигурации могат да използват и In-Memory Columnar Caching. **Не приемай, че всеки Exadata има идентични кешове или че columnar caching е активен без съответните опции.** Виж официалните източници по-долу.

- **Warm:** често заявяваните блокове/колони са в бързите кешови слоеве, затова тежък SQL може да изглежда приемлив.
- **Cold:** след restart/DR/сменен workload cache residency е различен; нарастват physical reads, storage latency и/или обработката извън кеша.
- **Execution plan:** същият оператор и plan hash могат да изпълнят много повече физически I/O при същата логическа заявка. Планът е необходим, но **не е достатъчен** за RCA.
- **Контрафактичен тест:** проверявай „първо изпълнение при студено/почти студено състояние“ срещу повторно изпълнение, само в контролирана среда. Не изключвай production/cache без change plan.

**Важна корекция към често срещано опростяване:** „index“ не означава евтин `JOIN` при всяка кардиналност. Индекс за стотици GB и огромни входни множества може да не спести най-скъпите операции; трябва да разгледаме access path, join strategy, actual rows, physical reads и селективността на предикатите.

## 5. Три хоризонта на решението: first aid → tactical → strategic

| Хоризонт | Действие по лекцията | Полза | Цена / риск |
| --- | --- | --- | --- |
| **First aid** | Увеличаване на frontend timeout | Бързо възстановява достъпа за голяма част от клиентите | Натоварването не намалява; по-дълги висящи заявки, connection/thread pool saturation, retry amplification |
| **Controlled observation** | Без агресивно рестартиране/сменяне на инстанция; следят затоплянето на кеша | Избягва второ „студено“ превключване и дополнительен I/O от масово prewarm | Остава период на деградация, необходимо е доказателство, мониторинг и go/no-go праг |
| **Tactical** | Възстановяват конфигурируем **10-дневен query window** | Ограничават излишния read-time обем, преди следващия DR | Може да изключи легитимни стари pending записи, ако бизнес invariant-ът е неточен |
| **Strategic** | Изчисляване и дедупликация при write/import; booked и pending read model | Евтини, предсказуеми заявки; по-малко runtime joins/dedup | По-сложен importer, out-of-order събития, идемпотентност и consistency |

### Невидимата цена на увеличения timeout

Примерна оценка за инженерното мислене, **не измерване от банката**: ако системата започне да държи десетки или стотици заявки по-дълго, това може да изчерпи лимитирани concurrency ресурси. В Java/Spring анализирай:

- HTTP/client deadlines по цялата верига (browser → gateway → service → JDBC).
- Размер и wait time на JDBC connection pool; scheduler/threads; отменени заявки и SQL, който продължава.
- Опашки, retries, backpressure, circuit breakers и saturation.
- Кога удълженият timeout е **временен feature flag с дата за връщане**, а не трайно решение.

### Защо „не правим нищо“ може да е правилно

Екипът има наблюдения, че повторният достъп до същите клиентски данни е значително по-бърз. Следователно контролираното изчакване има проверима хипотеза **cache warming**. Алтернативите — нов failover към също студена инстанция или масов `SELECT` за prewarming върху вече натоварена машина — могат да влошат ситуацията.

Такова решение изисква **time-box, наблюдавани показатели, известна граница за ескалация и отговорен собственик**. „Нямаме идея какво става“ не е същото като „наблюдаваме стабилно подобрение и не въвеждаме нов риск“.

## 6. Tactical fix — 10-дневният филтър

При стар refactoring времевото ограничение е отпаднало. По време на инцидента това е критично, защото включването на огромна история в read-time дедупликацията е ненужно за типичния бизнес процес.

**Пример само за идеята на ограничен кандидатен набор (не production SQL):**

~~~sql
SELECT transaction_id, event_time
FROM transaction_events
WHERE event_time >= :window_start;
~~~

**Не добавяй този филтър на сляпо в реален query.** Трябва да знаем *кой* timestamp се използва (created/changed/booked), кои pending записи могат да останат валидни по-дълго, има ли празници/междубанкови забавяния, ретроактивни корекции и как се доказва, че няма загубени резултати. В лекцията прозорецът е сверяван с бизнес колеги и календарни сценарии и е станал **параметър**, вместо скрит hardcode.

**Кога намалява I/O?** Само при подходяща структура на данните, достъп и предикат — index range scan, partition pruning, storage predicate pushdown и др. Не обещавай автоматично „без full scan“ само защото има `WHERE`. Сравнявай **actual rows, buffers, physical reads, elapsed time** и коректността на резултата.

## 7. Дългосрочна промяна: write-side integrity вместо read-time deduplication

Лекторът предпочита една таблица, но екипът избира **две**: booked и pending, защото имат твърде различни атрибути (малко общи полета). Няма универсално правило „една таблица е най-добра“ — домейн моделът и query patterns са по-важни.

~~~mermaid
flowchart LR
  A[API / Kafka / file / DB източници] --> I[Importers / mapping / validation]
  I --> R[Stable transaction identity + version ordering]
  R --> D[Dedup + state transition при запис]
  D --> B[(Booked current)]
  D --> P[(Pending current)]
  B --> Q[Опростени account reads]
  P --> Q
  Q --> U[Потребител]
~~~

**Цел:** клиентската заявка вече да чете текущо, подготвено състояние, а не да преизчислява историята. Според лекцията към конференцията **само част от преместването вече е внедрена**; външният feed изисква допълнителна интеграция.

### Write-side invariants, които нашият анализ изисква

1. **Stable business identity:** как разпознаваме една и съща транзакция между два източника, без да смесваме различни транзакции?
2. **Version/order:** как различаваме стара промяна от нова? Timestamp сам по себе си невинаги е безопасна business version.
3. **Idempotency:** повторно получено събитие не трябва да създава нова транзакция или да връща по-стар статус.
4. **Concurrency:** уникални ограничения и атомарни state transitions; без опасен check-then-insert race.
5. **Lifecycle:** при pending → booked няма момент, в който клиентът вижда и двата реда или губи транзакцията (според consistency contract).
6. **Replay/backfill:** историческите промени и live поток не трябва да се презаписват в грешен ред.
7. **Reconciliation:** детектираме пропуснато или сгрешено състояние, вместо да разчитаме само на „няма exception“.

**Компромисът не е очевиден:** ако имаме около **40 млн. writes срещу 20 млн. reads** дневно, преместването на работа към write path може да увеличи общата работа по импорт. Решава се с измерване на честота × цена × business latency/SLA, а не с лозунга „CQRS винаги е по-бърз“.

**Свързани наши теми:** [CQRS / separate read model](../../spring/architecture/cqrs/README.md), [Idempotency](../../spring/reliability/idempotency/README.md), [Transactional Outbox + Inbox](../../spring/messaging/outbox-inbox/README.md).

## 8. Oracle → PostgreSQL: защо CDC не е избран и защо dual-write не е гаранция

Лекторът обсъжда отказ от типичен CDC инструмент като Debezium **за конкретната им среда**: тежко регулиран Exadata, ограничения за DBA привилегии, промени по инфраструктурата и договорна поддръжка. Те управляват всички записи в *собствената си schema* чрез importers, затова разглеждат писане към Oracle и PostgreSQL от този контролируем слой.

**Критична фактологична корекция:** `Debezium Oracle` **не изисква като универсално runtime правило SYSDBA и инсталиране на агент в Oracle хоста**. Има различни adapter-и и привилегии/предварителни DBA настройки (например LogMiner). Това, че CDC не е разрешен/приемлив в организацията на лектора, **не означава, че Oracle CDC е технически невъзможен за други организации**. Преди избор провери версия, licensing, архитектура, security approvals, поддръжка, operational cost и достъпни grants.

### Предложена етапна миграция (планът на банката, не завършен резултат)

~~~mermaid
flowchart TD
  S[~15 източника] --> I[Importers — контролиран write path]
  I --> O[(Oracle: текуща production база)]
  I --> P[(PostgreSQL: нова база)]
  H[Исторически backfill: ~11 TB + catch-up] --> P
  O --> C[Reconciliation: counts + business keys + state/value comparison]
  P --> C
  U[Client read router] --> F{Feature flag / canary cohort}
  F -->|останалите клиенти| O
  F -->|1%, 2%, 5%, ...| P
~~~

Последователността от лекцията: (1) паралелно захранване на новата база, (2) прехвърляне на старите данни, (3) сравнение и бизнес пилоти, (4) постепенно маршрутизиране на read traffic към PostgreSQL с бърз път обратно към Oracle. При големи обеми network bandwidth, maintenance windows, права и форматите за дати/числа са реални ограничения. Лекторът говори за **proof of concept и планирана миграция**.

### Failure matrix: защо наивният dual-write не е „zero data loss“

| Failure mode | Какво става | Необходима защита / проверка |
| --- | --- | --- |
| Commit в Oracle, PostgreSQL timeout/failure | Двете бази се разминават | Durable intent/retry + idempotent target writes + reconciliation |
| PostgreSQL commit, Oracle rollback | Новата база има несъществуващ в source запис | Ясно кой е source of truth, compensating logic / resync |
| Повторно събитие | Два импорта на една business операция | Stable dedup key + version и constraints |
| Historical backfill застъпва live updates | Стар snapshot може да презапише ново състояние | Snapshot boundary, version-aware merge, catch-up strategy |
| Numeric/time representation | Разлики в суми, precision, timezone, `NULL` семантика | Canonical comparison по полета, currency precision, договорени трансформации |
| Canary чете грешно | 1% клиенти виждат различна история | Shadow reads/diffs, error budgets, feature flag rollback |
| Трафик се прехвърля и към writes | Връщането назад може да загуби нови записи | Изрична стратегия за reverse sync или controlled cutover |

**Това са наши инженерни рискове/контроли, не твърдение, че банката използва конкретно outbox/queue/XA решение.** Наличието на `try { write(oracle); write(postgres); }` не означава distributed atomic commit. Дори с двупосочни retries и reconciliation „zero loss“ остава **цел, която трябва да се доказва**, а не гаранция от името на архитектурата.

**Историческо прехвърляне:** еднократно bulk extraction/import, последователен incremental catch-up и проверка; има различни инструменти и `COPY` пътища за PostgreSQL, но конкретният подход зависи от правата и мрежата. Не твърдим, че Debezium или други third-party миграционни инструменти са били използвани.

### Безопасен rollout договор

- Първо сравни схеми, ключове, финансови стойности, крайни състояния и клиентски справки; row count сам не стига.
- Включи read-only пилотен cohort; измервай **P95/P99, error rate, mismatch rate**, latency по размер на клиент.
- Разширявай при изпълнени критерии; спирай и връщай read router към Oracle при регресия.
- Съхранявай Oracle като надежден source of truth, докато write-cutover/rollback semantics не са решени.
- Имай репетиран rollback; **връщане на read path не е същото като възстановяване на двустранно синхронизирани writes**.

## 9. Alternatives & trade-offs — decision record

| Подход | Кога има смисъл | Защо не е достатъчен сам |
| --- | --- | --- |
| Повече CPU/RAM, по-големи кешове | Временен капацитетен буфер | Маскира read amplification и не гарантира cold-start SLO |
| Принудително cache warming | Предвидима, контролирана подготовка без contention | В инцидент с висок I/O може да влоши проблема; не лекува query logic |
| Увеличаване на timeout | Ако измереният customer impact намалява | Ресурсни лимити и латентност остават; временен mitigation |
| По-селективни SQL предикати / индекси / partitioning | Ясен бизнес прозорец и измерими access paths | Не решава постоянната read-time работа над historical events |
| Materialized / precomputed read model | Много четения на derived state | Refresh consistency, lag и operational сложност |
| Write-side integrity (избраната посока) | Контрол върху ingest-а и надеждна identity/version логика | По-сложни записи, race conditions, out-of-order events |
| CDC + snapshot/backfill | Разрешен log access и операционна поддръжка | Security/privileges, logs/retention, schema evolution, monitoring |
| App-level dual writes (планът) | Importers контролират всички писания, CDC не е приемлив | Няма автоматична атомарност между две бази |
| Big-bang database switch | Почти никога при този риск/обем | Трудно връщане, много неизследвани corner cases |

**Сравни с [Case 001 — Decathlon / Kafka CDC](001-decathlon-kafka-business-table-cdc.md):** там business-table CDC е избран за избягване на API-level DB+Kafka dual write, но въвежда WAL/replication-slot риск. Тук CDC е отхвърлен като оперативен вариант при специфични ограничения, а application-level dual write поражда **сходен риск от разминаване** между двете бази. Pattern-ът няма универсален победител.

## 10. Production recognition & runbook

### Сигнали

- Успешни HTTP 2xx, но рязко влошен **customer journey / business SLI**.
- Увеличена латентност след DB restart/failover/DR; подобрение при повторно четене на идентични данни.
- Разлика `P50` срещу `P99` и спрямо големината на акаунта.
- Увеличени physical reads, I/O wait, CPU/storage cell pressure, сортирания/joins върху огромни множества.
- Frontend timeouts, JDBC pool waiting/active, pending HTTP requests и retry rate.

### Стъпки при инцидент

1. **Дефинирай impact:** кой бизнес поток е прекъснат, какво точно не вижда клиентът, кой deadline го прекъсва; отдели `2xx` от функционален успех.
2. **Ограничи щетата:** сравни timeout mitigation, rate limiting, canary degradation/fallback; опиши пределите и ресурсните рискове.
3. **Корелирай промени:** последен DR/failover, patches, deployment, statistics/index changes, ръст в cardinality и обем.
4. **Изолирай SQL:** `SQL_ID`, план, actual rows, buffer gets, physical reads, elapsed time, ASH/AWR или налични telemetry; не променяй права/настройки за диагностика без одобрение.
5. **Провери хипотези:** cold vs warm, скъпи `JOIN/ROW_NUMBER/SORT`, липсващ времеви филтър, plan stability vs runtime variability.
6. **Избери най-безопасната промяна:** конфигурируемо ограничение само след бизнес проверка; rollback план и feature flag/observability.
7. **Проследи възстановяването:** error budget, latency по cohort, DB saturation; наблюдавай дали подобрението е устойчиво.
8. **Запиши RCA и превенция:** cold-cache test преди DR, query complexity regression tests, query-window contract tests, synthetic end-to-end SLI.

### Oracle 19c — примерна read-only диагностика на план

Използвай само ако имаш легитимен `SQL_ID`/`child_number`, необходимите права за performance views и runtime статистики (напр. събрани чрез `gather_plan_statistics` за тестовото изпълнение); **не** включвай глобално тежко проследяване на production само за този пример.

~~~sql
SELECT *
FROM TABLE(
    DBMS_XPLAN.DISPLAY_CURSOR(
        :sql_id,
        :child_number,
        'ALLSTATS LAST +PREDICATE +PARTITION'
    )
);
~~~

**Тълкуване:** търси `E-Rows` спрямо `A-Rows`, `Buffers`, actual time, access/filter predicates, избран `JOIN`, `PARTITION` и `Starts`. За I/O wait, cache/cell metrics и историческо изпълнение трябват допълнителни наблюдения; `DBMS_XPLAN` не показва всичко.

### Предварителна DR checklist

- [ ] Знаем критичните SQL и имаме latency/error SLO за **първи достъп след failover**.
- [ ] Направили сме безопасен тест за performance при cold/partially warmed caches в представителна среда.
- [ ] Проверили сме data distribution, големи бизнес акаунти, join cardinalities и филтри.
- [ ] Има guardrails за DB connection pools, deadlines, retry budgets и concurrency.
- [ ] Има наблюдаемост на кеш/I/O и процедури за деградация/rollback.
- [ ] Има бизнес валидиран максимален query window и тестове на edge cases.
- [ ] Няма обещание за „zero loss“ без reconciliation и доказуем cutover protocol.

## 11. Production traps и кога да използваме/не използваме идеите

**Когато работи подходът:** имаме повторяемо скъпо derived read състояние, контрол върху ingestion path, сигурна бизнес identity/version, възможност за постепенно внедряване и добре дефинирани SLA/consistency изисквания.

**Когато е опасно:** не можем да дефинираме кои събития са latest; данните имат множество несинхронизирани писатели; няма replay/reconciliation; финансови транзакции могат да пристигат извън времевия прозорец; migration rollback е само UI превключвател, без да отчита write divergence.

**Не прави неверни изводи от случая:** „Oracle се провали“, „индексите не работят при милиарди редове“, „всяко `ROW_NUMBER` е лошо“, „CDC винаги изисква SYSDBA“, „dual-write е атомарно“, „PostgreSQL автоматично ще е по-бърз“. Проблемът е взаимодействие между работа на query-то, мащаб, cache behavior и operational constraints; всяко твърдение изисква измерване.

## 12. Senior engineering challenge — 7 въпроса

*За самопроверка след прочитането. Първо аргументирай на глас; после отвори критериите.*

1. **Observability:** Всички API заявки връщат `200`, но клиентите твърдят, че „банката не работи“. Кои три слоя и кои конкретни business/technical SLI ще сравниш първи?
2. **Plan vs runtime:** Една заявка има идентичен plan hash преди и след DR, но латентността скача от милисекунди на минути. Изброй поне четири проверими хипотези и как ще ги различиш.
3. **Tactical fix:** Защо `WHERE event_time >= current_date - 10` може едновременно да подобри performance и да наруши correctness? Как валидираш прозореца?
4. **Architectural trade-off:** Имаме 20 млн. reads и 40 млн. writes на ден. При какви условия прехвърлянето на dedup на write side подобрява общото положение, въпреки че writes са повече?
5. **Concurrency/idempotency:** Два importers получават противоречиви версии на една транзакция в обратен ред. Как гарантираш, че booked не се превръща пак в pending?
6. **Dual-write failure:** Oracle е commit-нал, PostgreSQL е недостъпен. Опиши безопасен recovery, така че заявката да не се загуби и повторението да не удвои транзакцията.
7. **Migration/rollback:** На 5% canary откриваш коректност на балансите, но разминаване в няколко исторически транзакции. Как спираш rollout, сравняваш и вземаш решение за rollback или продължаване?

<details>
<summary>Критерии за силен отговор (без единствена „магическа“ имплементация)</summary>

1. Business synthetic journey и client-visible outcome; gateway/client timeout; backend traces/pool metrics; SQL/cell I/O.
2. Cache residency/cold starts, I/O waits/storage, bind/data distribution и cardinalities, contention, parallelism, statistics/plan child selection; корелирани метрики и контролирани тестове.
3. Event time ≠ business state time, holidays/exceptional settlement, retained pending, late-arriving corrections; business contract и негативни тестове.
4. Cost × frequency, различни latency SLO, bounded incremental updates, concurrency cost и consistency; доказателство с benchmark.
5. Stable identity/version, atomic conditional upsert/compare-and-set, constraints, state machine/invariants, replay and alerting.
6. Source of truth, durable change log/outbox или recoverable import journal, idempotent replay, bounded retries, reconciliation и ясно разграничен acknowledgement contract.
7. Freeze canary/flag rollback, diff по business keys и money/time semantics, граница backfill-vs-live, repair plan, explicit критерии за възстановяване.

</details>

## 13. Идеи за възпроизвеждане (не са реализирани тестове)

1. **SQL experiment:** синтетични pending event revisions + booked записи; две версии — bounded candidates и unbounded; сравни `EXPLAIN (ANALYZE, BUFFERS)` за PostgreSQL и `DBMS_XPLAN` за Oracle *при налична тестова среда*, измери estimated/actual rows, физическо I/O, correctness.
2. **State projection:** със Spring Boot и Testcontainers симулирай importer, replay, out-of-order events и паралелни записи; покажи corrupted read model и поправка със стабилни версии и constraints.
3. **Dual-write chaos:** fail PostgreSQL след успешен Oracle commit; реализирай **надеждно** записан retry intent и reconciliation, демонстрирай recovery без duplicate side effects.
4. **Canary/read compare:** router с cohorts + shadow query към двете бази; сравнявай canonical money/date representations, тествай mismatch-triggered rollback.
5. **Cold path performance:** benchmark първо vs повторно четене в контролирана среда и събирай I/O наблюдения; не прави disruptive cache flush върху shared DB.

**Важна граница:** тези лаборатории са предложения. Няма claim за съществуващ код/тест в този case study; линковете към съществуващите модули са само за фундаментите.

## 14. Връзки към нашия учебен план

- **P0 SQL/Oracle/PostgreSQL:** `DB-05` window functions, `DB-06` indexes/selectivity, `DB-07` execution plans/actual rows, `DB-16` Oracle/PostgreSQL differences — [roadmap](../learning/ROADMAP.md).
- **P0 Reliability:** `REL-01` timeouts/retries, `REL-02` idempotency, `REL-05` observability.
- **P1 System Design & Performance:** migration trade-offs, read/write models, scaling and operational incident handling.
- **Налични лаборатории:** [CQRS](../../spring/architecture/cqrs/README.md), [Fault Tolerance](../../spring/reliability/fault-tolerance/README.md), [Idempotency](../../spring/reliability/idempotency/README.md), [Outbox/Inbox](../../spring/messaging/outbox-inbox/README.md), [QueryDSL](../../persistence/specification-querydsl/README.md).
- **За NotebookLM:** постави **оригиналното видео** в **Tech Radar → P1** като case study source. Видеото вече е изслушано; не е необходимо второ аудио по същото. Дългосрочната ни справка е този документ.

## 15. Оригинални и официални източници

1. [Martin Ladecký / Devoxx Belgium 2026 — YouTube лекция](https://www.youtube.com/watch?v=TB45qqp-OxM) — **първичен източник за incident story, решенията и докладваните числа**.
2. [Devoxx Belgium 2026 — описание, дата и лектор](https://m.devoxx.com/events/dvbe26/talks/16408/10-billion-records-vs-the-regulator-an-architectural-post-mortem-of-an-11tb-exadata-failure) — официална програма.
3. [Oracle — Administering Exadata Smart Flash Cache](https://docs.oracle.com/en/engineered-systems/exadata-database-machine/sagug/administering-smart-flash-cache.html) — кеширане и storage tiers.
4. [Oracle — In-Memory Columnar Caching](https://docs.oracle.com/en/engineered-systems/exadata-database-machine/sagug/enabling-or-disabling-memory-columnar-caching-storage-servers.html) — предпоставки/лиценз за columnar cache.
5. [Oracle Database 19c — DBMS_XPLAN](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_XPLAN.html) — действителни execution statistics и формати.
6. [Debezium — Oracle connector](https://debezium.io/documentation/reference/stable/connectors/oracle.html) — CDC adapters, privileges, operational prerequisites.
7. [PostgreSQL — Logical Replication](https://www.postgresql.org/docs/current/logical-replication.html) — полезен контекст за snapshots/changes; **не означава, че PostgreSQL logical replication е предложената миграция от Oracle**.
8. [Case Study 001 — Decathlon](001-decathlon-kafka-business-table-cdc.md) — контрастен CDC избор и operational рискове.

**Разграничение на достоверността:** хронологията и решенията на банката са предадени по лекцията; илюстративните SQL/Mermaid схеми, runbook, failure matrix и упражнения са **наш учебен анализ**, не вътрешни production артефакти на Commerzbank. Не е публично доказан всеки детайл на root cause и бъдещата миграция не е отчетена като завършена.
