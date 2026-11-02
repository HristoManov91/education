# NotebookLM P1 — Big Runtimes Make Efficient Programs

Източници: [Ron Pressler — Devoxx 2026](https://www.youtube.com/watch?v=vhDwy1eCXbs) и [практическия Education урок](BIG-RUNTIMES-JIT-ABSTRACTIONS.md). Транскриптът е автоматично генериран с Gemini; не интерпретирай сгрешени думи като официални технически термини.

## Готов prompt — 30–40 минути български technical audio

Създай разговорна, технически точна и много конкретна 30–40-минутна аудио лекция на български между двама инженери. Аудитория: Java 25/Spring Boot backend Mid+ → Early Senior. Входният казус е голям backend с 10 вида протоколи. Колега предлага да махне интерфейсите и да pool-ва DTO-та „за скорост“.

Следвай това съдържание:
1. 4 мин: какво означава performance на програма, която можем да еволюираме; ограничение на математическото „съществува по-бърз код“.
2. 8 мин: abstraction vs specialization, virtual calls, C++ monomorphization и защо специализацията може да е viral по call chain.
3. 8 мин: HotSpot runtime type profiling, speculative guarded inlining, deoptimization, JIT warm-up; кога оптимизацията може да се провали.
4. 7 мин: generational moving GC, TLAB, write barriers, locality, CPU/RAM/heap; защо обикновеният object pool може да влоши нещата и защо това не е универсален закон.
5. 7 мин: JUnit ShapeDispatchTest/MutableEventTest, JMH ShapeDispatchBenchmark/AllocationReuseBenchmark -prof gc, JFR и Spring Boot HTTP endpoint с *един и същ core метод*. Обясни защо ns/op не означава HTTP p99.
6. 4 мин: engineering decision contract за всяко решение: проблем, кога използваме, кога НЕ, ползи, загуби, по-проста алтернатива, code/tests, измерима хипотеза.

Не измисляй benchmark числа или гарантиран deopt. Изрично разграничи Java 25 runnable код от JDK 27 промени и JDK 28 JEP 401 preview. Не повтаряй вече съществуващия Leyden/JFR курс. Финализирай с checklist и 3 senior-level проверочни въпроса.
