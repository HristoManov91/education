# P2 — Routing с time windows и Replanning при променени условия

> Допълнение към [Constraint Optimization P1](README.md) по [„You May Now Optimize the Wedding“, ~25–35 мин.](https://www.youtube.com/watch?v=lsvVJaigNp8?t=1500). **Това НЕ е production-ready VRP**, не е геолокационен двигател и не използва реален `@PlanningListVariable` implementation. Двата executable експеримента са: ограничен reference route search и batch replanning с Timefold.

## 1. Реален казус: част от посещенията са задължителни

Екипът има свободен ден между 09:00–15:00, трябва да посети конкретен клиент и би искал да добави още посещения. Всяко посещение има времеви прозорец, продължителност, зона, приоритет и `required` статус.

Маршрутът е **подреден списък**, не независимо `task → slot` присвояване. Последователността влияе на travel time, възможността да се спази opening window и броя обслужени клиенти. `Sort.by(priority)` и `nearest first` могат да пропуснат по-добър общ ред.

**В лекцията:** `@PlanningListVariable(allowsUnassignedValues=true)` дава на Timefold възможност да избере и подреди само част от visits. Демо-то е пътешествие с хотели/дейности. **Нашият P2 executable lab засега е независим exhaustive reference**, не Timefold list-variable solver.

## 2. Проста проверима лаборатория

[`TripReferenceSolver.java`](src/main/java/bg/hristomanov/education/optimization/routing/TripReferenceSolver.java) моделира:
- `Visit(id, zone, duration, opensAt, closesAt, desirability, required)`;
- start zone + start/end minute;
- synthetic travel proxy `10 * abs(zoneA-zoneB)`, wait until opening;
- hard: required visits трябва да са включени, time windows и крайният час не могат да се нарушават;
- soft objective: `sum(desirability) * 100 - totalTravelMinutes`;
- **ограничение до 8 посещения** → изчерпателно търсене на подредени подмножества, използвано **само за correctness oracle**. Ако няма допустим маршрут с всички required visits → `Optional.empty()`.

```bash
mvn -pl java/optimization/constraint-solving -Dtest=TripReferenceSolverTest test
```

[`TripReferenceSolverTest.java`](src/test/java/bg/hristomanov/education/optimization/routing/TripReferenceSolverTest.java) пази: задължителни посещения, незадължителни дейности, които не се побират, отворено/затворено време, travel-vs-value решение, duplicate IDs и invalid inputs.

### Кога list variable става оправдан

При десетки/стотици посещения, превозни средства, капацитет, реални time windows и dynamic facts. Моделът в Timefold 2.x използва `@PlanningEntity` на route/vehicle, `@PlanningListVariable` за поръчан списък и shadow variables за arrival time/previous location. Изисква надеждно изчисление на duration и score, optional unassigned values да бъдат **изрично наказани**, достатъчен CPU/memory budget и JUnit със сравним tiny oracle.

**Не е реализация в този PR:** Timefold list-variable optimization, route shadow variables, пълно VRP решение, Google Maps/matrix API или данни от GPS. Не ги обозначаваме като тествани.

## 3. Batch replanning: pinning на вече поети назначения

В production част от работата е започнала. След промяна на наличност или добавяне на заявка solver-ът трябва да пренареди **само останалите задачи**, без да мести вече потвърдена проверка.

[`TaskReplanning.java`](src/main/java/bg/hristomanov/education/optimization/solver/TaskReplanning.java) създава **нова** `TaskSchedule` snapshot, копира tasks, фиксира committed IDs с `@PlanningPin` и подава новата версия към [TaskScheduleSolver](src/main/java/bg/hristomanov/education/optimization/solver/TaskScheduleSolver.java). Ако pinned assignment липсва от актуалните options, връща validation error вместо скрито да премести изпълняваната задача.

```java
TaskSchedule oldPlan = ScheduleDemoData.create();
WorkTask first = oldPlan.getTasks().getFirst();
first.setAssignment(oldPlan.getOptions().getFirst());

TaskSchedule next = TaskReplanning.newSnapshot(
        oldPlan, oldPlan.getOptions(), List.of(
                new WorkTask("ADDED", "INSPECTION", Set.of("MORNING"), 3, 4)),
        Set.of(first.getId()));
TaskSchedule result = new TaskScheduleSolver(Duration.ofMillis(500)).solve(next);
```

[`TaskReplanningTest.java`](src/test/java/bg/hristomanov/education/optimization/solver/TaskReplanningTest.java) доказва pinned assignment, snapshot isolation, нова задача, unknown/duplicate ID и fail-closed при изчезнал pinned option. Това е **повторно batch solve**, не live `ProblemChange` докато solver работи.

## 4. Decision contract: кога кой алгоритъм

| Подход | Кога | Кога НЕ | Печалба | Цена/риск | По-проста алтернатива |
| --- | --- | --- | --- | --- | --- |
| Exhaustive reference | <=8 посещения, JUnit/oracle, обяснение на combinatorial space | production със стотици посещения | доказва малък exact optimum | факториален runtime | bounded greedy |
| `@PlanningListVariable` (планирано P2) | ordered visits, capacity, time windows, many locations | independent appointments без ред | може да пренарежда itinerary | сложни shadow variables/score, CPU/latency | basic single-assignment model |
| Batch replanning + `@PlanningPin` | periodic revisions, вече committed задачи | sub-second continuously arriving events | snapshot isolation, прост test/rollback | re-solve CPU/time and delayed response | new greedy assignment |
| Live `ProblemChange` | continuous updates по време на решаване | кратък offline batch | обработка на събития без restart | thread safety + change director/observability | batch snapshot |

**Trade-off с реалния backend:** `Solver.solve()` блокира текущата нишка, затова не го изпълнявай директно в HTTP controller. Използвай `SolverManager`, job ID, persisted job lifecycle и policy за cancel/timeout/status. [P2 asynchronous integration note](ASYNC-SPRING-INTEGRATION.md) показва тази граница. Не модифицирай solver working facts от други нишки — за live updates се използва `ProblemChange`.

## 5. Review / finish

Проверяваме дали необходимият visit е включен, не само „най-красивия маршрут“. Unassigned optional values получават реална цена/полза. Pinned assignments не се преместят silently. За production изискваме verified travel matrix, user-acknowledged omissions, и реални SLA/CPU/GC измервания.

**Източници:** [лекцията](https://www.youtube.com/watch?v=lsvVJaigNp8) · [Planning list variables](https://docs.timefold.ai/timefold-solver/latest/domain-modeling/modeling-planning-problems) · [Repeated planning and ProblemChange](https://docs.timefold.ai/timefold-solver/latest/responding-to-change/responding-to-change).
