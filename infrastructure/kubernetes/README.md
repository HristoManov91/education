# P1 · Kubernetes for Java Backend Developers — от локален Spring Boot до работещ Pod

> **Източник:** [Kubernetes for Developers — Alain Regnier, Devoxx Belgium 2026](https://www.youtube.com/watch?v=AxhLQZqZdIg) (00:00–47:15); подкрепящи примери от [Production Ready Java Containers — Albert Attard](https://www.youtube.com/watch?v=LPhGSgO5eKE). **Изпълним Spring Boot 4.1.2 / Java 25 модул**, Kubernetes `kind` от v1.35 за автоматизирания smoke, без платена cloud услуга. Съществуващият [Docker и Testcontainers курс](../../spring/testing/testcontainers/README.md) остава prerequisite, не се копира тук.

## 1. Входен production казус — „локално работи, в Kubernetes се рестартира“

Разработваме Spring Boot API. `java -jar` и `docker run` работят. След деплой при бавен startup Kubernetes изпраща HTTP traffic твърде рано; нов Pod е убит по време на стартиране, а rolling update прекъсва заявки. На друг cluster имаме `OOMKilled`, въпреки че Java heap е слабо натоварен. Не е достатъчно да сменим `replicas: 3`.

Искаме да докажем **цялата верига**:

```text
Java code + JUnit
  → executable Spring Boot JAR
  → multi-stage Docker build (JDK builder → JRE runtime, non-root)
  → Docker image, loaded in local kind nodes
  → Kubernetes Deployment (2 replicas), Pod и container runtime
  → Service (ClusterIP) → port-forward → HTTP
  → Actuator startup/liveness/readiness → safe rollout & SIGTERM
  → resource/security controls → diagnostics
```

**Цел:** да можеш да разбереш *защо* един Pod не е Ready, защо е рестартиран, как се спира коректно и коя граница между Java и Kubernetes отговаря за проблема.

## 2. Какво ще научим

1. Pod ≠ Deployment ≠ ReplicaSet ≠ Service ≠ Ingress. Pod е управляемата runtime единица; Deployment желае определен набор Pod-ове, Service дава стабилен discovery/маршрутизиране към Ready endpoints.
2. `requests` са основа за scheduling и resource shares, `limits` ограничават консумацията; не е вярно, че всяко CPU request осигурява абсолютно запазени ядра във всички режими.
3. `startupProbe` забавя liveness/readiness проверките при бавен startup; `livenessProbe` е за неизправност, която може да се поправи с restart; `readinessProbe` решава дали Pod приема трафик.
4. SIGTERM, `preStop` и `terminationGracePeriodSeconds` имат общ времеви бюджет; `SIGKILL` не се хваща с shutdown hook.
5. Container `securityContext`, ConfigMap и environment variables не заменят secret management; имаме отделни правила за SBOM/signature/base image.
6. `kubectl logs --previous`, `describe`, events, rollout, `debug` и JFR обслужват различни диагностични въпроси.
7. Версията има значение: container-level in-place resize е stable в Kubernetes 1.35, Pod-level resize е отделна възможност с ограничения. **JVM heap не се преизчислява магически**, защото K8s е променил limit-а.

## 3. Изпълними файлове — README → код → тест

| Сценарий | Код/config | Доказателство |
| --- | --- | --- |
| HTTP Spring Boot | [KubernetesJavaApplication.java](src/main/java/bg/hristomanov/education/kubernetes/KubernetesJavaApplication.java), [HelloController.java](src/main/java/bg/hristomanov/education/kubernetes/HelloController.java) | [Application HTTP test](src/test/java/bg/hristomanov/education/kubernetes/KubernetesJavaApplicationTest.java) + [unit test](src/test/java/bg/hristomanov/education/kubernetes/HelloControllerTest.java) |
| In-flight work | [WorkController.java](src/main/java/bg/hristomanov/education/kubernetes/WorkController.java) | [bounds and interruption tests](src/test/java/bg/hristomanov/education/kubernetes/WorkControllerTest.java) |
| Actuator, graceful shutdown | [application.properties](src/main/resources/application.properties) | `/actuator/health/liveness`, `/readiness` in HTTP test / kind smoke |
| Multi-stage JDK/JRE + USER | [Dockerfile](Dockerfile), [.dockerignore](.dockerignore) | real `docker build`; [image hardening lesson](../../spring/testing/testcontainers/JAVA-IMAGE-HARDENING.md) |
| Namespace + ConfigMap + Service + Deployment | [manifests/](manifests/) | [offline manifest validator](scripts/validate_manifests.py), [negative validator tests](scripts/test_validate_manifests.py) |
| Real cluster rollout | [kind-smoke.sh](scripts/kind-smoke.sh) | [GitHub Actions kind test](../../.github/workflows/kubernetes-java-lab.yml), not a simulated YAML-only check |
| Debug, OOM, rollback | [OPERATIONS.md](OPERATIONS.md) | manual cluster/JFR exercise, no fabricated speedup |
| Advanced optional concepts | [ADVANCED-WATCH.md](ADVANCED-WATCH.md) | version-gated notes / not P1 baseline |

## 4. Първа лаборатория: локално (без Docker и Kubernetes)

**Изисквания:** JDK 25 и Maven. От repository root:

```bash
mvn -pl infrastructure/kubernetes test
mvn -pl infrastructure/kubernetes package
java -jar infrastructure/kubernetes/target/kubernetes-java-lab-1.0-SNAPSHOT.jar
```

В друга конзола:

```bash
curl -fsS http://localhost:8080/api/hello
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS 'http://localhost:8080/api/work?delayMs=2000'
```

Очакваме безопасен JSON с greeting, pod name (`local` извън cluster), Java feature и health `UP`. **Няма DB/Redis**: не въвеждаме нова инфраструктура, за да научим Kubernetes lifecycle.

## 5. Втора лаборатория: Docker image

```bash
docker build -t education-kubernetes-lab:dev infrastructure/kubernetes
docker image inspect education-kubernetes-lab:dev --format '{{.Config.User}}'
# Очаквано: 10001:10001; това НЕ доказва изцяло secure image.
docker run --rm -p 8080:8080 education-kubernetes-lab:dev
```

`Dockerfile` изгражда JAR в **builder stage**, а в runtime копира само готовия артефакт върху JRE image. **Това не е distroless/jlink**: те са P2 оптимизации, след доказана необходимост и runtime diagnostics. Tag-овете остават четими за учебния пример; за production pin-вай одобрени **multi-arch digest-и** и автоматизирай обновяването им. Не измисляме sha256 digest без реално проверен registry manifest.

## 6. Трета лаборатория: реален локален Kubernetes cluster (`kind`)

Изисквания: Docker Desktop/Engine, `kind`, `kubectl`, `curl` и Bash (Linux/macOS/WSL). Първоначален setup:

```bash
kind create cluster --name education-java
kind get clusters
kubectl cluster-info --context kind-education-java
```

От **repository root**:

```bash
bash infrastructure/kubernetes/scripts/kind-smoke.sh
```

Скриптът изгражда `education-kubernetes-lab:dev`, зарежда image-а в node container чрез `kind load docker-image`, прилага **само** четирите основни manifests (не optional NetworkPolicy), рестартира Deployment с rolling update, чака `rollout status` и проверява readiness + реални HTTP заявки през `port-forward`.

**Очаквано:** `KIND_SMOKE_PASS`. Това е работещ Kubernetes acceptance smoke **само ако реално е изпълнен**, не го приписвай на успешния Maven build.

Ръчно разгледай ресурсите:

```bash
kubectl -n education get pods -o wide
kubectl -n education get deployments,replicasets,services
kubectl -n education describe deployment java-kubernetes-lab
kubectl -n education get endpointslices -l kubernetes.io/service-name=java-kubernetes-lab
kubectl -n education rollout history deployment/java-kubernetes-lab
kubectl -n education logs deployment/java-kubernetes-lab --all-pods=true --tail=50
```

`ClusterIP` работи вътре в cluster-а. `kubectl port-forward` прави локален диагностичен тунел, **не** production ingress/load balancer. След лабораторията: `kind delete cluster --name education-java` (изтрива локалния cluster).

## 7. Как се отнасят Pod, Deployment, Service и probes

```text
Client → (Ingress/Gateway, опционално) → Service java-kubernetes-lab
                                              │ selector app=java-kubernetes-lab
                 ┌────────────────────────────┴────────────────────────┐
                 ▼                                                     ▼
           Pod (Ready)                                          Pod (Ready)
           Java :8080                                           Java :8080
           /actuator/health/readiness                            /actuator/health/readiness

Deployment desired replicas=2 → ReplicaSet → тези Pod-ове
```

- **BAD:** liveness зависи от Oracle/Redis. При падане на база K8s рестартира масово работещи JVM-и, утежнявайки incident-а.
- **GOOD:** liveness описва локалното състояние на приложението; readiness дава сигнал дали може да обработва заявки. Дори readiness зависимости трябва да се избират внимателно, иначе цялата услуга може да загуби Ready endpoints.
- **Startup probe:** полезен за startup с голяма вариативност. При наличието му liveness/readiness не се изпълняват преди успешния startup check.

Spring Boot конфигурацията и Deployment probes са в един и същи модул: няма магическа auto-discovery за health path, когато в YAML е написан грешен URL. Има JUnit HTTP test и kind smoke.

### P2 operational extension — Live Patching vs Rolling Update

[Live patching vs rolling update](LIVE-PATCHING-VS-ROLLING-UPDATES.md) уточнява какво поправяме: Linux kernel, native library, JDK или Maven JAR. Реалните ни два Pods, `maxUnavailable: 0` и readiness/termination probes се използват за **локален rolling update**, който не означава автоматично нов patch. Не е необходим платен KernelCare/LibCare агент.

## 8. Безопасен rollout и спиране

В [Deployment](manifests/30-deployment.yaml) задаваме `replicas: 2`, `maxUnavailable: 0`, `maxSurge: 1`, `minReadySeconds: 3` и `terminationGracePeriodSeconds: 35`.

```text
стар Pod → маркира се за прекратяване → Service endpoints се обновяват
          → preStop sleep 5s → SIGTERM към Java PID1
          → Spring Boot graceful shutdown до 20s → JVM exit
          → след общия grace budget, ако не е спрял → SIGKILL
```

**Не обещаваме zero downtime само с тези флагове.** Readiness propagation, ingress/sidecar, DNS, connection reuse, external transaction/idempotency и real traffic load също участват. Hook-ът е Kubernetes `sleep`, не команда в контейнера, и е stable от K8s 1.34; 5s са **част от** 35s, не допълнително време. За конкретен production SLO са нужни трафик тестове и настройка.

За ръчен сценарий: отвори `/api/work?delayMs=5000` и едновременно рестартирай Deployment. Провери дали заявката завършва; ако не, анализирай трасето и настройките, не приемай едно минаване за SLO доказателство.

## 9. Resources и security controls

Нашият образователен пример има `requests: 100m / 192Mi`, `limits: 1CPU / 512Mi`, `JDK_JAVA_OPTIONS=-XX:MaxRAMPercentage=60.0`. Този процент **не е универсална настройка**: трябва да оставиш бюджет за Metaspace, thread stacks, direct/native memory, code cache, JFR и други allocations.

Проверяваме `USER 10001`, `runAsNonRoot`, `readOnlyRootFilesystem`, `allowPrivilegeEscalation:false`, `capabilities.drop:[ALL]`, `seccompProfile:RuntimeDefault`, липсващ service-account token и writable `/tmp` чрез ограничен `emptyDir`. `emptyDir` не е персистентно място за heap dumps между Pod replacement-и.

Ограничената достъпност **не се решава с ConfigMap**: ако имаш парола/токен, използвай Kubernetes Secret или външен secret manager с RBAC и rotation. Secret не е равнозначно на криптиране/авторизация по подразбиране; не commit-вай истински secret в Git. [`40-network-policy.yaml`](manifests/40-network-policy.yaml) е **опционален** и се нуждае от CNI, който налага NetworkPolicy; kind vanilla не дава гаранция за enforcement.

## 10. Какво да НЕ използваме на този етап

- Не въвеждаме Helm, Gateway API, service mesh, HPA, VPA, operators и GitOps преди да разбираме Pod/Service/Deployment/probes и failure modes.
- Няма Kubernetes управление на production cloud акаунт, credentials или платени services.
- Не ползваме `kubectl debug` с привилегирован контейнер/remote mirroring срещу production без разрешение.
- Не заменяме Docker Compose с `kind` за всички проекти: Compose остава по-прост за dependency tests; `kind` е правилният инструмент, когато тестваме **Kubernetes semantics**.

## 11. Senior-level decision contract

| Въпрос | Отговор |
| --- | --- |
| Решава | Възпроизводими Java deployment/lifecycle/security troubleshooting сценарии, не само `docker run` |
| Кога | Услуга действително ще работи на Kubernetes или behavior-ът зависи от K8s APIs/probes/rollout |
| Кога НЕ | Един JVM process без orchestration, интеграционен тест за DB/Redis — Compose/Testcontainers стигат |
| Печалби | Desired-state controllers, graceful rollouts, service discovery, health gates, declarative resource/security checks |
| Загуби | API/cluster/tool version drift, YAML, RBAC/CNI/registry dependency, повече observability и release инженерство |
| По-просто | Spring Boot jar, Docker Compose или managed platform с по-малко K8s отговорности |
| Доказателство | HTTP/JUnit, Python manifest tests, **реален** kind smoke в отделен workflow, production-like metrics при реален adoption |

**Изходен checkpoint:** Обясни разликата между Java `OutOfMemoryError` и Kubernetes `OOMKilled`, кога да използваш readiness, какво става при SIGTERM и защо `maxUnavailable:0` не е универсален zero-downtime договор. Продължи с [Operations](OPERATIONS.md) и [JVM in containers](../../java/jvm/performance-evolution/JVM-IN-CONTAINERS.md).

## Оригинални източници

- [Devoxx 2026 — Kubernetes for Developers](https://www.youtube.com/watch?v=AxhLQZqZdIg), предоставен SRT (47 мин).
- [Devoxx 2026 — Production Ready Java Containers](https://www.youtube.com/watch?v=LPhGSgO5eKE), предоставен SRT.
- [kind documentation](https://kind.sigs.k8s.io/); [Kubernetes Pods](https://kubernetes.io/docs/concepts/workloads/pods/); [Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/); [Service](https://kubernetes.io/docs/concepts/services-networking/service/).
- [Kubernetes probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/); [Spring Boot Availability Probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes).
- [Kubernetes Pod termination](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination); [JEP/Java 25 runtime flags](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html).
- [Kubernetes security checklist](https://kubernetes.io/docs/concepts/security/application-security-checklist/); [Kubernetes In-place Container Resource Resize](https://kubernetes.io/docs/tasks/configure-pod-container/resize-container-resources/).
