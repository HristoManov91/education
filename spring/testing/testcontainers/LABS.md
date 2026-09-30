# Hands-on Labs — Containers & Testcontainers

Това е практическата част. След всяка стъпка си отговаряй: **какъв process/image/container/network/storage object създадох и къде живее той?**

Командите са приложими на Windows 11 + Docker Desktop и macOS + Docker Desktop.

---

# Lab 0 — Preflight

```bash
docker version
docker info
docker run --rm hello-world
```

Windows допълнително:

```bash
wsl --status
wsl -l -v
```

На macOS няма WSL2; Docker Desktop използва Linux VM.

---

# Lab 1 — Container като process

```bash
docker run --name process-lab -d alpine:3.22 sleep 600
docker ps
docker top process-lab
docker inspect process-lab
docker exec process-lab ps
```

Cleanup:

```bash
docker rm -f process-lab
```

Въпрос: виждаш ли цяла отделна OS или малък process tree в изолиран environment?

---

# Lab 2 — Image vs container

```bash
docker pull alpine:3.22
docker image ls alpine
docker run --name alpine-a -d alpine:3.22 sleep 600
docker run --name alpine-b -d alpine:3.22 sleep 600
docker ps
```

Един image, две running instances.

```bash
docker rm -f alpine-a alpine-b
```

---

# Lab 3 — Writable state е disposable

```bash
docker run --name fs-lab alpine:3.22 sh -c "echo hello > /learning.txt && cat /learning.txt"
docker start fs-lab
docker exec fs-lab cat /learning.txt
docker rm -f fs-lab
docker run --name fs-lab alpine:3.22 sh -c "ls /learning.txt || true"
docker rm -f fs-lab
```

Нов container от същия image няма writable state-а на стария.

---

# Lab 4 — Volume lifecycle

```bash
docker volume create learning-data
docker run --rm -v learning-data:/data alpine:3.22 sh -c "echo persisted > /data/value.txt"
docker run --rm -v learning-data:/data alpine:3.22 cat /data/value.txt
docker volume rm learning-data
```

Container-ът между двете команди е различен, volume-ът е същият.

---

# Lab 5 — Port mapping

```bash
docker run --name port-lab -d -p 18080:80 nginx:alpine
docker port port-lab
```

macOS:

```bash
curl http://localhost:18080
```

Windows PowerShell:

```powershell
curl.exe http://localhost:18080
```

```text
host localhost:18080 → container :80
```

```bash
docker rm -f port-lab
```

---

# Lab 6 — Dynamic host port

```bash
docker run --name random-port-lab -d -p 80 nginx:alpine
docker port random-port-lab
```

Ще видиш dynamic host port. Това е същият принцип, който Testcontainers използва.

```bash
docker rm -f random-port-lab
```

---

# Lab 7 — Docker network и service name

```bash
docker network create learning-net
docker run --name web-lab --network learning-net -d nginx:alpine
docker run --rm --network learning-net alpine:3.22 wget -qO- http://web-lab
docker rm -f web-lab
docker network rm learning-net
```

`web-lab` е network name/alias, не host port.

---

# Lab 8 — Compose

От директорията на модула:

```bash
docker compose up -d
docker compose ps
docker compose exec postgres pg_isready -U education -d education
docker compose exec redis redis-cli PING
```

Очакваме Redis да върне `PONG`.

```bash
docker compose down
docker compose down -v
```

`down -v` премахва и named volume state.

---

# Lab 9 — Spring Boot срещу Compose

```bash
docker compose up -d
```

От repository root:

```bash
mvn -pl spring/testing/testcontainers spring-boot:run -Dspring-boot.run.profiles=local
```

Тук developer управлява Compose lifecycle-а, а приложението се връзва към фиксирани localhost ports.

Cleanup:

```bash
docker compose down -v
```

---

# Lab 10 — Build на application image

От repository root:

```bash
mvn -pl spring/testing/testcontainers -am package -DskipTests
```

От директорията на модула:

```bash
docker build -t education-testcontainers:good .
docker image inspect education-testcontainers:good
docker run --rm --entrypoint id education-testcontainers:good
```

Търси UID 10001.

---

# Lab 11 — BAD security image

```bash
docker build -f Dockerfile.bad -t education-testcontainers:bad .
docker image inspect education-testcontainers:bad
```

Сравни configured user и environment metadata с good image.

---

# Lab 12 — Testcontainers без Spring

```bash
mvn -pl spring/testing/testcontainers -Dtest=GenericContainerLifecycleTest test
```

Прочети [`GenericContainerLifecycleTest.java`](./src/test/java/bg/hristomanov/education/containers/GenericContainerLifecycleTest.java).

Фокус: `getHost()` и `getMappedPort(...)`.

---

# Lab 13 — Spring Boot + PostgreSQL + Redis

```bash
mvn -pl spring/testing/testcontainers -Dtest=SpringBootServiceConnectionIntegrationTest test
```

Прочети [`SpringBootServiceConnectionIntegrationTest.java`](./src/test/java/bg/hristomanov/education/containers/SpringBootServiceConnectionIntegrationTest.java).

Отговори си:

1. Кой стартира PostgreSQL?
2. Кой избира host port?
3. Откъде Spring DataSource получава URL?
4. Защо Redis има `name = "redis"`?
5. Кой чисти containers след теста?

---

# Lab 14 — `@DynamicPropertySource` fallback

Отвори [`DynamicPropertySourceFallbackIntegrationTest.java`](./src/test/java/bg/hristomanov/education/containers/DynamicPropertySourceFallbackIntegrationTest.java).

По подразбиране е disabled, за да не стартираме втори PostgreSQL във всеки build.

Махни временно `@Disabled` и стартирай:

```bash
mvn -pl spring/testing/testcontainers -Dtest=DynamicPropertySourceFallbackIntegrationTest test
```

Сравни кой слой знае property names при `@ServiceConnection` и при `@DynamicPropertySource`.

---

# Lab 15 — Два паралелни test run-а

В два terminal-а стартирай:

```bash
mvn -pl spring/testing/testcontainers -Dtest=GenericContainerLifecycleTest test
```

Докато вървят:

```bash
docker ps
```

Наблюдавай различните mapped host ports.

---

# Lab 16 — Свържи знанието с repository-то

След горните labs отвори:

- `spring/caching/redis-eviction` — намери manual `docker run`;
- `persistence/specification-querydsl` — виж PostgreSQL Testcontainers roadmap;
- `spring/reliability/idempotency` — виж concurrency/unique constraint exercise.

Това са реалните места, където знанието ще се използва.

---

# Cleanup checklist

```bash
docker ps -a
docker network ls
docker volume ls
```

Не прави глобално destructive cleanup на машина с други Docker проекти. Премахвай само lab resources.

---

# Финална самопроверка

Трябва да можеш със свои думи да обясниш:

1. Защо container не е VM.
2. Какво дават namespaces.
3. Какво дават cgroups.
4. Защо Windows/macOS имат Linux backend за Linux containers.
5. Image vs container.
6. Container port vs host port.
7. Защо `localhost` вътре в container не е друг container.
8. Volume vs container filesystem.
9. Compose vs Testcontainers lifecycle.
10. Защо Testcontainers използва dynamic ports.
11. Какво решава readiness strategy.
12. Как `@ServiceConnection` маха manual property glue.
13. Кога `@DynamicPropertySource` остава полезен.
14. Защо real PostgreSQL е по-силно DB integration доказателство от H2.
15. Защо не трябва container във всеки unit test.