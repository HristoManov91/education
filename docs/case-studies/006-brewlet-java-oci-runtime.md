# Case 006 · Brewlet — централизирани JDK runtimes за Java приложения върху Kubernetes

> **Оригинал:** [Bruno Borges, Java on Kubernetes Without Container Images: The WebAssembly Way](https://www.youtube.com/watch?v=fszyRIZXjQM), Devoxx 2026 (00:00–52:49), предоставен SRT; [microsoft/brewlet source project](https://github.com/microsoft/brewlet). **P3 architecture case study, не runnable production lab.** Brewlet е **pre-1.0 preview**, не предлага стабилност/поддръжка по подразбиране. Реалният baseline остава [Kubernetes Java Lab](../../infrastructure/kubernetes/README.md).

## 1. Problem: кой притежава runtime-а?

При 50 Java services с еднаква JDK distribution application teams често притежават `FROM JDK`/base userland, application libraries, Java версията и deploy pipeline. Когато JDK/base image има критичен CVE, трябва да rebuild-нем/retest-нем/redeploy-нем десетки приложения, дори да няма промяна в бизнес кода.

**Ключовият проектен въпрос не е „можем ли да спестим bytes?“, а**: може ли platform team да поеме lifecycle на JDK/runtime отделно от lifecycle на бизнес JAR-ове?

## 2. WebAssembly-inspired — какво всъщност работи

WebAssembly проекти като SpinKube използват portable Wasm module в OCI registry и node-managed Wasm runtime. Brewlet **заема този deployment принцип**, но **Java приложението не се компилира до WebAssembly**: пак изпълнява JVM bytecode на истинска Java Virtual Machine.

```text
Application team                       Platform team
--------------------------------     ---------------------------------
Spring Boot JAR                       NodeProfile: approved JDKs/digests
Maven plugin / CLI                     Node provisioner + admission webhook
launch metadata                       containerd shim + runtimeClass
   |                                       |
   v                                       v
OCI registry — application artifact      Brewlet-enabled Kubernetes nodes
          \                               /
           \                             /
            ---> Kubernetes JavaApplication CRD
                 + runtimeClassName: brewlet
                      |
                      v
                  containerd shim assembles OCI bundle
                      |
                      v
                  JVM process on node-managed JDK
                  (still cgroups/namespaces/Pod semantics)
```

Тук OCI artifact пази JAR, dependency layers и launch configuration; **не е традиционен container image с JDK/OS base за всяко приложение**. Node provisioner зарежда одобрени JDK roots/userland образи; това **не** означава, че Kubernetes работи без никаква container/runtime технология — containerd, namespaces, cgroups, OCI bundle и Linux node продължават да участват.

## 3. Какво лекторът действително показва (и какво не доказва)

- Maven/CLI публикува Java JAR, dependencies и JVM launch metadata като OCI artifact; inspect показва отделни layers (около 35–41 мин).
- Node profile конфигурира runtime inventory — одобрени JDK 25/27 distributions, architectures и съвместимост.
- Spring Boot приложение работи с node-managed JDK 25; лекторът променя JDK profile към 27, след което **Pod-овете се заменят**, а application OCI artifact digest остава същият (42–46 мин).
- Демонстрацията показва **отделен release lifecycle**, не hot swapping на JDK в съществуващ JVM процес.
- На въпрос за platform-managed dependency bundles лекторът изрично казва, че **не е тествал live patch/replace на тези dependency layers** (52-ра мин). Не записвай тази възможност като production факт.
- За приложения с `fontconfig`, certificates и други native/userland зависимости node-managed JDK runtime image трябва да ги съдържа; обратното не е „прозрачна“ оптимизация.

## 4. Trade-offs / сравнение

| Подход | Кой rebuild-ва image при JDK CVE | Кога е подходящ | Цена / ограничения |
| --- | --- | --- | --- |
| Обикновен app container + pinned digest | Application release pipeline, често централно автоматизиран | **Default** за повечето teams, cloud portability | Image rebuild/retest/deploy на приложенията |
| Golden base image + Renovate/SBOM | Platform поддържа base, app pipeline rebuild-ва | Production governance със стандартни OCI images | Patch propagation зависи от app adoption/CI |
| Buildpacks/Jib | Build system генерира готов image | Developer convenience; layering | Крайният deploy artifact остава container image |
| **Brewlet pre-1.0** | Platform поддържа node runtimes; apps дават OCI JAR artifact | Experiment на изолирани cluster/node pools, много Java workloads | Custom shim/operator/CRDs, node mutations, scheduling/rollback/security/migration complexity |

**Ползи, ако бъдат потвърдени:** по-бързо fleet-wide runtime patching, централен compliance/policy, по-малко app-layer rebuilds. **Не е доказано от една демонстрация:** по-малка сума CPU/RSS, универсално по-ниска latency, production operability, равна или по-добра security. Споделено platform ownership също концентрира blast radius — грешен node JDK upgrade може да засегне много services.

## 5. Senior decision gate — защо P3, а не P1

- Поддържаме ли containerd 2+, cgroup v2, privileged node provisioner и dedicated node pool? Ако не, **не внедряваме**.
- Има ли надежден owner за `NodeProfile`, JDK compatibility matrix, restore/rollback, CVE SLA, node draining, cluster upgrades?
- Как доказваме cryptographic identity на OCI artifacts, supply-chain provenance, registry authentication и rollout auditing?
- Кой тества JDBC/ORM, JNI/FFM, TLS, agents, JFR и dependencies с **всяка одобрена JDK patch**?
- Как сравняваме в реален fleet разходите за node patch/operations спрямо pinned/golden images, а не само размера на една OCI layer?

**Изход:** записваме подхода като case study за платформена архитектура и прехвърляне на ownership. Не добавяме Brewlet admission/operator/provisioner към основния `kind` lab, за да остане локалното обучение лесно и безопасно.

## Оригинални източници

- [Devoxx, Bruno Borges](https://www.youtube.com/watch?v=fszyRIZXjQM), предоставен SRT.
- [Microsoft Brewlet — README, status, implementation, operators/CLI/specs](https://github.com/microsoft/brewlet).
- [Brewlet roadmap](https://github.com/microsoft/brewlet/blob/main/ROADMAP.md).
- [Kubernetes RuntimeClass](https://kubernetes.io/docs/concepts/containers/runtime-class/).
- [OCI Image/Artifact Spec](https://github.com/opencontainers/image-spec).
