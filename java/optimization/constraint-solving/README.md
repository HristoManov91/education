# P1 — Constraint Optimization с Timefold Solver (Java 25)

> **Основен източник:** [Tom Cools и Geoffrey De Smet — „You May Now Optimize the Wedding“](https://www.youtube.com/watch?v=lsvVJaigNp8), Devoxx Belgium 2026, предоставен автоматичен SRT (около 37 мин). Материалът е преработен за Java/Spring backend задачи. Примерите с назначаване на служители и нашите тестове **не са кодът от сватбената демонстрация**.  
> **Runtime:** Java 25 LTS, Timefold Solver **2.8.0** (Community Edition), Maven, JUnit. **Един** Maven модул, без база, Docker, реални служители/клиенти, външен optimization service или платени API.

## 1. Реален казус — кой служител, коя задача и кога?

Backend получава задачи за проверки и поддръжка. Всяка има необходима квалификация, допустими времеви слотове, приоритет и местоположение. Имаме служители с квалификации, наличност, предпочитания и начална зона. При всяка нова задача трябва да построим план:

- Никой да не бъде назначен два пъти в един слот;
- Всички назначения да са по квалификация и време;
- Когато ресурсите не стигат, някои задачи може да останат **неразпределени**, но не без цена;
- Сред допустимите решения да предпочетем по-близък служител и подходящ работен час.

**Наивно решение:** `Collections.sort(tasks)` по приоритет и `findFirst` служител. За една задача е удобно; при десетки взаимозависими задачи изборът за първата може да направи следващите невъзможни. Brute force расте комбинаторно (при точно N гости и N места, N! разпределения).

**По-добър подход:** изрично описваме модели и ограниченията, solver търси най-добрия намерен план в зададеното време; **не обещава глобалния оптимум**. Използваме 1:1 Timefold building blocks от лекцията, но ги прилагаме за backend назначения.

### Трите сватбени демонстрации и как ги използваме

| От предоставения SRT | Принцип | Наша адаптация |
| --- | --- | --- |
| ~06–24 мин. Seating: гости ↔ места, фиксирани места, accessibility, конфликти; `@PlanningEntity`, `@PlanningVariable`, hard/soft | Едно назначение и конкуриращи се ограничения | **P1 runnable Timefold** — `WorkTask` ↔ `AssignmentOption`, hard/medium/soft |
| ~25–30 мин. Honeymoon: избиране и подреждане на посещения и хотели, `@PlanningListVariable(allowsUnassignedValues=true)` | Ред, време, пътуване, optional visits | **P2 reference search**, малък изчерпателен алгоритъм с time windows — [Routing & Replanning](ROUTING-AND-REPLANNING.md). Истински list-variable solver **още не е runnable**. |
| ~30–35 мин. Вечер/бюджет, избор на варианти, нов план при изчерпана опция | Промени в problem facts и повторна оптимизация | **P2 batch replanning с `@PlanningPin`**, отказ при изчезнала pinned стойност; live `ProblemChange` остава следваща стъпка |

**Източник срещу адаптация:** лекторите използват Timefold, frontend/REST демо и техни domain classes. Нашата демонстрация не приписва на тях нито конкретните бизнес правила, нито fixture числата, тестовете или performance резултати.

## 2. Ментален модел: problem facts → variables → constraints → score → solve

```text
Input snapshot
  ├─ Employee (immutable fact): skills, availability, home zone, preferred slots
  ├─ TimeSlot (immutable fact): non-overlapping time grid
  ├─ AssignmentOption (immutable value): employee + slot
  └─ WorkTask (@PlanningEntity): fixed requirements, mutable @PlanningVariable
                                        |
                                     Timefold
                                        |
            @PlanningVariable(allowsUnassigned = true)
                        Task -> AssignmentOption or null
                                        |
                                ConstraintProvider
               HARD ───── MEDIUM ───────── SOFT
        cannot violate    omission cost    preferences
                                        |
                            TaskSchedule (@PlanningSolution)
                assignments + HardMediumSoftScore + diagnostics
```

Избрахме **една composite planning value** `AssignmentOption(employee, timeSlot)`, вместо две независими variables. Така не създаваме полусъстояние „служител назначен, но слотът е null“. Това увеличава броя на възможните стойности (служители × слотове) и е trade-off — при големи набори може да изисква different model/value range filtering.

Timefold **променя само** анотираната planning variable. Правилата, task ID, skill и location не се изменят от solver. `@PlanningId` остава константен, а `WorkTask` не дефинира `equals/hashCode` през mutable assignment. `Employee`/slot/options са immutable records.

### Hard / Medium / Soft — точен priority contract

| Ниво | Пример | Тегло |
| --- | --- | --- |
| Hard | Липсваща квалификация, служител извън наличността, задача извън прозореца, два task-а на един служител/слот | `HardMediumSoftScore.ONE_HARD` |
| Medium | Неизпълнена задача: `@PlanningVariable(allowsUnassigned=true)` и **`forEachIncludingUnassigned`**; наказание, равно на priority | `ONE_MEDIUM` |
| Soft | Наказание за разстояние от home zone и непредпочитан слот | `ONE_SOFT` |

`0hard/-10medium/-2000soft` е **по-добра** оценка от `-1hard/0medium/0soft`. Нито една soft/medium полза не компенсира hard нарушение. `0hard/-5medium` значи **допустим, но непълен** план — frontend трябва да показва и неразпределените задачи; `0hard/0medium` означава няма omissions.

В Timefold 2.8 score типовете са в `ai.timefold.solver.core.api.score` и нивата се четат с `.hardScore()`/`.mediumScore()`, не старите 1.x getters. `Score.isSolutionInitialized()` е премахнато; след `Solver.solve` изискваме non-null computed score и hard=0. Виж [официалния v1→v2 migration guide](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-from-v1).

## 3. README → код → доказателство

| Концепция / защо | Изпълним код | Реални tests |
| --- | --- | --- |
| Mutable planning entity, pinned status, nullable assignment | [WorkTask](src/main/java/bg/hristomanov/education/optimization/model/WorkTask.java) | [TaskReplanningTest](src/test/java/bg/hristomanov/education/optimization/solver/TaskReplanningTest.java) |
| Immutable business facts и затворени слот диапазони | [Employee](src/main/java/bg/hristomanov/education/optimization/model/Employee.java), [TimeSlot](src/main/java/bg/hristomanov/education/optimization/model/TimeSlot.java), [AssignmentOption](src/main/java/bg/hristomanov/education/optimization/model/AssignmentOption.java) | Test fixtures в [TaskConstraintProviderTest](src/test/java/bg/hristomanov/education/optimization/solver/TaskConstraintProviderTest.java) |
| `@PlanningSolution`, entity/fact collections и score | [TaskSchedule](src/main/java/bg/hristomanov/education/optimization/model/TaskSchedule.java) | [TaskScheduleSolverTest](src/test/java/bg/hristomanov/education/optimization/solver/TaskScheduleSolverTest.java) |
| Incremental hard/medium/soft constraints | [TaskConstraintProvider](src/main/java/bg/hristomanov/education/optimization/solver/TaskConstraintProvider.java) | [ConstraintVerifier](src/test/java/bg/hristomanov/education/optimization/solver/TaskConstraintProviderTest.java) — тест за всяко правило |
| Не вярваме на oracle, написан от самия оптимизатор | [IndependentScoreOracle](src/main/java/bg/hristomanov/education/optimization/solver/IndependentScoreOracle.java) — отделни прости цикли | [9 exhaustive states](src/test/java/bg/hristomanov/education/optimization/solver/IndependentScoreOracleTest.java) + пълната score проверка |
| Bounded solve и проверка преди publication | [TaskScheduleSolver](src/main/java/bg/hristomanov/education/optimization/solver/TaskScheduleSolver.java) | [Solver integration](src/test/java/bg/hristomanov/education/optimization/solver/TaskScheduleSolverTest.java) |
| Повторна оптимизация с непроменени committed assignments | [TaskReplanning](src/main/java/bg/hristomanov/education/optimization/solver/TaskReplanning.java) | [Pinned batch replanning](src/test/java/bg/hristomanov/education/optimization/solver/TaskReplanningTest.java) |
| Минимална демонстрация без Spring | [ScheduleDemoData](src/main/java/bg/hristomanov/education/optimization/solver/ScheduleDemoData.java), [ScheduleDemoMain](src/main/java/bg/hristomanov/education/optimization/solver/ScheduleDemoMain.java) | CI CLI smoke + score/oracle check |

## 4. Стартиране и упражнения

От корена на `education`:

```bash
mvn -pl java/optimization/constraint-solving -am verify

# CLI с всички dependencies чрез Maven exec plugin (от module directory):
cd java/optimization/constraint-solving
mvn -DskipTests exec:java \
  -Dexec.mainClass=bg.hristomanov.education.optimization.solver.ScheduleDemoMain
```

CLI трябва да покаже `SCORE=...`, `FEASIBLE=true`, присвояванията и `ORACLE_MATCHES=true`. Конкретните soft scores/назначения **не са обещани**: solver използва search и runtime budget, не exact optimizer с guarantee за тази стойност. Maven профилът е Java25; изолираният Timefold вариант не изисква Spring context. Поддържа се с Timefold 2.8.0 OSS, не Enterprise API.

### Experiment A — „забрави“ едно задължително правило

Премахни `employeeCollision` от `defineConstraints`: тогава две задачи може да получат един и същ служител и слот. [`TaskConstraintProviderTest.sameEmployeeAndSlotConflicts...`](src/test/java/bg/hristomanov/education/optimization/solver/TaskConstraintProviderTest.java) защитава правилото. `ConstraintVerifier` тества именно constraint-а, **не** доказва global optimum. Внимавай: ако тестваш само един constraint метод, махането му от `defineConstraints` може да не се улови — затова има и **full score parity** спрямо independent oracle.

### Experiment B — грешен unassigned handling

Смени `forEachIncludingUnassigned(WorkTask.class)` с `forEach` при `unassignedPriority`. Тогава никоя задача без planning value няма да генерира penalty; all-unassigned решение може да изглежда отлично. Отрицателният JUnit тест изисква `penalizesBy(9)` при priority 9.

### Experiment C — отделна, независима оценка

Използвай 2 задачи и 2 допустими options плюс `null`: общо 9 assignment състояния. [`IndependentScoreOracleTest`](src/test/java/bg/hristomanov/education/optimization/solver/IndependentScoreOracleTest.java) обхожда всяко състояние и проверява пълната Timefold оценка срещу independent nested-loop calculation. Допълнителен тест обхожда 4 състояния на невъзможна задача (две задачи, един служител/слот): най-добрият **feasible** score оставя задача с по-малък приоритет unassigned.

### Experiment D — невъзможен пълен план

При една свободна слот/служител опция и две задачи, фиксираме първата. Solver трябва да запази committed assignment, да избягва hard collision и да отбележи другата като **UNASSIGNED** с medium penalty. Това е честен резултат за over-constrained planning, не solver bug. Публикуването на план трябва да показва едновременно feasible hard status и incomplete tasks.

## 5. Сравнение на подходите / decision contract

| Подход | Кога | Кога НЕ | Печелим | Губим | По-проста алтернатива | Proof |
| --- | --- | --- | --- | --- | --- | --- |
| Sort / greedy | малко независими задачи; няма cross-task hard constraints | сложни взаимозависимости | простота, предвидима цена | локалните избори може да блокират глобален план | обикновена Java Stream/sort | собствен инвариантен JUnit |
| Backtracking / brute force | 2–8 задачи, независим oracle, учебна проверка | 50+ задачи с много options | доказваме малък оптимум | експоненциална цена | Timefold или MILP/CP solver | [exhaustive test](src/test/java/bg/hristomanov/education/optimization/solver/IndependentScoreOracleTest.java) |
| Timefold `@PlanningVariable` | много комбинации и score-based choices | проста сортиране/SQL query, latency-critical 5ms HTTP request | incremental scoring и extensible constraints | dependency, search tuning, CPU use, quality not guaranteed | rule-based greedy или подходящ готов planning API | [runnable tests](src/test/java/bg/hristomanov/education/optimization/solver/TaskScheduleSolverTest.java) |
| `allowsUnassigned` + medium penalty | ресурсите не достигат, искаме частично изпълнение | всяка задача е абсолютно задължителна и липсата е недопустима | без hard violations, ясна пропусната работа | непълен план/нужна реакция от оператор | reject whole request и manual escalation | [constraint tests](src/test/java/bg/hristomanov/education/optimization/solver/TaskConstraintProviderTest.java) |

### Операционни граници

1. **Никакви SQL/HTTP calls в `filter`/join/score.** Подготви DTO/facts предварително. `Constraint Streams` са incremental, но map lookups и helper calls пак струват CPU.
2. **`Duration` е time budget, не гаранция за optimum или дори feasibility.** Съхранявай най-добрия намерен план и проверявай hard score преди публикация.
3. **Fixed time limit ≠ deterministic exact output.** За сравняеми benchmark условия контролирай seed, solver version, JDK, CPU, input ordering и termination policy.
4. **Няма timezone/overlapping slots в този опростен модел.** Слотовете са предварително зададени и не се припокриват. За произволни интервали е необходим реален interval-overlap predicate и tests.
5. **`zone` е синтетична distance proxy**, не пътна мрежа, геолокация или истинска цена на пътуване.
6. **Community vs Enterprise:** този модул използва OSS core и ConstraintVerifier. Не използва платен Score Analysis / оптимизационни API.
7. **Не блокирай HTTP request със `Solver.solve()`**. За реална Spring Boot услуга виж [Async integration decision note](ASYNC-SPRING-INTEGRATION.md) и [официалния `SolverManager`](https://docs.timefold.ai/timefold-solver/latest/running-timefold-solver/library/library-integration).

## 6. Изход — какво решихме, какво остава

**Преди:** наивният greedy разбираше само „вземи най-високия приоритет“ и можеше да наруши skill/availability или да остави задачите без обяснение. **Сега:** имаме explicit hard/medium/soft договор, real Timefold solver, отрицателни тестове, independent reference и фиксирано replanning.

**Не сме реализирали:** production REST API, истинска оптимизация на времеви интервали/гео маршрути, distributed SolverManager, live `ProblemChange`, платен explainability или benchmark, показващ 2× ускорение. P2 [Routing & Replanning](ROUTING-AND-REPLANNING.md) е малък референтен алгоритъм + runnable batch replan, ясно разграничен от `@PlanningListVariable`.

**Senior review checklist:** [ ] кои са hard/medium/soft; [ ] non-assigned наказани ли са; [ ] immutable facts ли използваме; [ ] `@PlanningId` стабилен ли е; [ ] no DB in constraints; [ ] independent score oracle; [ ] hard score проверен ли е; [ ] ресурси/time budget; [ ] pinned commitments; [ ] cancel/error path в реален HTTP service; [ ] няма claims за global optimality?

## Оригинални източници

- [Tom Cools & Geoffrey De Smet, Devoxx Belgium 2026 — wedding optimization](https://www.youtube.com/watch?v=lsvVJaigNp8) — потребителски автоматичен SRT, проверени timestamps и описания (seating/honeymoon/bachelor-party).
- [Timefold 2.8.0 release](https://github.com/TimefoldAI/timefold-solver/releases/tag/v2.8.0), [Apache 2.0 community repository](https://github.com/TimefoldAI/timefold-solver).
- [Timefold 2.8 Quickstart, standalone Java](https://docs.timefold.ai/timefold-solver/latest/quickstart/hello-world/hello-world-quickstart).
- [Modeling planning problems, planning pins/list variables](https://docs.timefold.ai/timefold-solver/latest/domain-modeling/modeling-planning-problems).
- [Constraint Streams and ConstraintVerifier](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/score-calculation).
- [Overconstrained planning, unassigned penalty](https://docs.timefold.ai/timefold-solver/latest/domain-modeling/common-patterns).
- [SolverManager and async service](https://docs.timefold.ai/timefold-solver/latest/running-timefold-solver/library/library-integration).
- [Timefold 1.x → 2.x migration](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-from-v1).
