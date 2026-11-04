# P1 · Java 25 JVM in Containers — Heap, Native Memory, OOMKilled, jlink и AOT

> **Основен източник:** [Albert Attard — Production Ready Java Containers: Smaller, Faster, Smarter](https://www.youtube.com/watch?v=LPhGSgO5eKE), Devoxx Belgium 2026, 00:00–48:20, предоставен SRT. Допълва [Project Leyden/Compact Object Headers](README.md) и [Benchmark Correctness](BENCHMARK-CORRECTNESS.md), **не ги дублира**. Изпълнимият container workload е [Kubernetes Java Lab](../../../infrastructure/kubernetes/README.md). JDK25 LTS baseline.

## 1. Реалният казус — Pod е `OOMKilled`, но имаме "свободен heap"

Java приложение в 512Mi container има Heap ~300Mi, а процесът консумира heap + Metaspace + direct/NIO buffers + JIT code cache + native libs + JVM/runtime + thread stacks. Когато cgroup memory limit бъде надвишен, Linux може да убие процеса (`OOMKilled`), без изобщо JVM да е успяла да хвърли `java.lang.OutOfMemoryError`.

```text
K8s limit: 512 MiB (cgroup)
  ├── Java heap (MaxRAMPercentage от допустимата JVM memory)
  ├── Metaspace / code cache / thread stacks
  ├── Direct buffers / native memory / GC overhead
  └── other process memory / shared pages / mapping

Heap OOME != container OOMKilled != CPU throttling
```

**Не** взимай числото 75% от лекцията като универсално препоръчително. Оптималният процент зависи от native headroom, използваните libraries, platform threads vs virtual threads, GC, heap live set, RSS peak и SLO. В нашата [ConfigMap](../../../infrastructure/kubernetes/manifests/10-configmap.yaml) демонстрационната стойност е 60% за limit 512Mi; това е exercise, **не финално production sizing**.

## 2. Различни environment variables — не ги бъркай

| Механизъм | За какво служи | Капан |
| --- | --- | --- |
| Командни JVM options преди `-jar` | `-Xmx`, GC, JFR, system properties | Ако hardcode-неш във image ENTRYPOINT, всяка среда има един default |
| `JDK_JAVA_OPTIONS` | Java launcher обработва JVM/launcher options преди explicit CLI; полезно за tuning на `java -jar` процес | Не е универсално за embedded JVM; някои launcher-only options не се приемат |
| `JAVA_TOOL_OPTIONS` | JVM initialization / agent и diagnostic options, използва се и когато Java launcher не е директният вход | Поддържаните options/precedence са различни; не дублирай със същия флаг без тест |
| `_JAVA_OPTIONS` | Vendor/VM-specific behavior, съществува в някои HotSpot distributions | Не е общо публично обещание като `JDK_JAVA_OPTIONS`; не го използвай без нужда |
| `JAVA_OPTS` | Convention на shell/entrypoint scripts | **Java binary НЕ го прочита автоматично** — трябва wrapper да го използва |

Пример за runtime override в Kubernetes ConfigMap:

```yaml
data:
  JDK_JAVA_OPTIONS: "-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
```

Нито една от тези променливи **не променя Xmx в вече стартирал JVM процес**. Измененията се прилагат при нов JVM startup. ConfigMap update, особено при environment variables, не променя автоматично вече стартирали Pod-ове; нужен е контролирано планиран rollout.

## 3. Reproducible diagnostic lab — реален container budget

След `docker build -t education-kubernetes-lab:dev infrastructure/kubernetes`:

```bash
# JVM вижда memory/cpu limits и избрания GC (изходът зависи от версия, архитектура и cgroup):
docker run --rm --memory=512m --cpus=1 --entrypoint java \
  education-kubernetes-lab:dev -XshowSettings:system -version

docker run --rm --memory=512m --cpus=1 --entrypoint java \
  education-kubernetes-lab:dev -XX:+PrintFlagsFinal -version 2>&1 \
  | grep -E 'MaxRAMPercentage|MaxHeapSize|UseSerialGC|UseG1GC|ActiveProcessorCount'

# Същият base, но различен heap budget; сравнявай с headroom, не с 512Mi ~= Xmx:
docker run --rm --memory=512m --cpus=1 --env JDK_JAVA_OPTIONS=-XX:MaxRAMPercentage=60.0 \
  --entrypoint java education-kubernetes-lab:dev -XX:+PrintFlagsFinal -version 2>&1 \
  | grep -E 'MaxRAMPercentage|MaxHeapSize'
```

**Контролирани променливи:** един и същ image digest/JDK, machine, runtime/cgroup, memory+CPU limits; сравни maximum heap, GC selection, actual RSS и request latency. **Не** прави conclusion за throughput от `java -version`.

## 4. OOME vs OOMKilled — engineering response

| Java `OutOfMemoryError` | Linux/Kubernetes `OOMKilled` |
| --- | --- |
| JVM заявява неспособност да удовлетворява дадена memory allocation; може да е heap, metaspace, direct memory | Container/Pod memory budget е нарушен; kernel убива процеса |
| Възможно JVM да остане жива и да е в деградирано състояние | Процесът е прекратен, може да няма heap dump/Java stack trace |
| Полезни са JFR, heap histograms, heap dumps и exception logs | Полезни са `kubectl describe pod`, `lastState.terminated.reason`, cgroup metrics, RSS/native allocation |
| `-XX:+ExitOnOutOfMemoryError` дефинира crash policy (внимание към cleanup) | Решава се с resource/heap/native budget + leak/traffic анализ, не само с Java flag |

За heap dump `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/diagnostics` е полезно само когато има реално **writable, достатъчно голямо, защитено и персистентно** място. В [Kubernetes учебния Pod](../../../infrastructure/kubernetes/manifests/30-deployment.yaml) **няма** такъв persistent volume, затова не включваме dump flag по подразбиране. Heap dumps могат да съдържат пароли и лични данни; настрой минимални права/TTL и достъп.

## 5. Multi-stage image, layers и `jlink` — P2 допълнение

Лекторът сравнява fat JAR слой с Spring Boot layers: ако зависимости са отделен image layer, rebuild след малка Java промяна често прехвърля само последния application layer. [Kubernetes Dockerfile](../../../infrastructure/kubernetes/Dockerfile) вече има multi-stage JDK→JRE build, **но не** претендира за оптимално кеширани Spring layers (цял fat JAR се копира в един слой). Това са две различни оптимизации.

`jlink` позволява custom runtime image с подмножество модули, но модули се избират **според реалното приложение и зависимостите**, не по най-малкия възможен JAR. Reflection, ServiceLoader, TLS/JDBC, locales, CA trust, attach/JFR/`jcmd` могат да изискват допълнителни модули/tools. `jdeps` е отправна точка, не runtime guarantee. Преди да приемеш нов image: startup, TLS, DNS, timezone, JFR, heap dump, debugger и rolling-update test.

**Trade-off matrix**:

| Image strategy | Печалба | Цена/защо не веднага |
| --- | --- | --- |
| Maven build → JRE runtime (текущо) | По-малка build/attack surface; най-лесен debug | Цял JRE + mutable tags в demo |
| Spring layered JAR или Jib/Buildpacks | Layer cache/rebuild efficiency | Build tool conventions, registry auth/version pins |
| JRE on distroless | По-малък userland, няма shell | Трудно `exec`, диагностика чрез ephemeral container/JFR |
| `jlink` custom runtime | По-малко Java modules | динамични dependencies/diagnostic modules, per-app maintenance |
| `FROM scratch` | Минимален userland | Native dependencies, certs/locales/architecture трудни за audit |
| Multi-platform AMD64/ARM64 | Планиране на hardware/cost | JNI/native libs + base manifest platform support, per-arch testing |

## 6. Project Leyden AOT — не дублираме стария курс

Лекторът показва AOT cache генерация с **реална training run**, след което използване в runtime, като набляга на *classpath order*. Това вече е покрито в [Project Leyden](README.md). Новото за контейнерите: AOT cache файлът трябва да е произведен за **същата версия на JDK, bytecode, classpath order, flags, architecture/CPU assumptions и представителен workload**, а не просто да бъде copy-нат между произволни build/runtime stages. Release pipeline трябва да включва `verify JDK/cache compatibility` и fallback policy; няма универсален startup speedup.

`Compact Object Headers` (JDK25 opt-in, JDK27 default) може да намалят object size, но alignment/padding понякога правят разликата нула, а latency/GC резултатите зависят от workload. Нашият [JVM performance lab](README.md) вече има измервания; не пренасяме сценичните числа като гаранция.

## 7. Shutdown и променливи ресурси

Spring Boot `server.shutdown=graceful` и timeout per phase се съобразяват с K8s `terminationGracePeriodSeconds`. `preStop` sleep е counted в същия budget и SIGKILL не предоставя Java cleanup. В момента Kubernetes container-level CPU/memory resize може да се направи без Pod recreation при допустимите условия (stable 1.35), но **Java heap maximum е фиксиран в startup context**, освен ако конкретен runtime feature изрично не го управлява. При промяна на memory budget практическата безопасна опция често е controlled rollout.

## 8. Decision contract

- **Проблем:** JVM не е контейнер; неговите heap/native/GC настройки трябва да се наблюдават спрямо real container limits.
- **Кога:** `OOMKilled`, нестабилен startup, бавни rollouts, image rebuild overhead, смяна CPU архитектура.
- **Кога НЕ:** не настройвай `MaxRAMPercentage=75`, custom `jlink`, `AlwaysPreTouch` и AOT по навик. `AlwaysPreTouch` може да увеличи стартовото време/RSS и да доведе до ранен OOM kill.
- **Печалба:** предсказуем heap/native бюджет, reproducible image, faster diagnosis, по-бърз rebuild при layering.
- **Загуба:** platform-specific tuning, diagnostic modules, base image maintenance, CI/deploy сложност.
- **По-просто:** стандартен JRE image, non-root, ясни limits и JFR; оптимизирай само доказан bottleneck.
- **Доказателство:** actual `docker --memory/--cpus` inspection, [HTTP/JUnit + kind test](../../../infrastructure/kubernetes/README.md), [Leyden benchmarking](README.md). Няма изкуствен expected startup speedup.

## Оригинални източници

- [Albert Attard — Devoxx 2026 talk](https://www.youtube.com/watch?v=LPhGSgO5eKE), предоставен SRT.
- [Alain Regnier — Kubernetes for Developers](https://www.youtube.com/watch?v=AxhLQZqZdIg), предоставен SRT.
- [JDK 25 `java` command / memory percentages](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html).
- [Spring Boot Kubernetes probes & shutdown](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).
- [OpenJDK jlink tool](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html).
- [Kubernetes resource management](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/).
