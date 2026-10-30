# NotebookLM — Reproducible Builds & Build Integrity with Maven (P2)

**Source:** [Maarten Mulders — Deterministic by Design (Devoxx)](https://www.youtube.com/watch?v=9vtYvAI2Dtk).

Използвай оригиналния transcript и този [практически урок](README.md), за да създадеш задълбочено, но кратко техническо аудио на **български език** за Java backend инженер.

Задължително:
- Започни с казуса „същият production Git commit, различен JAR“ и разграничи successful / functionally equivalent / bit-for-bit identical.
- Обясни като decision contract: **проблем; кога използваме; кога НЕ; ползи; загуби; по-проста алтернатива**.
- Покажи защо floating dependencies, непредвидени plugin versions, `outputTimestamp`, generated resources, environment leakage и непочистени директории влияят на резултата.
- Разкажи lab-а: Spring Boot 4.1.2 executable JAR, Java 25, POM property, два clean build-а, SHA-256, `artifact:check-buildplan`, `artifact:compare`, `diffoscope`, отрицателния `demo-nondeterministic` profile, JUnit tests и GitHub Actions.
- Уточни разликата между Maven 3.9 и Maven 3.10+ defaults и ролята на наследения Spring Boot parent/pluginManagement.
- Разграничи **локален сравним build**, **два clean checkout-а на един runner** и **независима third-party reproduction**; не ги представяй като равностойни.
- Не обещавай, че reproducible builds заменят SAST, SBOM, attestation, code signing, runtime configuration или deploy artifact tracking.
- Дай 5 практически въпроса (без предварително да разкриваш отговорите), включително hotfix и build-time secret leak.

Ако източникът твърди нещо без доказателство, кажи го. Дай версиите и ограниченията ясно; не представяй demo или plugin като автоматична защита срещу supply-chain атаки.
