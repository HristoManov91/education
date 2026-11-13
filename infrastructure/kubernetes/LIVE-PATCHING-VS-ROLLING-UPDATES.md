# P2 · Live Patching vs Kubernetes Rolling Update — на кой слой е уязвимостта?

> **Operational decision guide**, не инсталираме live patching агент. Източник: [Sven Sellen — Engineering for the Long Haul](https://www.youtube.com/watch?v=Pg5kS8l9CpI), предоставен SRT (~24–28 мин): TuxCare KernelCare/LibCare vendor presentation. „Zero downtime“, „пълно покритие“ и „без нужда от рестарт“ **не са универсални гаранции**. Кодът и командите по-долу стъпват на вече наличния [Kubernetes Java lab](README.md) и [Operations](OPERATIONS.md).

## 1. Казус: имаме CVE, но какво трябва да заменим?

| CVE е в... | Основен patch path | Какво НЕ прави другият подход |
| --- | --- | --- |
| **Linux host kernel** | Проверен, поддържан vendor live patch; или node drain/replace/reboot | Rolling update само на Java Pod не сменя host kernel |
| **Native userspace library** (напр. OpenSSL) | Update + restart на всички засегнати процеси; или *конкретно потвърден* in-memory library patch | Писането на нов `.so` на диск не гарантира, че вече зареденият mapping се е сменил |
| **JDK / JVM** | New tested JDK image → rebuild/deploy → нов Java process | Kernel patch не заменя JDK класовете и VM runtime |
| **Java/Maven dependency JAR** | Dependency upgrade или backport, tests, нов image digest, rolling deployment | KernelCare/LibCare не променят Java classpath |
| **Spring config / auth rule** | Code/config fix, validate, rollout | Live patch няма да замени грешна authorization policy |

**Кога има смисъл host live patch?** Когато конкретният kernel/OS/CVE се поддържа, надеждният supplier е проверен и disruption risk от reboot е сериозен. **Кога не?** Когато CVE е в Java JAR, нямаме доказателство за обхвата на in-memory patch или промяната изисква смяна на kernel ABI/features.

## 2. Нашият реален локален Deployment

[`30-deployment.yaml`](manifests/30-deployment.yaml) има `replicas: 2`, `maxSurge: 1`, `maxUnavailable: 0`, `minReadySeconds: 3`, startup/readiness/liveness probes, `terminationGracePeriodSeconds: 35` и preStop 5 секунди. Това е **добра база за rolling update**, не математическа гаранция за нулево downtime при изчерпана capacity, лош ingress/connection draining или readiness race.

След като стартираш локалния kind lab от [README](README.md):

~~~bash
# Проверки върху локалния education namespace:
kubectl -n education get deploy java-kubernetes-lab \
  -o jsonpath='{.spec.template.spec.containers[0].image}'
kubectl -n education get pods -l app=java-kubernetes-lab -o wide

# Този restart показва rollout behavior, НЕ прилага нов security patch!
kubectl -n education rollout restart deployment/java-kubernetes-lab
kubectl -n education rollout status deployment/java-kubernetes-lab --timeout=240s
kubectl -n education rollout history deployment/java-kubernetes-lab

# Ако има валидна предишна revision, rollback е отделно действие:
# kubectl -n education rollout undo deployment/java-kubernetes-lab
~~~

**Важно:** `rollout restart` може да стартира **същия уязвим image**. За истински security update публикувай и провери **нов immutable digest**, промени Pod template и потвърди, че *новите Pods* реално използват този digest. Локалният `education-kubernetes-lab:dev` е за kind, не шаблон за production pinned images.

## 3. Как доказваме успешен patching deployment?

1. Идентифицирай *адресираната CVE*, host/kernel vs library vs JDK vs application layer.
2. Събери exact artifact identities: image digest, SBOM component PURL, source/patch и runtime JDK/OS.
3. Провери `rollout status`, readiness и действително обслужване на traffic: errors, p95/p99, in-flight requests, shutdown, достатъчно ресурси за `maxSurge`.
4. Изпълни сканиране върху **deployed** artifact, а не просто върху `main`. Свържи със [SBOM/VEX Evidence](../../spring/testing/testcontainers/VEX-BACKPORT-EVIDENCE.md).
5. Разпиши **rollback**: стар image digest, owners, incident procedure. Когато patch е в host kernel, планирай node drain/reboot fallback и проверка след него.

**Кога НЕ:** не инсталирай host live patch agent на production само за учебни цели, не считай vendor „100% CVE coverage“ за доказателство и не смесвай pod availability с kernel version. Дори vendor-supported live patch не елиминира нуждата от нормални OS upgrades и планирани рестартирания.

## 4. Decision contract

**Проблем:** критичен security fix при строг uptime budget.

**Кога:** exact patch target, налична инфраструктура и доказан operational benefit.

**Кога НЕ:** неподдържани live patch комбинации, Java dependency CVE с решение „kernel patch“, няма тестове и rollback.

**Печалби:** по-малко downtime при определени host fixes или управляем Java image rolling update.

**Загуби:** vendor trust, host privilege surface, покритие само за определени fixes, допълнителен rollout/CI burden.

**По-прсто:** upgrade/build/test/rolling deploy за Java; за kernel — безопасен node replacement с drain/PDB/quorum план.

**Proof:** съществуващ [Kubernetes Operations lab](OPERATIONS.md); тук няма добавен host live patch integration test, защото изисква поддържана enterprise OS, privileges и доставчик.

## Източници

- [Sven Sellen — Devoxx 2026](https://www.youtube.com/watch?v=Pg5kS8l9CpI), SRT (~24–28 мин).
- [Kubernetes Rolling Update](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-update-deployment), [Pod Termination](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination).
- [Education Kubernetes lab](README.md), [OSS EOL strategy](../../java/build/reproducible-maven/OPEN-SOURCE-LIFECYCLE-EOL.md).
