# Containers & Testcontainers — от Linux process до Spring Boot integration test

Този модул е за Java/Spring backend developer, който иска да разбира **какво реално става**, а не само да запомни `docker run` и няколко annotations.

Входният проблем е реален:

```text
integration test
→ трябва PostgreSQL / Redis
→ стартирай ги ръчно на localhost
→ на моята машина работи
→ друг developer / CI има друга версия, друг port или никакъв service
→ тестът вече не е self-contained
```

Целта е да стигнем от process/isolation до:

```java
@Container
@ServiceConnection
static final PostgreSQLContainer POSTGRES = ...;
```

и да виждаме цялата верига:

```text
JUnit
→ Testcontainers Java API
→ Docker API
→ image
→ isolated Linux process
→ network + mapped port
→ PostgreSQL / Redis
→ Spring Boot ConnectionDetails
→ application code
```

## Как да учиш модула

1. Прочети секции 1–10.
2. Изпълни [LABS.md](./LABS.md) до Docker Compose.
3. Прочети Testcontainers частта.
4. Стартирай `GenericContainerLifecycleTest`.
5. Стартирай `SpringBootServiceConnectionIntegrationTest`.
6. Разгледай fallback примера с `@DynamicPropertySource`.
7. Върни се към Redis, QueryDSL и Idempotency модулите.

## README → код

| Концепция | Код / config | Доказателство |
| --- | --- | --- |
| Basic Spring Boot application | [`TestcontainersLearningApplication.java`](./src/main/java/bg/hristomanov/education/containers/TestcontainersLearningApplication.java) | application context |
| Реален PostgreSQL access | [`CustomerDirectory.java`](./src/main/java/bg/hristomanov/education/containers/customer/CustomerDirectory.java) | service-connection integration test |
| Реален Redis access | [`GreetingCache.java`](./src/main/java/bg/hristomanov/education/containers/cache/GreetingCache.java) | service-connection integration test |
| Manual local dependencies | [`compose.yaml`](./compose.yaml) | `docker compose up` |
| Local Spring profile | [`application-local.yml`](./src/main/resources/application-local.yml) | app срещу Compose |
| Root/non-root comparison | [`Dockerfile.bad`](./Dockerfile.bad), [`Dockerfile`](./Dockerfile) | image/container inspect |
| GenericContainer lifecycle | [`GenericContainerLifecycleTest.java`](./src/test/java/bg/hristomanov/education/containers/GenericContainerLifecycleTest.java) | Redis PING + mapped port |
| Spring Boot `@ServiceConnection` | [`SpringBootServiceConnectionIntegrationTest.java`](./src/test/java/bg/hristomanov/education/containers/SpringBootServiceConnectionIntegrationTest.java) | real PostgreSQL + Redis |
| `@DynamicPropertySource` fallback | [`DynamicPropertySourceFallbackIntegrationTest.java`](./src/test/java/bg/hristomanov/education/containers/DynamicPropertySourceFallbackIntegrationTest.java) | disabled comparison lab |

---

# 1. Започваме от process, не от Docker

Когато стартираш `java -jar app.jar`, операционната система създава process.

Практически process има:

```text
PID
memory / address space
open files
environment variables
current working directory
user / permissions
network sockets
threads
```

JVM е process. PostgreSQL server е process. Redis server е process.

Преди container-ите mental model-ът е:

```text
Operating System
├── java process
├── postgres process
└── redis process
```

---

# 2. Как Linux изолира process-и

Полезният mental model е:

> **Container е process, на който Linux kernel показва ограничен/изолиран изглед към системата и прилага resource rules.**

Двете важни primitives са:

```text
namespaces → какво process-ът вижда
cgroups    → какви ресурси може да използва / как се accounting-ват
```

## 2.1 Namespaces

Най-полезните за mental model-а са:

- PID namespace — отделен изглед към process tree;
- network namespace — отделни interfaces, routes и ports;
- mount namespace — различен filesystem/mount изглед;
- UTS namespace — отделен hostname;
- IPC namespace;
- user namespace.

Важно: isolation не означава отделна физическа машина.

## 2.2 Cgroups

