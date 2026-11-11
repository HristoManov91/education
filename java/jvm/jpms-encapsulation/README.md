# P1 — JPMS: strong encapsulation, `exports`, `opens` и `--add-opens`

> **Ask the Architects** — [Gavin Bierman, Maurizio Cimadamore, Per-Åke Minborg, Viktor Klang, Alan Bateman и други, Devoxx 2026](https://www.youtube.com/watch?v=ewCPBgOrLAE), 38:00–41:30 в предоставения SRT. Архитектите обсъждат practical module-path friction и encapsulation; **Java 25 примери и CLI тестовете са създадени за Education**, не са част от оригиналното видео.

## 1. Реалният проблем: библиотеката работи, но framework reflection получава отказ

Имаме Java библиотека с публичен бизнес API и вътрешен тип, който dependency injection/serialization framework трябва да чете. Нейният `public` клас не означава, че друга named module може свободно да прави дълбока рефлексия върху него. В реален Spring проект често добавяме глобално `--add-opens`, за да „поправим“ грешката, но така разширяваме правата за достъп.

**Цел:** разграничаваме compile-time видимост и runtime deep reflection, без да модуларизираме целия Education Maven reactor.

## 2. README → изпълним код → negative tests

| Правило | Файл | Проверка |
| --- | --- | --- |
| Public API се изнася изрично | [library/module-info.java](src/org.education.jpms.library/module-info.java), [Greetings](src/org.education.jpms.library/org/education/jpms/library/api/Greetings.java) | `PUBLIC=Hello, Education` |
| Internal package не се `exports`-ва | [Vault](src/org.education.jpms.library/org/education/jpms/library/internal/Vault.java), [DeniedCompile](negative/DeniedCompile.java) | **очакван отказ на `javac`**, проверен по диагностиката |
| `opens ... to` дава право само на framework module | [FrameworkReader](src/org.education.jpms.framework/org/education/jpms/framework/FrameworkReader.java) | `FRAMEWORK=demo-only` |
| App **няма** дълбок достъп по подразбиране | [AppMain](src/org.education.jpms.app/org/education/jpms/app/AppMain.java) | `InaccessibleObjectException` → `APP_REFLECTION_DENIED` |
| Временен scoped `--add-opens` променя само runtime достъпа | [run-lab.sh](run-lab.sh) | `APP_REFLECTION_ALLOWED` при explicit flag |

### Изпълнение от root директорията с Java 25

```bash
bash java/jvm/jpms-encapsulation/run-lab.sh
```

Скриптът **не инсталира зависимости**, компилира три реални named modules с `javac --module-source-path`, стартира класовете с `--module-path`, извършва положителните и отрицателните проверки и изпълнява `jdeps --list-deps`. Файловете в `target/jpms-lab` се създават само при локално изпълнение. Windows: използвай WSL/Git Bash + JDK 25 или еквивалентен `javac`/PowerShell сценарий.

```text
org.education.jpms.library
  exports org.education.jpms.library.api
  opens org.education.jpms.library.internal to org.education.jpms.framework

org.education.jpms.app
  requires org.education.jpms.library
  requires org.education.jpms.framework
```

## 3. Какво доказваме и какво НЕ

- `exports` прави публичните типове на пакет достъпни за code linkage от други четящи модули. Не прави private fields достъпни.
- `opens` позволява deep reflection на посочен модул, **не** е еквивалентно на `exports` за нормален Java source import.
- `--add-opens module/package=targetModule` е диагностично/миграционно средство и потенциално security разширение; не го добавяй глобално за `ALL-UNNAMED` без причина.
- `requires` дефинира module readability, не package visibility.
- На classpath всички класове се разглеждат в unnamed module и губим част от тези named module boundaries; **не твърдим**, че JPMS е пълна sandbox за злонамерен код.

## 4. Decision contract

| Проблем | Кога | Кога НЕ | Печалба | Загуба/риск | По-проста алтернатива |
| --- | --- | --- | --- | --- | --- |
| Изолация на стабилна Java библиотека | Поддържани `module-info.java`, ясни public API и runtime requirements | Голям Spring dependency tree с несъвместими JAR-ове без migration plan | По-силен contract, контрол на reflection и service wiring | module-path compatibility, extra build/test матрица | classpath + екипен API contract |
| `opens` към конкретен framework | Легитимна reflection нужда с named consumer module | „Отваряме всичко за всеки“ | Точни минимални права | runtime reflective access изисква maintenance | public factory / direct API |
| `--add-opens` runtime override | Временна migration диагностика със записан owner и срок | Постоянен скрит workaround на security/config проблем | unblock migration | допълнителни права и JVM version debt | коректен `module-info.java` |

**Изход:** разделяме business API от reflection capability, получаваме работещ пример и **доказваме отказ**, вместо да твърдим, че JPMS винаги се конфигурира лесно. Една проблемна транзитивна зависимост все още може да осуети module-path migration. Следващия път първо потърси къде точно липсва `exports`, `opens` или `requires`, преди да променяш флагове.

## Оригинални източници

- [Ask the Architects, Devoxx 2026](https://www.youtube.com/watch?v=ewCPBgOrLAE), предоставен SRT (~38–41 мин).
- [JEP 261 — Module System](https://openjdk.org/jeps/261).
- [JEP 403 — Strongly Encapsulate JDK Internals](https://openjdk.org/jeps/403).
- [Java 25 `Module` API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Module.html).
- [Java 25 `jdeps` command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeps.html).
