# P2/P3 Advanced Kubernetes: наблюдаваме, без да усложняваме базовата лаборатория

> Специфични функции от [Alain Regnier — Kubernetes for Developers](https://www.youtube.com/watch?v=AxhLQZqZdIg): local tooling (03:00), in-place resize (11:00), debug pods (27:00), mirrord (33:00), RuntimeClass/gVisor/Kata (36:00), CEL/ValidatingAdmissionPolicy (38:00), OpenTelemetry (42:00). **Това е версия-специфичен материал; не всички технологии са в основния running lab.**

| Тема | Решава | Кога | Защо не по подразбиране |
| --- | --- | --- | --- |
| **Flox/Nix** (транскриптът грешно разпознава „Flux“) | Reproducible local `kubectl`, `kind`, Helm, JDK toolchains | Multi-project teams с version drift | Нов environment manager; `kind` + versioned setup е по-просто за началото |
| `kubectl debug` ephemeral containers | Shell/tools без да вграждаме shell в app image | Diagnosing lean images, staging | RBAC, pod security, оставащ ephemeral container; не е обикновен `kubectl exec` |
| `mirrord` | Насочва/копира cluster network context към local IDE process | Одобрен staging диагностика | Може да обработи реални payloads/secrets; опасно в production |
| `RuntimeClass` — gVisor/Kata | По-силна sandbox изолация от традиционния runtime | Untrusted workloads, multi-tenancy | Kernel/runtime support, incompatibility/overhead, platform ownership |
| CEL `ValidatingAdmissionPolicy` | Cluster-side декларативни правила (non-root, approved registries, min replicas) | Platform policy като Code, без webhook service | Cluster-wide impact и policy versioning; dry-run/deny semantics |
| OpenTelemetry Java agent/SDK | Trace context/spans across services | Имаме реална distributed trace нужда | Agent overhead, sampling, PII, collector/backends; не всеки method трябва да има span |
| **In-place resize** | CPU/memory resource changes без Pod replacement при условия | Server/cluster го поддържат; измерена необходимост | JVM heap остава startup-sized; QoS/node/cgroup ограничения |

## Практически пример: CEL admission policy — концептуален, НЕ apply-ван от CI

В лекцията примерът отказва Deployment с малко реплики в определен namespace. В production policy трябва да има scope, matchConstraints, bindings, exceptions, `Deny/Audit` rollout, unit тестове на CEL expression и план за аварийно изключване. Ако забраниш произволно всички single-replica workloads, можеш да блокираш Jobs/operators/dev tools, които имат законна нужда от тях. Съзнателно **не** включваме такава глобална политика в учебните manifests.

## RuntimeClass и network security

`RuntimeClass` може да избере gVisor/Kata, но **не инсталира** handler-а на node-овете. Не копирай `runtimeClassName: gvisor` в production manifest без платформена поддръжка. `NetworkPolicy` работи само при съвместим CNI; нашият [optional manifest](manifests/40-network-policy.yaml) не се прилага от smoke test поради това. Не използвай резултата от vanilla kind като доказателство за network isolation.

## Observability

Java agent auto-instrumentation може да ускори първия trace experiment, но не гарантира автоматично пълни trace/metrics/logs за *всеки* метод. Въвежда CPU/memory overhead и sensitive data рискове. SDK/manual spans е избор след profiling на реална цел, не задължителна „по-бърза“ опция. Вече имаме [JFR troubleshooting](../../java/jvm/jfr-troubleshooting/README.md) — то отговаря на други въпроси, не е заместител на distributed trace.

## Версионна матрица (проверена към 2026-11)

| Feature | Минимална граница / статус | Отношение към този lab |
| --- | --- | --- |
| Sleep lifecycle hook | Stable K8s 1.34 | `preStop.sleep.seconds:5` е в demo |
| In-place container resources resize | Stable K8s 1.35 | P2 експеримент, не автоматизирано в CI |
| Pod-level resources + resize | Самостоятелни features; Pod-level in-place resize beta 1.36 | Не са в demo |
| Ephemeral debug containers | Stable K8s 1.25 | Ръчно с RBAC / Security профил |
| `kubectl debug` target process namespace | Runtime-dependent | Може `ps` да не показва target процеса |

## Оригинални източници

- [Devoxx Alain Regnier](https://www.youtube.com/watch?v=AxhLQZqZdIg), предоставен транскрипт.
- [Flox](https://flox.dev/) и [flox activate](https://flox.dev/glossary/).
- [Kubernetes RuntimeClass](https://kubernetes.io/docs/concepts/containers/runtime-class/).
- [Kubernetes ValidatingAdmissionPolicy](https://kubernetes.io/docs/reference/access-authn-authz/validating-admission-policy/).
- [Kubernetes resize containers](https://kubernetes.io/docs/tasks/configure-pod-container/resize-container-resources/), [pod-level resize](https://kubernetes.io/docs/tasks/configure-pod-container/resize-pod-resources/).
- [OpenTelemetry Java instrumentation](https://opentelemetry.io/docs/languages/java/instrumentation/).
