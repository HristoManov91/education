# P2 · Homomorphic Encryption — Computing on Encrypted Data (Java 25 + OpenFHE)

> **Devoxx 2026 · Advanced Security · Java 25/JUnit + opt-in Python/OpenFHE**. Източник: [Andrea Iacono — Beyond encryption at rest: computing on encrypted data](https://www.youtube.com/watch?v=ePQyy9sLmsY), предоставен SRT (~50 мин.). **Нашият код е учебна адаптация**, не copy/paste от лектора. Всички данни и коефициенти са синтетични, а не медицински препоръки.

## 1. Реалният казус — encrypted disk, plaintext Java heap

Изпращаме чувствителна информация към външен compute provider. HTTPS/TLS я криптира в транзит, Oracle TDE я защитава на диск, но стандартно Java приложението я декриптира в heap, за да я обработи. Бизнес изискването вече е по-строго: **compute service не трябва да получава plaintext**, дори за едно пресмятане.

**Bad, но правдоподобен подход:** „Имаме TDE + TLS, следователно външният сървър не може да прочете данните.“ И при правилно конфигурирани TLS/TDE, обикновената бизнес логика получава plaintext. Field encryption с ключ, достъпен на същия сървър, не премества trust boundary.

**Good за тесни функции:** клиентът притежава private key, създава контекст, криптира входовете; отделен worker извършва само позволени математически операции върху ciphertext; клиентът декриптира и класифицира. Ако доверена Spring/Oracle услуга или client-side aggregation са достатъчни, **не използвай FHE**.

## 2. Какво научаваме / mental model

- Data **in transit** (TLS), **at rest** (TDE/AES) и **in use**: FHE защитава определени операнди пред *недоверен compute worker*, не „всичката RAM“.
- За поддържаната функция: `Decrypt(secretKey, Eval(ciphertexts)) == f(inputs)`, **когато** криптографските параметри, noise budget и целочисленият домейн са коректни.
- PHE vs somewhat/leveled/FHE, **BFV/BGV точни integers**, **CKKS approximate reals**, **TFHE/FHEW boolean circuits** и bootstrapping. [Подробен преглед](FHE-SCHEMES.md).
- Кога FHE губи от [TEE/MPC/TLS/TDE и client-side aggregation](FHE-TRADEOFFS.md).
- Кое доказват истинските BFV тестове и кое не: mock-нати криптографски операции НЕ откриват грешна декрипция.

**Правило:** FHE решава конкретна confidentiality граница, но не input truthfulness, целостта на резултата, автентикация, audit, целия hardware security model или компрометиран клиент.

## 3. Карта на оригиналната лекция

| Минути | Идея от транскрипта | При нас |
| --- | --- | --- |
| 01–09 | TLS/TDE vs memory; homomorphic addition/multiplication; textbook RSA | Threat model + [схеми](FHE-SCHEMES.md) |
| 09–18 | Learning With Errors, BFV, `t`/`q` и noise | [Java modular bound tests](src/test/java/bg/hristomanov/education/privacy/RiskScoreCalculatorTest.java) |
| 18–22 | Leveled FHE vs скъп bootstrapping | [FHE-SCHEMES](FHE-SCHEMES.md) |
| 22–36 | BFV синтетичен score; client/server; OpenFHE Python | [реален клиент](openfhe-lab/client.py), [worker](openfhe-lab/server.py) |
| 37–39 | Serialization, memory и latency | [разделени измервания](openfhe-lab/README.md) |
| 39–41 | Silent wrong decrypt и oracle testing | [истински BFV differential test](openfhe-lab/test_real_openfhe.py) |
| 41–44 | Branching/comparison и BFV vs CKKS vs TFHE | [Decision guide](FHE-SCHEMES.md) |
| 45–50 | Приложимост, ограничения и доверие в клиента | [Trade-offs](FHE-TRADEOFFS.md) |

## 4. Синтетичен пример — НЕ реален медицински алгоритъм

Лекторът демонстрира претеглена сума върху 6 показатели. От транскрипта са ясни `age=3`, `total_cholesterol=1`, `hdl_cholesterol=-2`. **Останалите тегла тук са наши фикстури**, а не медицинска формула:

```text
score = 3*age + total_cholesterol - 2*hdl_cholesterol
      + 2*systolic_pressure + 75*hypertension + 120*smoker

if score < 500       -> LOW
else if score < 1000 -> MEDIUM
else                 -> HIGH

BFV plaintext modulus t=65537; multiplicative depth=1
```

Baseline: `40*3 + 200 - 60*2 + 115*2 = 430`. Класификацията става **на клиента след декриптиране**, защото encrypted comparison/branching при BFV е сложен.

**Ключова граница:** BFV смята modulo `t`, не върху безкрайни Java integers. За `t=65537` signed centered range е `[-32768, 32768]`, а `-400` може да бъде представено като residue `65137`. [Java input validation](src/main/java/bg/hristomanov/education/privacy/RiskInputs.java) ограничава всички шест полета; [RiskScoreCalculator](src/main/java/bg/hristomanov/education/privacy/RiskScoreCalculator.java) проверява сумата. Консервативната горна граница за сумата от абсолютните членове е `1855`, значително под 32768. Това **не** доказва криптографска сигурност на BFV параметрите — само integer domain correctness за фикстурите.

## 5. README → код → proof

| Какво | Реализация | Доказателство |
| --- | --- | --- |
| Independent Java plaintext oracle | [RiskScoreCalculator.java](src/main/java/bg/hristomanov/education/privacy/RiskScoreCalculator.java) | [JUnit](src/test/java/bg/hristomanov/education/privacy/RiskScoreCalculatorTest.java) |
| Shared cross-language input/expected | [risk-cases.csv](src/test/resources/risk-cases.csv) | 8 cases: `-400`, 499, **500**, 999, **1000** и др. |
| Protocol, hashes/limits/schema | [protocol.py](openfhe-lab/protocol.py) | [8 offline tests](openfhe-lab/test_protocol.py) |
| Client owns secret key, encryption and decrypt | [client.py](openfhe-lab/client.py) | [Real BFV test](openfhe-lab/test_real_openfhe.py) |
| Worker без secret key | [server.py](openfhe-lab/server.py) | [Real BFV test](openfhe-lab/test_real_openfhe.py) |
| Native wheel / budgeted CI | [requirements](openfhe-lab/requirements-ubuntu24.txt) | [path-limited CI](../../../.github/workflows/privacy-fhe-lab.yml) |

Java кодът е **чист изчислителен модел**, без измислени Java FHE bindings. OpenFHE C++ работи през официалните Python bindings в **отделен процес**; няма JNI/FFM неволни ABI рискове.

## 6. Trust boundaries и payload

```text
CLIENT (secret key stays only in client memory)
  validate synthetic inputs -> BFV GenCryptoContext+KeyGen
  -> encrypt 6 packed vectors (8 scores / 8 slots)
        |
        | manifest.json + context.bin + six ciphertext .bin files
        | WITHOUT secret key
        v
SEPARATE PYTHON WORKER PROCESS
  verify files/schema -> Deserialize context and ciphertext
  -> EvalMult(encrypted vector, plaintext weight vector)
  -> EvalAdd(all terms) -> serialized encrypted response
        |
        v
CLIENT decrypts response -> checks score -> classifies LOW/MEDIUM/HIGH
```

**Ограничение:** това е отделен локален процес с file-based transport, **не** HTTP microservice на отделна машина. Няма TLS, network authorization, result signing/attestation, защита срещу злонамерен worker, доказателства за input ranges вътре в ciphertext или гаранции за secure erasure. SHA-256 в manifest открива **случайна** корупция, но **не е автентикация**, защото атакуващ може да подмени и manifest, и данните. Споделеният host е отделна trust boundary.

## 7. Изпълними проверки

**Java 25 / Maven (част от общия reactor):**

```bash
mvn -pl spring/security/privacy-preserving-computation test
```

**Реален OpenFHE на Ubuntu 24.04 + CPython 3.12:**

```bash
cd spring/security/privacy-preserving-computation/openfhe-lab
python3.12 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements-ubuntu24.txt
python -m unittest -v test_protocol       # offline schema negatives
python -m unittest -v test_real_openfhe   # REAL BFV, NO mock/skip
python client.py                          # client -> worker -> decrypt
python benchmark.py --runs 3              # manual exploratory only
```

Виж [платформените инструкции](openfhe-lab/README.md). Wheel `openfhe==1.6.1.0.24.4` е **конкретен Ubuntu 24.04 build**, не универсален pip пакет за Windows/macOS; PyPI показва, че **няма provenance attestation** за файла. Не го инсталираме в общия Java Maven build. Реалният тест в CI трябва да FAIL-не при липсваща FHE библиотека, не да бъде маркиран SKIP.

<details>
<summary>Илюстративен резултат (времената и bytes се различават)</summary>

```text
baseline: plaintext=430, decrypted_BFV=430, label=LOW PASS
medium_exact: plaintext=500, decrypted_BFV=500, label=MEDIUM PASS
negative: plaintext=-400, decrypted_BFV=-400, label=LOW PASS
... 5 additional shared cases ...
timings_ms: context/keygen=<variable>, encrypt=<variable>, worker_roundtrip=<variable>
payload_bytes: request=<variable>, response=<variable>
```
</details>

## 8. Тестове, цена, trade-offs

- **Bad:** `get()` връща ciphertext, следователно декрипцията е правилна. Не: noise/overflow/wrong context може да даде неправилна стойност без exception. Сравнявай декриптиран резултат с независим reference, включително негативни и boundary cases.
- **Bad:** ciphertext size или compute speed е като plaintext. Лекторът показва `0.43 μs` срещу `7.71 ms` (приблизително 18 000×) **само за неговия workload**. Нашият [benchmark](openfhe-lab/benchmark.py) измерва keygen, encrypted batch, serialization, subprocess overhead и decrypt **отделно**, без performance gates.
- **Bad:** FHE позволява произволен Java `if/else` или Oracle SQL върху ciphertext. За BFV сравнения и динамично branching са много по-трудни от линейни суми; не го използвай за near-real-time GPS/SQL filtering без сериозен POC.
- **Bad:** щом client encrypt-ва, данните са истинни. Не: FHE не доказва, че **клиентът е честен**. Validation/zero-knowledge proofs/key policy са отделни задачи.

| Decision contract | Отговор |
| --- | --- |
| **Проблем** | Compute service няма право да види plaintext |
| **Кога** | Малки, фиксирани numeric circuits със силна privacy нужда |
| **Кога НЕ** | Trusted CRUD/Oracle, сложни SQL справки, strict latency/CPU budget |
| **Печалба** | Evaluate без possession на decryption key |
| **Загуба** | CPU, RAM, bytes, математически/parameter complexity, native runtime и verification burden |
| **По-просто** | Client-only aggregation, TLS/TDE/KMS/roles, понякога TEE/MPC |
| **Proof** | [JUnit](src/test/java/bg/hristomanov/education/privacy/RiskScoreCalculatorTest.java) + [protocol negatives](openfhe-lab/test_protocol.py) + [истински BFV](openfhe-lab/test_real_openfhe.py) |

## 9. Връщаме се към казуса

Външният worker вече изчислява ограничена сума, без да получава secret key. Клиентът проверява всеки декриптиран резултат и прави класификацията. Това е **privacy property при конкретно доверие/параметри**, а не production-ready confidential service или медицинска система.

**Code review checklist:** защо FHE, а не TDE? кой притежава secret key? кой вижда plaintext? моделът публичен ли е? bounds/modulus/noise? tests без mock? client/worker authentication? integrity на резултата? metadata leak? memory/CPU/bytes bench? key rotation? native supply chain? rollback?

**Източници:** [Devoxx видео](https://www.youtube.com/watch?v=ePQyy9sLmsY), [OpenFHE Python](https://github.com/openfheorg/openfhe-python), [OpenFHE API](https://openfhe-python.readthedocs.io/en/latest/), [PyPI version](https://pypi.org/project/openfhe/1.6.1.0.24.4/), [STRIDE](../threat-modeling/README.md), [CRA](../../../docs/security/cra/README.md), [FFM](../../../java/jvm/native-interoperability/README.md).
