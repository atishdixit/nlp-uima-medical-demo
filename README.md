# Medical Chart NLP Demo: Apache UIMA + Spring Boot + MySQL

A small, production-shaped demo that turns a free-text medical chart into structured findings using **one Apache UIMA
pipeline** (typed **JCas** annotations) served by a **Spring Boot 3 / Java 21** REST API. Every rule the pipeline uses
(regexes, trigger terms, vocabulary codes, negation triggers) is stored in **MySQL**, so behaviour changes by editing
rows, with no redeploy.

```
chart text ──► Section ─► Sentence ─► PHI ─► Concept (+codes) ─► Negation ─► Measurement ──► JSON
                 ▲                                ▲
                 └──────── rules cached from MySQL (Caffeine) ────────┘
```

## What it extracts

| Finding | Example in the chart | What you get back |
|---------|----------------------|-------------------|
| **Sections** | `HPI:`, `Medications:`, `Plan:` | name, span, who the section is about (patient / family) |
| **Concepts + vocabulary codes** | `type 2 diabetes mellitus` | ICD-10-CM `E11.9`; also RxNorm (`metformin` → `6809`) and LOINC (`HbA1c` → `4548-4`) |
| **Negation** | `denies fever but reports cough` | `fever`: negated by `denies`; `cough`: affirmed |
| **Vitals, labs, dosages** | `BP 148/92`, `Glucose 182 mg/dL`, `500 mg BID` | type, value, unit |
| **PHI** | MRN, SSN, DOB, phone, e-mail, `Patient:` name | type + span, optionally a redacted copy of the text |

## Quick start

Needs **JDK 21** only (no Docker, no MySQL) for the first look:

```bat
run.bat h2
```

Then open <http://localhost:8080/swagger-ui.html>, or:

```bat
curl -X POST "http://localhost:8080/api/v1/charts/analyze/text?redactPhi=true" ^
     -H "Content-Type: text/plain; charset=UTF-8" --data-binary @samples/soap-note.txt
```

With the real MySQL backend (Docker Desktop):

```bat
run.bat
```

Full instructions, prerequisites and troubleshooting: **[SETUP.md](SETUP.md)** and **[docs/INFRASTRUCTURE.md](docs/INFRASTRUCTURE.md)**.

### Example

Request: `Patient denies fever but reports cough. BP 120/80.`

```json
{
  "textLength": 50,
  "sentenceCount": 2,
  "concepts": [
    { "begin": 15, "end": 20, "text": "fever", "code": "R50.9", "codeSystem": "ICD10CM",
      "preferredName": "Fever, unspecified", "category": "SYMPTOM",
      "negated": true, "negationTrigger": "denies", "experiencer": "PATIENT", "section": "UNSECTIONED" },
    { "begin": 33, "end": 38, "text": "cough", "code": "R05.9", "codeSystem": "ICD10CM",
      "preferredName": "Cough, unspecified", "category": "SYMPTOM",
      "negated": false, "negationTrigger": null, "experiencer": "PATIENT", "section": "UNSECTIONED" }
  ],
  "measurements": [
    { "type": "BLOOD_PRESSURE", "value": "120/80", "unit": "mmHg", "begin": 40, "end": 49,
      "text": "BP 120/80", "rule": "MEAS_BLOOD_PRESSURE" }
  ],
  "phi": [],
  "sections": [],
  "stats": { "durationMs": 113, "rulesLoadedAt": "2026-09-19T05:30:39.283Z", "rulesRejected": 0 }
}
```

All offsets are 0-based indexes into the text you sent.

## API

| Method and path | Purpose |
|-----------------|---------|
| `POST /api/v1/charts/analyze` | Analyse a chart sent as JSON: `{ "text": "...", "redactPhi": false, "includeSentences": false }` |
| `POST /api/v1/charts/analyze/text` | Same, body is `text/plain`; flags are query parameters |
| `GET  /api/v1/rules/summary` | Active rule counts and any **rejected** rules with reasons |
| `POST /api/v1/admin/rules/refresh` | Reload rules from MySQL now (after editing the rule tables) |
| `GET  /actuator/health` | Health |
| `GET  /swagger-ui.html` | Interactive docs (OpenAPI at `/v3/api-docs`) |

Errors are RFC 7807 problem responses: `400` blank/malformed input, `413` chart over `mednlp.max-chars`, `415` unsupported
type, `503` all engines busy or rules could not be reloaded.

## Tech stack

Java 21 · Spring Boot 3.5 · Maven (wrapper included) · **Apache UIMA 3.6** (JCas types generated from a type-system XML) +
uimaFIT · MySQL 8.4 + Flyway + JPA · **Caffeine** cache · Aho-Corasick matcher · springdoc/Swagger UI · JUnit 5 + AssertJ ·
Docker Compose. All open source. Details: [docs/INFRASTRUCTURE.md](docs/INFRASTRUCTURE.md).

## How it is built

- **Six small annotators** in a fixed UIMA aggregate flow: `SectionAnnotator` → `SentenceAnnotator` → `PhiAnnotator` → `ClinicalConceptAnnotator` → `NegationAnnotator` → `MeasurementAnnotator`.
- **Engine pool.** UIMA engines are not thread-safe, so requests lease one of N engine+CAS pairs; a full pool answers `503`.
- **Rules are data.** Compiled once into an immutable `RuleSet`, cached in Caffeine, read per document. Bad rules (invalid or dangerous regex, unknown type) are skipped and reported, never fatal; if MySQL goes away the last good rules keep serving.
- **Safe by design.** Regexes run under a time budget, chart text is never stored, logged or echoed, and the audit table holds counts only.

Diagrams (component view, request flow, rule loading), the data model and the design decisions:
**[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Tests

```bat
run.bat test
```

144 automated tests (unit tests per annotator on the real UIMA pipeline, rule compiler, cache resilience, concurrency,
large documents, and end-to-end HTTP tests on H2 in MySQL mode). Every use case and edge case has an ID and a named test:
**[docs/USE_CASES.md](docs/USE_CASES.md)**.

## Project layout

```
src/main/java/com/example/mednlp
  api/        REST controllers, DTOs, error handling
  service/    ChartAnalysisService: validate, run pipeline, map JCas to DTOs, audit
  uima/       MedicalPipeline (aggregate engine), EnginePool, annotators/
  rules/      RuleSet + compiler, Aho-Corasick TermMatcher, SafeRegex, Caffeine-backed RuleService, seed loader
  domain/     JPA entities and repositories for the rule tables
src/main/resources
  desc/MedicalTypeSystem.xml      UIMA type system (JCas classes are generated from it)
  db/migration/V1__schema.sql     Flyway schema
  seed/medical-rules.json         initial rules, loaded only into empty tables
samples/      example charts (also used as test fixtures)
docs/         ARCHITECTURE.md, INFRASTRUCTURE.md, USE_CASES.md
run.bat  docker-compose.yml  SETUP.md
```

## Limitations (it is a demo)

Negation is lexical (NegEx-style), family history is recognised only by section, there is no temporal/hypothetical
context, the vocabulary is a small **illustrative** subset (verify codes against official releases; SNOMED CT is excluded
because it is licensed), PHI detection is heuristic and **not** HIPAA de-identification, and the admin endpoint has no
authentication. The full list, each pinned by a test, is in [docs/USE_CASES.md](docs/USE_CASES.md#3-known-limitations-lim).