Cgroups решават resource control/accounting, например CPU, memory, I/O и process count.

```text
namespace → view / isolation
cgroup    → resources
```

---

# 3. Container vs Virtual Machine

VM:

```text
Host OS
→ Hypervisor
→ Guest OS
→ Guest kernel
→ Application
```

Linux container:

```text
Linux kernel
→ isolated process
```

Container-ите не boot-ват собствен Linux kernel за всеки container.

---

# 4. Windows 11 и macOS: откъде идва Linux kernel-ът?

Това е важно специално за твоите две машини.

## 4.1 Windows 11 + Docker Desktop

При WSL2 backend:

```text
Windows 11
├── IntelliJ / Maven / JUnit
└── Docker CLI
      ↓
   Docker Desktop
      ↓
   WSL2 Linux environment / Linux kernel
      ↓
   Linux containers
```

Linux container-ът не използва Windows NT kernel като Linux kernel. WSL2 предоставя Linux kernel-а.

## 4.2 macOS + Docker Desktop

macOS използва XNU, не Linux kernel. Затова:

```text
macOS
├── IntelliJ / Maven / JUnit
└── Docker CLI
      ↓
   Docker Desktop
      ↓
   lightweight Linux VM
      ↓
   Linux kernel
      ↓
   Linux containers
```

## 4.3 Какво означава това за Testcontainers?

И на двете машини:

```text
JUnit
→ Testcontainers
→ Docker API
→ Docker Desktop backend
→ container
```

Test-ът не трябва да hardcode-ва host assumptions. Затова използваме `getHost()` и `getMappedPort(...)`.

---

# 5. Image не е Container

```text
IMAGE
immutable template
   │ docker run
   ↓
CONTAINER
running instance / process
```

Един image може да стартира много containers с различни ports, env variables, volumes и networks.

Image е съставен от immutable layers. Running container добавя writable state върху image-а.

---

# 6. Dockerfile: recipe за image

[`Dockerfile`](./Dockerfile) е минимален baseline:

```dockerfile
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/testcontainers-learning-1.0-SNAPSHOT.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

```text
Dockerfile
→ docker build
→ Image
→ docker run
→ Container
```

Важните instructions: `FROM`, `WORKDIR`, `COPY`, `RUN`, `ENV`, `USER`, `ENTRYPOINT`, `CMD`.

---

# 7. Ports: container port не е host port

При `docker run -p 15432:5432 ...`:

```text
host:15432
   ↓
