# FHE схемите и Noise Budget — PHE, BFV/BGV, CKKS, TFHE

> Следва [основния урок](README.md), като разширява концепциите от лекцията на Andrea Iacono (около 07–22 и 41–44 мин). **Това е теория + decision guide**, а изпълнимият вариант е [BFV lab](openfhe-lab/README.md).

## 1. Какъв проблем решават хомоморфните операции

Ако `E` е encryption, `D` е decryption и `Eval` е допустима операция, искаме `D(sk, Eval(E(pk,x))) == f(x)` при **валидни параметри**. Това е свойство за **correctness**, не декларация, че всички `f` са евтини или че compute provider не вижда никакви metadata.

Лекцията използва textbook RSA като математически пример за multiplicative partially homomorphic схема. **Textbook RSA не е сигурна production encryption**; RSA-OAEP не наследява тази проста algebraic property. Не го предлагаме за реални Java приложения.

## 2. Нива на homomorphic computation

| Ниво | Какво получаваме | Кога | Кога НЕ / цена |
| --- | --- | --- | --- |
| PHE (напр. Paillier) | Ограничени операции, например събиране | Точно един вид aggregate | Не може произволен Java pipeline |
| Somewhat HE | Addition + multiplication, но ограничен noise budget | Ниска известна дълбочина | Дълъг/динамичен circuit |
| Leveled HE | Параметри за предварително зададена multiplicative depth **без bootstrapping** | Линейна претеглена сума като нашата | Неограничени операции |
| Fully HE | Възможно възстановяване на capacity чрез bootstrapping | Privacy-critical дълбоки circuits | Значителни CPU/RAM и parameter costs |

BFV работи с **modular integers** — не с произволно голям `BigInteger`. Plaintext modulus `t=65537` дава centered signed range `[-32768,32768]`. Например `-400` може да се появи като residue `65137`; клиентът тълкува signed encoding. Java референтният тест проверява [това](src/test/java/bg/hristomanov/education/privacy/RiskScoreCalculatorTest.java).

`q` (ciphertext modulus), ring dimension, security level и noise budget са съвместни криптопараметри. Точната коректност **не може да се реши само от `t`**. EvalMult и особено последователните multiplications могат да увеличат noise. Изчерпването му може да се прояви като **тихо неправилна декрипция**, а не задължително exception.

## 3. Bootstrapping

Bootstrapping хомоморфно обновява ciphertext и може да позволи нови последователни операции. Това е съществен криптографски механизъм, **не автоматично ускорение**. Скъп е и зависи от схемата и параметрите. **Нашата сума** `Σ wi*xi` е известен circuit с една ciphertext×plaintext multiplication и последващи sums, затова използваме `SetMultiplicativeDepth(1)` и **без bootstrapping**. При промяна на схемата/версията е нужен нов regression proof.

## 4. Кой алгоритъм да изберем?

| Схема | Представяне | Добра за | Ограничения / алтернатива |
| --- | --- | --- | --- |
| **BFV** | точни signed integers modulo `t` | числа, бройки, integer aggregates | wraparound, сравнението/branching е трудно |
| **BGV** | exact integers с различно управление на noise | други integer circuits | няма обща performance победа над BFV |
| **CKKS** | approximate packed real/complex | ML, статистика с error tolerance | неподходяща за точни финансови cents без строг error budget |
| **TFHE/FHEW** | bit/Boolean/lookup operations | conditional/bit-level logic | не я избирай автоматично за всички аритметични суми |

За нашия пример `LOW/MEDIUM/HIGH` се изчислява от клиента **след** декриптиране. OpenFHE може да комбинира схеми чрез scheme switching, но това има отделен computational overhead, keys и security threat model. Не включваме тежък branch circuit в beginner P2 lab.

## 5. Доказателства и тестове

[Java независим oracle](src/main/java/bg/hristomanov/education/privacy/RiskScoreCalculator.java) + [CSV inputs](src/test/resources/risk-cases.csv) + [истински OpenFHE test](openfhe-lab/test_real_openfhe.py) проверяват точно `-400`, `499/500` и `999/1000`. [Negative transport tests](openfhe-lab/test_protocol.py) откриват tampering, unsupported claimed modulus и missing fields. **Това НЕ е noise exhaustion test**, нито certifying security level; за реална система добави explicit parameter study и независим криптографски одит.

Източници: [OpenFHE](https://openfhe-development.readthedocs.io/en/latest/), [OpenFHE Python](https://github.com/openfheorg/openfhe-python), [Devoxx](https://www.youtube.com/watch?v=ePQyy9sLmsY).
