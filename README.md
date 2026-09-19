# Medical Chart NLP Demo: Apache UIMA + Spring Boot + MySQL

A small, production-shaped demo that turns a free-text medical chart into structured findings using **one Apache UIMA
pipeline** (typed **JCas** annotations) served by a **Spring Boot 3 / Java 21** REST API. Every rule the pipeline uses
(regexes, trigger terms, vocabulary codes, negation triggers) is stored in **MySQL**, so behaviour changes by editing
rows, with no redeploy. An **Angular web UI** lets you edit a chart, analyze it and inspect every finding in the browser.

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

Needs **JDK 21** only (no Docker, no MySQL, no Node) for the first look:

```bat
run.bat h2
```

Then open **<http://localhost:8080>**: the UI. (The first run also builds the UI, which takes a couple of minutes; Maven
downloads its own Node for that.) API docs are at <http://localhost:8080/swagger-ui.html>, or use curl:

```bat
curl -X POST "http://localhost:8080/api/v1/charts/analyze/text?redactPhi=true" ^
     -H "Content-Type: text/plain; charset=UTF-8" --data-binary @samples/soap-note.txt
```

With the real MySQL backend (Docker Desktop):

```bat
run.bat
```

Full instructions, prerequisites and troubleshooting: **[SETUP.md](SETUP.md)** and **[docs/INFRASTRUCTURE.md](docs/INFRASTRUCTURE.md)**.

## The web UI

<http://localhost:8080> once the app is running.

- **Edit a chart:** type or paste, load one of four samples, open a `.txt` file, or reopen a chart you saved. Saved charts live in *your browser only* (localStorage); nothing leaves it until you press Analyze. Optional *Analyze as I type*, and `Ctrl+Enter` to analyze.
- **See the findings in place:** affirmed concepts green, **negated concepts red and struck through** (hover for the code and the trigger phrase), vitals / labs / doses underlined, PHI outlined, section headers as margin badges.
- **Inspect the details:** tabs for concepts (filterable, with code, system, category, negation, section), measurements, PHI, sections, sentences, the PHI-redacted text, and raw JSON. Click a table row to jump to that finding in the chart.
- **Know when it is stale:** editing after an analysis flags the results as out of date.
- **Check and reload the rules:** the header shows what rules the backend runs, lists any that were rejected (with the reason), and reloads them from MySQL; the chart on screen is re-analyzed so a rule edit shows up immediately.
- Errors from the backend (blank chart, too long, busy, offline) are shown in plain words.

Rules are still edited in MySQL (see [SETUP.md](SETUP.md#5-change-the-rules-without-redeploying)); the UI checks and reloads them.

UI development with live reload: `run.bat dev` (needs Node 20.19+).

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
Docker Compose · **Angular 21** (standalone components, signals, Vitest) bundled into the jar by Maven. All open source.
Details: [docs/INFRASTRUCTURE.md](docs/INFRASTRUCTURE.md).

## How it is built

- **Six small annotators** in a fixed UIMA aggregate flow: `SectionAnnotator` → `SentenceAnnotator` → `PhiAnnotator` → `ClinicalConceptAnnotator` → `NegationAnnotator` → `MeasurementAnnotator`.
- **Engine pool.** UIMA engines are not thread-safe, so requests lease one of N engine+CAS pairs; a full pool answers `503`.
- **Rules are data.** Compiled once into an immutable `RuleSet`, cached in Caffeine, read per document. Bad rules (invalid or dangerous regex, unknown type) are skipped and reported, never fatal; if MySQL goes away the last good rules keep serving.
- **Safe by design.** Regexes run under a time budget, chart text is never stored, logged or echoed, and the audit table holds counts only.

Diagrams (component view, request flow, rule loading), the data model and the design decisions:
**[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Tests

```bat
run.bat test       rem backend
run.bat uitest     rem Angular UI (needs Node 20.19+)
```

**144 backend tests** (unit tests per annotator on the real UIMA pipeline, rule compiler, cache resilience, concurrency,
large documents, and end-to-end HTTP tests on H2 in MySQL mode) and **71 UI tests** (store, API client, highlighting
engine, chart library, and whole flows through the component tree). Every use case and edge case has an ID and a named test:
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
frontend/     Angular UI (built into the jar by `mvnw -Pui`; see frontend/README.md)
samples/      example charts (test fixtures for the backend and the sample picker in the UI)
docs/         ARCHITECTURE.md, INFRASTRUCTURE.md, USE_CASES.md
run.bat  docker-compose.yml  SETUP.md
```

## Limitations (it is a demo)

The UI reads and reloads rules but does not edit them (use MySQL), and saved charts live in one browser only. Negation is lexical (NegEx-style), family history is recognised only by section, there is no temporal/hypothetical
context, the vocabulary is a small **illustrative** subset (verify codes against official releases; SNOMED CT is excluded
because it is licensed), PHI detection is heuristic and **not** HIPAA de-identification, and the admin endpoint has no
authentication. The full list, each pinned by a test, is in [docs/USE_CASES.md](docs/USE_CASES.md#3-known-limitations-lim).