container:5432
```

Testcontainers предпочита dynamic host port, за да няма collision между паралелни test run-ове.

```java
Integer port = POSTGRES.getMappedPort(5432);
```

`EXPOSE 8080` в Dockerfile е metadata; не публикува автоматично port към host-а.

---

# 8. Networking: защо localhost обърква

В container:

```text
localhost → този container
```

Не означава Windows host, macOS host или друг container.

Два container-а в една Docker network могат да комуникират по service/container name:

```text
application
├── postgres:5432
└── redis:6379
```

Запомни:

```text
container-to-container → Docker network / service name
host-to-container      → published/mapped port
```

---

# 9. Filesystem, volume и bind mount

Container writable filesystem е свързан с lifecycle-а на container-а.

Volume има отделен lifecycle:

```text
container
→ Docker volume
→ persistent data
```

Bind mount свързва конкретен host path с container path. Това е мощно, но и security-sensitive.

За integration tests обикновено искаме dependency state-ът да е disposable и контролиран.

---

# 10. Docker Compose

[`compose.yaml`](./compose.yaml) описва локална development среда с PostgreSQL + Redis.

```bash
cd spring/testing/testcontainers
docker compose up -d
```

Compose решава: **какви по-дълго живеещи services искам за локалната си среда?**

Testcontainers решава различен проблем: **какви disposable dependencies са нужни на конкретния test lifecycle?**

---

# 11. Security: container не е security magic

Първото видео от набора е полезно точно тук: **Coffee + Software — Is Your Container Security Strategy Setting You Up for Failure?**

Container isolation намалява blast radius, но container не е абсолютна security boundary.

## 11.1 Root inside container

`Dockerfile.bad` оставя process-а root и bake-ва примерна password стойност в image metadata.

`Dockerfile` стартира JVM с non-root UID и не bake-ва secret.

## 11.2 Не bake-ваме secrets в image

Image може да бъде push-нат, кеширан, inspect-нат и споделен. Runtime secret трябва да идва от runtime secret/config mechanism.

## 11.3 Bind mounts

Write access към чувствителна host директория отслабва практическата isolation граница.

## 11.4 `--privileged`

Не го използваме като универсален fix. Първо намираме конкретната permission/capability.

## 11.5 Docker daemon

Достъпът до Docker API/socket е чувствителен: той позволява създаване на containers, mounts и широки runtime permissions.

---

# 12. Защо localhost integration test е крехък

Naive предпоставка:

```text
Увери се, че PostgreSQL работи на localhost:5432
```

Скрити assumptions:

- Docker/Postgres е стартиран;
- version е правилният;
- port е свободен;
- database е чиста;
- credentials съвпадат;
- друг test не използва същите данни.

Това е manual precondition, а не self-contained test.

---

# 13. H2 не е PostgreSQL

H2 е полезен, но:

```text
H2 test passing ≠ PostgreSQL behavior proven
```

Разлики има в SQL syntax, data types, locking, constraints, transaction behavior, JSON/array/vendor features и query planner.

Следователно:

```text
pure business unit test → mock/fake може да е идеален
database integration test → real production-family database е по-силно доказателство
```

Testcontainers не означава да заменим всички unit tests с containers.

---

# 14. Какво прави Testcontainers

```text
JUnit starts
→ Testcontainers discovers Docker environment
→ pull image ако липсва
→ create container
→ configure network/env/ports
→ start
→ wait until ready
→ run test
→ cleanup
```

`GenericContainerLifecycleTest` демонстрира това без Spring.

```java
@Container
static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:8.10.2"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());
```

Test-ът открива runtime host/port вместо да hardcode-ва.

---

# 15. Running не винаги означава Ready

```text
Docker state = running
PostgreSQL = още init/recovery
application connection = fail
```

Затова има wait strategies. В Redis примера използваме `Wait.forListeningPort()`.

```text
process started ≠ service ready
```

---

# 16. Testcontainers 2.x: важно за стари tutorials

Проектът използва Spring Boot 4.1.1, който управлява Testcontainers 2.0.5.

При Testcontainers 2.0:

- module artifacts са с `testcontainers-` prefix;
- typed container classes са преместени в module-specific packages;
- JUnit 4 support е премахнат.

Стар пример може да използва `org.testcontainers.containers.PostgreSQLContainer`, а текущият код използва `org.testcontainers.postgresql.PostgreSQLContainer`.

---

# 17. Spring Boot `@ServiceConnection`

Без Spring integration бихме регистрирали ръчно datasource URL/username/password.

С Boot:

```java
@Container
@ServiceConnection
static final PostgreSQLContainer POSTGRES = ...;
```

Boot създава connection details за dependency-то.

`SpringBootServiceConnectionIntegrationTest` има едновременно PostgreSQL и Redis:

```text
Spring Boot
├── PostgreSQL Testcontainer
│   → JdbcConnectionDetails
│   → DataSource / JPA
└── Redis GenericContainer
    → Redis connection details
    → StringRedisTemplate
