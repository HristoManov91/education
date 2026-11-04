# P1 · Kubernetes troubleshooting, graceful shutdown и runtime decision tree

> [Kubernetes for Developers — Alain Regnier](https://youtu.be/AxhLQZqZdIg?t=720) (resource resize, preStop/SIGTERM, probes, `kubectl debug`) + [Production Ready Java Containers — Albert Attard](https://youtu.be/LPhGSgO5eKE?t=1980) (JVM heap/native memory, OOM). Изпълнявай върху [kind lab](README.md), никога първоначално върху production namespace.

## Реален казус: 3 симптома, 3 различни механизма

Идва сигнал „Java application unstable“. Преди да отваряш IDE или да увеличаваш heap-а, провери **в кой слой е симптомът**:

| Симптом | Първи команди | Вероятна причина / не прибързвай |
| --- | --- | --- |
| `Pending` | `describe pod`, `get events` | недостатъчен `requests` capacity, scheduling constraints, volume binding; не Java exception |
| `ImagePullBackOff` | `describe pod` / Events | грешен tag/digest, credentials, registry/architecture |
| `CrashLoopBackOff` | `logs --previous`, `describe pod` | application startup exception, bad config, OOM or probe failure |
| `Running`, но `0/1 Ready` | `describe pod`, probe response, endpointslices | readiness failure; **не** означава рестарт автоматично |
| `OOMKilled` / exit 137 | `describe pod` lastState.terminated, cgroup metrics | kernel container memory limit; Java heap може да е далеч под Xmx |
| `java.lang.OutOfMemoryError` | JVM logs/JFR/heap dump + native memory | JVM heap/direct/metaspace exhaustion; не гарантира `OOMKilled` |
| 502/503 по време на update | `rollout status`, endpoints, ingress, connection draining | зает Pod, race в routing/probes, недостатъчен grace period |
| `kubectl exec ... sh` fails | `kubectl debug` | distroless/minimal image може да няма shell, това е нормално |

## Лаборатория 1: диагностика без пълен shell

```bash
kubectl -n education get pods -o wide
kubectl -n education describe deployment java-kubernetes-lab
kubectl -n education describe pods -l app=java-kubernetes-lab
kubectl -n education get events --sort-by=.lastTimestamp
kubectl -n education logs deployment/java-kubernetes-lab --all-pods=true --tail=100
kubectl -n education get endpointslices -l kubernetes.io/service-name=java-kubernetes-lab
```

Ако контейнерът се е рестартирал, идентифицирай Pod-а и използвай `kubectl logs POD -c java --previous`. Историческият log може да не е наличен след изтриване на Pod-а; production използва централизирано събиране на logs.

## Лаборатория 2: грешни probes (негативен сценарий)

В локалния Deployment смени readiness path на невалиден (`/wrong-readyz`) в **твоята локална копия** на файла и приложи с `kubectl apply -f`. Pod-овете ще са running, но няма да са Ready. Обърни внимание на `kubectl describe pod`, endpoint slices и `rollout status`.

**Възстановяване:** върни верния `/actuator/health/readiness` и приложи отново. Не тествай тази грешка на жив production service.

- `startupProbe` защитава дълъг startup от преждевременно liveness рестартиране.
- `liveness` **не** проверява Oracle/Redis, за да не предизвика cascading restarts при външен incident.
- `readiness` може да зависи от *необходимите* за обслужване зависимости, но конфигурирай тази зависимост внимателно, защото масово NotReady също е outage.

## Лаборатория 3: graceful shutdown

```bash
# Terminal 1: порт-forward (докато cluster и Service работят):
kubectl -n education port-forward service/java-kubernetes-lab 18080:80
# Terminal 2: long-running, но ограничена само за лабораторията GET:
curl -v 'http://127.0.0.1:18080/api/work?delayMs=5000'
# Terminal 3: инициирай replacement:
kubectl -n education rollout restart deployment/java-kubernetes-lab
kubectl -n education rollout status deployment/java-kubernetes-lab --timeout=240s
```

**Важно:** Port-forward към Service избира конкретен Pod, тунелът може да се прекъсне при неговото изтриване. За по-реалистично zero-downtime доказателство използвай HTTP load generator **през реален Ingress/Gateway** и преброй errors/p95 по време на rollout. Един `curl` през port-forward не доказва production SLO.

`preStop: sleep 5s` (K8s>=1.34), Spring graceful shutdown до 20s и общ pod grace 35s не са три независими бюджета. Kubernetes започва countdown **преди** hook-а. На timeout следва SIGKILL; никакъв Java `finally` не може да бъде гарантиран след това.

## Лаборатория 4: OOMKilled vs Java OOME (не причинявай умишлено node-wide OOM)

```bash
kubectl -n education describe pod <pod-name>
kubectl -n education get pod <pod-name> -o jsonpath='{.status.containerStatuses[0].lastState.terminated.reason}'
kubectl -n education logs <pod-name> --previous
```

Kubernetes `OOMKilled` = container memory (вкл. native/off-heap/thread stacks/heap) е над cgroup limit и kernel прекратява процеса. `java.lang.OutOfMemoryError: Java heap space` е Java изключение, когато GC не успее да освободи достатъчно heap. Тези случаи изискват различна диагностика. Виж [JVM in Containers](../../java/jvm/performance-evolution/JVM-IN-CONTAINERS.md) за `MaxRAMPercentage`, native headroom и безопасен dump path.

## Лаборатория 5: debug ephemeral container (само при разрешен RBAC/Pod Security)

```bash
# Избери pod по резултата от `kubectl -n education get pods`:
kubectl -n education debug -it pod/<pod-name> --image=busybox:1.37 --target=java --profile=restricted -- sh
```

Тази команда може да бъде отказана от `Pod Security restricted`, RBAC или runtime-а. Това е очаквана security boundary, не повод да отключиш `privileged` на цял namespace. `--target` се опитва да сподели process namespace с целевия контейнер (runtime-dependent). Добавеният ephemeral container **остава** в Pod spec-а до изтриване на Pod-а. Не издавай remote production debug разрешения без одобрение.

## Версионна бележка: in-place resize

- **Kubernetes 1.35+**: container-level CPU и memory `requests`/`limits` могат да се променят през `/resize` subresource при constraints (QoS, node policy, restartPolicy, memory decrease и т.н.).
- **Pod-level resources и in-place Pod-level resize са отделни features**; Pod-level resize е beta от 1.36, не го включвай в основния YAML без нужда.
- **Java:** вече стартирал `MaxHeapSize` не се преизчислява автоматично при промяна на cgroup memory limit. Измери с jcmd/flags; при Java workloads често избери restart за memory resizing и validate in-flight safety.

Нямаме автоматичен тест за динамичен resize: проверката се прави върху конкретен Kubernetes/node/runtime/JDK combo, не върху стар или симулиран API.

## Senior code-review checklist

- Не смесваме ли `Pending`, `NotReady`, `CrashLoopBackOff`, `OOMKilled` и `OutOfMemoryError`?
- Тестовете ми работят ли с реални pod endpoints и не зависят от локална изкуствена host network конфигурация?
- Съобразени ли са `preStop + graceful` с termination budget и latency на реалните requests?
- Оставяме ли умишлено writable директории; къде остават heap dumps след Pod delete?
- `kubectl debug` и mirroring ограничени ли са чрез RBAC, audit и owner approval?
- Не пренасяме ли Kubernetes >=1.35 feature към по-стар production cluster?

## Оригинални източници

- [Devoxx Kubernetes for Developers](https://www.youtube.com/watch?v=AxhLQZqZdIg) (SRT), [Production Ready Java Containers](https://www.youtube.com/watch?v=LPhGSgO5eKE) (SRT).
- [Kubernetes Debug Running Pods](https://kubernetes.io/docs/tasks/debug/debug-application/debug-running-pod/).
- [Kubernetes probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/).
- [Kubernetes Pod Lifecycle](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/).
- [Kubernetes container resource resize](https://kubernetes.io/docs/tasks/configure-pod-container/resize-container-resources/).
