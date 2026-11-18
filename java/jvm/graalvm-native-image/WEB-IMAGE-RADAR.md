# P3 — GraalVM Web Image / WebAssembly (architecture watch)

> **Не е runnable lab в Education**. Източник: Devoxx 2026 лекцията на Alina Yurenko и Thomas Wuerthinger, приблизително 35:25–40:00, плюс официалната [Web Image документация](https://www.graalvm.org/latest/reference-manual/web-image/).

## Начален казус

Backend екип има Java бизнес код, който би бил полезен в browser (например offline калкулация). Нормален Java JAR не се изпълнява в браузъра. Новото **Web Image** компилира Java bytecode към **WebAssembly module + JavaScript runtime wrapper**.

**Bad:** „Получаваме файл с разширение .wasm и го пускаме като standalone WASI binary във wasmtime или в Kubernetes като Brewlet.“ Това е грешно за текущия GraalVM Web Image runtime: генерираният Wasm очаква imports от JavaScript host и конкретни browser/Node възможности.

**Good:** оценяваме дали code и dependencies са съвместими с JavaScript-hosted runtime, после отделен експеримент и benchmark срещу най-простия JS/TypeScript вариант.

## Какво има в лекцията

- Реален Spring-based music player, компилиран с GraalVM Web Image, демонстриран в браузър. Лекторите показват около **18 MB Wasm** плюс JS wrapper. Това е конкретна сценична демонстрация, **не универсален размер за Spring Boot приложения**.
- Изпълнява се през JavaScript wrapper. Browser или Node.js са host, не общ WASI execution environment.
- Показани са приложения и в браузър, но **не** доказваме, че стандартен Spring Boot REST server автоматично става browser-compatible.

## Минимален технически workflow за бъдещ spike

Официалният [Getting Started with Web Image](https://www.graalvm.org/latest/reference-manual/web-image/) описва:

1. **Oracle GraalVM 25.1+**, Native Image prerequisites и **Binaryen 119+** (команда `wasm-as` на PATH).
2. Компилиране на подходящ Java entrypoint с `native-image --tool:svm-wasm` и версията на build tools, която го поддържа.
3. Доставка на **два runtime артефакта**: JavaScript wrapper и Wasm module.
4. Стартиране през Node.js с необходимите flags за конкретната версия, или browser чрез HTTP server. Текущият `wasmtime some.wasm` не е валиден еквивалент.
5. Compatibility tests: Java APIs, resources, JS interop, heap/memory, startup и browser security policy.

Това са **ръчни указания от официалния guide**, а не твърдение, че имаме успешно построен Wasm в този repo. Отделен genuine build+browser test се добавя само при реален продуктови use case.

## Decision contract

| Въпрос | Решение |
| --- | --- |
| Проблем | Java code трябва да работи в JS-hosted browser/runtime |
| Кога | Споделена проверима Java логика, която има реална бизнес полза offline/на клиента |
| Кога НЕ | Обикновен Spring CRUD backend, WASI CLI без JS runtime, сложни OS/socket API зависимости |
| Печалба | Потенциална повторна употреба на Java алгоритми в JS host |
| Цена | Експериментална технология, build/toolchain, browser/Node compatibility, JS wrapper и size |
| По-просто | TypeScript/browser код или server-side API, ако няма offline requirement |
| Доказателство | Отделен Web Image build, browser/Node integration tests, проверени output and bundle sizes |

## Какво НЕ дублираме

Microsoft **Brewlet** и Kubernetes Java OCI runtime са отделна архитектура. Виж [Brewlet engineering case](../../../docs/case-studies/006-brewlet-java-oci-runtime.md). Те не използват същия JavaScript-hosted Web Image contract.

## Оригинални източници

[Devoxx 2026 GraalVM Next](https://www.youtube.com/watch?v=OLQ98Ko4p3Q), предоставен SRT 35–40 мин; [GraalVM Web Image](https://www.graalvm.org/latest/reference-manual/web-image/), [GraalVM demos — Web Image](https://github.com/graalvm/graalvm-demos).