```

Тестът доказва JPA write/read върху real PostgreSQL и Redis write/read върху real Redis.

---

# 18. Защо Redis има `@ServiceConnection(name = "redis")`

PostgreSQL container-ът има специфичен Java type. Redis примерът е `GenericContainer<?>`.

Само от `GenericContainer` Spring Boot не може да знае какъв service стои вътре, затова подаваме hint `name = "redis"`.

---

# 19. `@DynamicPropertySource`: fallback

`DynamicPropertySourceFallbackIntegrationTest` показва manual wiring:

```java
registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
registry.add("spring.datasource.username", POSTGRES::getUsername);
registry.add("spring.datasource.password", POSTGRES::getPassword);
```

Полезно е при custom property contract или когато няма Spring Boot ConnectionDetails support.

Fallback test-ът е disabled по подразбиране, за да не стартира втори PostgreSQL при всеки build.

---

# 20. Compose vs Testcontainers

| | Docker Compose | Testcontainers |
| --- | --- | --- |
| Lifecycle | developer/tooling | test code |
| Основна цел | local environment | automated tests |
| Lifetime | по-дълъг | test-scoped |
| Ports | често фиксирани | обикновено dynamic |
| State | може да пази volume | обикновено disposable |
| Java awareness | няма | test кодът държи container object |

При нас:

```text
Compose → учене + local development
Testcontainers → deterministic integration tests
```

---

# 21. Къде ще ни е полезно в repository-то

## Redis caching

`spring/caching/redis-eviction` в момента има manual `docker run`. След тази тема можем да го направим self-provisioning.

## Specification + QueryDSL

`persistence/specification-querydsl` има roadmap за PostgreSQL Testcontainers profile.

## Idempotency

`spring/reliability/idempotency` има PostgreSQL Testcontainers concurrency exercise. Това е важно за real unique-constraint/concurrency semantics.

---

# 22. Стартиране

От repository root:

```bash
mvn -pl spring/testing/testcontainers -am test
```

Само lifecycle test:

```bash
mvn -pl spring/testing/testcontainers -Dtest=GenericContainerLifecycleTest test
```

Само Spring integration test:

```bash
mvn -pl spring/testing/testcontainers -Dtest=SpringBootServiceConnectionIntegrationTest test
```

Docker Desktop трябва да работи. Командите са еднакви на Windows 11 и macOS.

`@Testcontainers(disabledWithoutDocker = true)` позволява Docker-less environment да skip-не lab тестовете; CI с Docker трябва реално да ги изпълнява.

---

# 23. Test strategy: къде НЕ трябва container

```text
many fast unit tests
↓
fewer integration tests with real dependencies
↓
few end-to-end tests
```

Use container когато поведението зависи от real SQL, transactions, constraints, Redis/broker semantics или vendor features.

Не use container за pure Java business rule без infrastructure dependency.

---

# 24. Static vs instance `@Container`

Опростено:

```text
static @Container   → един container lifecycle за test class
instance @Container → lifecycle може да е per test instance/method
```

Static е по-бърз за много integration scenarios, но data cleanup пак е наша отговорност.

---

# 25. CI mental model

```text
checkout
→ JDK 25
→ mvn verify
→ JUnit
→ Testcontainers
→ Docker on runner
→ PostgreSQL + Redis
→ tests
```

Dependency version-ът е част от test code/image tag, а не каквото случайно е инсталирано на host-а.

---

# 26. Anti-patterns

- hardcoded `localhost:5432/6379` в Testcontainers test;
- `Thread.sleep(...)` като readiness strategy;
- `latest` image без причина;
- container за всеки unit test;
- shared persistent test DB без deterministic reset;
- `--privileged` като универсален fix;
- secrets bake-нати в image.

---

# 27. Mental model за запомняне

```text
PROCESS            → executable code, memory, files, sockets
NAMESPACE          → какво process-ът вижда
CGROUP             → какви ресурси може да използва
IMAGE              → immutable template/layers
CONTAINER          → running isolated process instance
PORT MAPPING       → host endpoint → container endpoint
VOLUME             → data lifecycle извън конкретния container
COMPOSE            → declarative local multi-container environment
TESTCONTAINERS     → test code управлява container lifecycle
SERVICE CONNECTION → Spring Boot превръща runtime connection info в app config
```

---

# 28. Code-review checklist

```text
[ ] Test-ът има ли hidden dependency към localhost?
[ ] Dependency version-ът pin-нат ли е?
[ ] Реално ли трябва container или unit test е достатъчен?
[ ] Port-ът discover-ва ли се runtime?
[ ] Readiness различена ли е от process running?
[ ] Test state reset-ва ли се deterministically?
[ ] Използваме ли @ServiceConnection когато Boot има support?
[ ] Ако имаме @DynamicPropertySource, защо е нужен?
[ ] Image-ът съдържа ли secrets?
[ ] Application container-ът има ли причина да е root?
[ ] Има ли опасен bind mount?
[ ] Използва ли се --privileged без конкретна причина?
[ ] Compose lifecycle смесен ли е с automated test lifecycle?
[ ] Работи ли еднакво през Docker abstraction на Windows/macOS/CI?
```

---

# 29. Упражнения

1. Изпълни [LABS.md](./LABS.md).
2. Премести Redis integration test-овете към Testcontainers.
3. Добави PostgreSQL Testcontainers profile към QueryDSL темата.
4. Замени H2 concurrency доказателството в Idempotency с PostgreSQL.
5. Добави custom Docker `Network` и network aliases.
6. Направи broken readiness пример и го поправи с WaitStrategy.
7. Сравни static и per-test container време.
8. Пусни два паралелни test run-а и наблюдавай mapped ports.
9. Inspect-ни good/bad image user/environment metadata.
10. По-късно добави Toxiproxy lab за latency/network failures.

---

# 30. Какво засега е optional

Не е нужно още да учиш в дълбочина OCI runtime internals, `runc`, `containerd`, OverlayFS, veth/iptables/nftables, Kubernetes CRI или cgroup filesystem internals.

Трябва да знаеш къде се намират в картината, но не да можеш да имплементираш container runtime.

---

# Оригинални източници

## Видеа, от които тръгна темата

- Coffee + Software — **Is Your Container Security Strategy Setting You Up for Failure?**
  https://www.youtube.com/watch?v=Sqmx9eikIiI
- Допълнително видео за Testcontainers/Spring, предоставено за темата:
  https://www.youtube.com/watch?v=zsmR4SZN_6g
- Допълнително видео за containers/Testcontainers, предоставено за темата:
  https://www.youtube.com/watch?v=p3NfPM1SKLg
- SpringDeveloper — **Spring Office Hours: S5E19 - Docker, Compose, Testcontainers, Oh My!**
  https://www.youtube.com/watch?v=jWWi0J6ggeI

## Docker / Linux / OCI

- Docker — What is a container? https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-a-container/
- Docker — What is an image? https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-an-image/
- Docker — Publishing ports https://docs.docker.com/get-started/docker-concepts/running-containers/publishing-ports/
- Docker — Persisting data https://docs.docker.com/get-started/docker-concepts/running-containers/persisting-container-data/
- Docker — Multi-container applications https://docs.docker.com/get-started/docker-concepts/running-containers/multi-container-applications/
- Docker Engine security https://docs.docker.com/engine/security/
- Docker Desktop WSL2 https://docs.docker.com/desktop/features/wsl/
- Docker Desktop VMM https://docs.docker.com/desktop/features/vmm/
- Linux namespaces(7) https://man7.org/linux/man-pages/man7/namespaces.7.html
- Linux cgroup v2 https://docs.kernel.org/admin-guide/cgroup-v2.html
- OCI Image Spec https://specs.opencontainers.org/image-spec/

## Testcontainers

- Testcontainers for Java https://java.testcontainers.org/
- JUnit 5 quickstart https://java.testcontainers.org/quickstart/junit_5_quickstart/
- Creating containers https://java.testcontainers.org/features/creating_container/
- Startup and waits https://java.testcontainers.org/features/startup_and_waits/
- Database containers https://java.testcontainers.org/modules/databases/
- PostgreSQL module https://java.testcontainers.org/modules/databases/postgres/
- Testcontainers 2.0 release notes https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.0

## Spring Boot

- Spring Boot — Testcontainers https://docs.spring.io/spring-boot/reference/testing/testcontainers.html
- Spring Boot — Development-time services https://docs.spring.io/spring-boot/reference/features/dev-services.html
- Spring Framework — DynamicPropertySource https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/dynamic-property-sources.html

---

# Изходен въпрос

Ако видиш:

```java
@Container
@ServiceConnection
static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
```

не го чети като annotation магия. Чети го като:

```text
JUnit управлява test lifecycle
→ Testcontainers говори с Docker
→ Docker стартира isolated PostgreSQL process от pin-нат image
→ container port 5432 се map-ва динамично
→ readiness се изчаква
→ Spring Boot получава connection details
→ DataSource сочи към тази disposable database
→ test-ът доказва поведението върху реален PostgreSQL
```

Това е целта на целия модул.