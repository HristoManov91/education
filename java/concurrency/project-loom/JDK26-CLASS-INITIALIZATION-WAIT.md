# P2 — JDK 26: Virtual Threads waiting for class initialization

> Източник: [Per-Åke Minborg — Yearly Java Performance Update 2026](https://www.youtube.com/watch?v=rNOzf12RORI), около 29–31 мин., предоставен SRT. Семантичните подробности са потвърдени в [Oracle JDK 26 release notes](https://www.oracle.com/java/technologies/javase/26-relnote-issues.html) — JDK-8369238. Допълнение към [Project Loom](README.md) и [JAVA-26.md](JAVA-26.md), не нов Thread API.

## 1. Реален казус: защо много Virtual Threads могат да задържат carrier threads

Първият thread, който използва даден Java class, може да изпълнява тежък static initializer. Ако други virtual threads (виртуални нишки) се опитат да използват същия class, те трябва да изчакат инициализацията да приключи.

**Преди JDK26:** в някои общи class-init wait paths виртуалните нишки се pin-ват (задържат platform carrier) докато чакат. При малък scheduler parallelism това може да изчерпи carriers, макар задачите да не вършат CPU работа. Краен случай: initializer чака virtual task, който няма свободен carrier → starvation или deadlock.

**От JDK26 (JDK-8369238):** в **повечето общи случаи** виртуална нишка, която чака друг thread да инициализира class, може да се **unmount-не** (освободи carrier). Това НЕ означава, че всички native/FFM/monitor pinning scenarios са премахнати.

## 2. Код → реално поведение → доказателство

| Концепция | Java25 runnable | Проверка |
| --- | --- | --- |
| Една class initialization, платформа thread собственик | [ClassInitializationWaitLab.java](loom-labs/src/main/java/bg/hristomanov/education/loom/labs/classinit/ClassInitializationWaitLab.java) | [ClassInitializationWaitLabTest.java](loom-labs/src/test/java/bg/hristomanov/education/loom/labs/classinit/ClassInitializationWaitLabTest.java) |
| Множество virtual tasks чакат `SlowClass.VALUE` | същият код (static `VALUE=initialize()` не е compile-time constant) | JUnit проверява published value 42, count 1 и всички waiters |
| Разлика 25/26+ при carrier behavior | child JVM с ограничен scheduler parallelism | JFR/events/thread dump, **manual наблюдение**; correctness test сам по себе си не доказва unmount |

```bash
mvn -pl java/concurrency/project-loom/loom-labs -am test

# От repository root, в два отделни JVM процеса (class init е веднъж за JVM):
"$JDK25_HOME/bin/java" -Djdk.virtualThreadScheduler.parallelism=1 \
  -cp java/concurrency/project-loom/loom-labs/target/classes \
  bg.hristomanov.education.loom.labs.classinit.ClassInitializationWaitLab 8

"$JDK27_HOME/bin/java" -Djdk.virtualThreadScheduler.parallelism=1 \
  -cp java/concurrency/project-loom/loom-labs/target/classes \
  bg.hristomanov.education.loom.labs.classinit.ClassInitializationWaitLab 8
# И при двете: INITIAL_VALUE=42 WAITERS=8 SUM=336 INIT_COUNT=1
```

**Стабилност на експеримента:** initializer е platform thread и се release-ва от външната нишка с latch. Така не създаваме умишлено deadlock в JDK25. Чакането е ограничено с timeouts и `finally` cleanup. JUnit доказва публикуването, а не брой carrier threads.

За диагностика пусни по желание `-XX:StartFlightRecording:filename=class-init.jfr,dumponexit=true` и после `jfr print --events jdk.VirtualThreadPinned class-init.jfr`. Събитието зависи от threshold/пътя на изпълнение; **липса на event не е доказателство, че никога няма pinning**. Прегледай и [JFR troubleshooting](../../jvm/jfr-troubleshooting/README.md).

## 3. Decision contract и ограничения

| Кога | Кога НЕ | Печалба | Загуба / риск | По-проста алтернатива | Доказателство |
| --- | --- | --- | --- | --- | --- |
| Diagnose first-request stalls или carrier starvation при lazy class init и много virtual tasks | при произволен I/O bottleneck, JDBC pool exhaustion или когато няма class-init contention | JDK26+ освобождава carriers при чести wait paths, без app code change | JDK upgrade regression, остава native pinning, труден за възпроизвеждане тайминг | предварително `Class.forName`/safe eager warmup за конкретни класове, без да товариш application startup излишно | [JUnit](loom-labs/src/test/java/bg/hristomanov/education/loom/labs/classinit/ClassInitializationWaitLabTest.java) + JFR + production metrics |

**Bad:** да добавим `synchronized` около цял request или да увеличим scheduler parallelism без profiling — маскира, а не решава причината. **Good:** проверка на class-init hotspots и разлика по JDK с JFR; не приемаме, че Virtual Threads могат да заобиколят resource limits.

## 4. Изход и code review

**Преди:** „virtual thread waiting не заема carrier“ — невярно за някои class-init paths в JDK25. **След:** знаем точната граница на JDK26 подобрението и можем да пуснем реални Java25/27 процеси. **Не доказваме** автоматично throughput speedup или липса на други pinning случаи.

Checklist: [ ] class initialized в кой thread; [ ] wait за чужд class initializer ли е; [ ] JDK версия; [ ] carrier parallelism; [ ] JFR и timeouts; [ ] safe warmup/rollback; [ ] DB/HTTP resource cap?

## Оригинални източници

- [Per-Åke Minborg, Devoxx 2026](https://www.youtube.com/watch?v=rNOzf12RORI) — предоставен SRT; не смесваме лекциите.
- [Oracle JDK 26 Release Notes: JDK-8369238](https://www.oracle.com/java/technologies/javase/26-relnote-issues.html).
- [JDK-8369238 — Allow virtual thread preemption on class initialization](https://bugs.openjdk.org/browse/JDK-8369238).
- [Java 26 Virtual Threads official guide](https://docs.oracle.com/en/java/javase/26/core/virtual-threads.html).
