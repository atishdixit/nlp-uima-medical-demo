# Infrastructure: prerequisites, tools and technologies

What you need to build, run and test the medical chart NLP demo, and what each piece is used for.
Step-by-step instructions are in [SETUP.md](../SETUP.md).

## 1. Prerequisites

| Requirement | Version | Needed for | Required? |
|-------------|---------|------------|-----------|
| **JDK** | **21 or newer** (Temurin, Oracle, Corretto …) | Compile and run | Yes |
| **Maven** | 3.9+ | Build | **No**: the project ships the Maven Wrapper (`mvnw.cmd`), which downloads Maven 3.9.11 on first use. A system Maven is used only if the wrapper is missing. |
| **Node.js** | 20.19+ or 22.12+ (Angular 21) | UI development (`run.bat dev`, `run.bat uitest`) | **No** for running the app: Maven downloads its own Node (v22.14.0) into `frontend/node` to build the UI |
| **Database** | MySQL 8.x (8.4 LTS used here) | Persistent rule store | Yes for `run.bat`, **no** for `run.bat h2` and for the tests |
| **Docker Desktop** (with Compose v2) | 4.x+ | Easiest way to get MySQL | Optional: you can install MySQL natively instead |
| **Git** | 2.x | Clone / push | Optional |
| **curl** | any (ships with Windows 10+) | Try the API | Optional: Swagger UI works without it |
| Internet access | | First build (Maven Central), first `docker compose up` (Docker Hub) | Yes, once. Afterwards the app runs offline. |

Verified on: Windows 11, JDK 21.0.10, Maven 3.9.14, Node 20.20.2 (system) and 22.14.0 (Maven-managed), Docker 29.3.1, MySQL 8.4 (container).

> **Why Angular 21 and not 22?** Angular 22 requires Node 22.22+/24; Angular 21 supports Node 20.19+, which is what many machines have. Upgrading later is a `frontend/package.json` change plus `frontend.node.version` in `pom.xml`.

### Hardware

| Resource | Minimum | Notes |
|----------|---------|-------|
| RAM | 2 GB free | JVM + the UIMA engine pool (4 engines) + MySQL container |
| Disk | ~2 GB | Maven repository (~300 MB), the `mysql:8.4` image (~800 MB), MySQL volume |
| CPU | 2 cores | Throughput scales with `mednlp.pool-size` |

### Network ports

| Port | Used by | Change with |
|------|---------|-------------|
| 8080 | The application: **UI**, REST, Swagger UI, actuator | `SERVER_PORT` |
| 4200 | Angular dev server (`run.bat dev` only) | `npm start -- --port <n>` |
| 3306 | MySQL (host side of the container mapping) | `MYSQL_PORT` (set it *before* both `run.bat` and the app) |

**Port 3306 is often already taken** by a native MySQL Windows service. If so, run `set MYSQL_PORT=3307` first: `run.bat` and the application both honour it.

## 2. Technology stack

| Layer | Technology | Role | License |
|-------|------------|------|---------|
| Language | Java 21 | Records, pattern-friendly APIs, virtual-thread-ready runtime | GPLv2+CPE (OpenJDK) |
| Framework | Spring Boot 3.5 (Web, Data JPA, Actuator) | REST API, wiring, configuration, health | Apache 2.0 |
| NLP framework | **Apache UIMA 3.6** (`uimaj-core`) | Analysis engines, CAS, **JCas** typed annotations | Apache 2.0 |
| UIMA tooling | uimaFIT 3.6 | Build the aggregate pipeline in code; JCas utilities | Apache 2.0 |
| JCas generation | `jcasgen-maven-plugin` 3.6 | Generates `com.example.mednlp.types.*` from the type-system XML at build time | Apache 2.0 |
| Rule store | **MySQL 8.4** + Connector/J | Regexes, trigger terms, vocabulary codes, negation triggers, audit | GPLv2 (server), GPLv2 + FOSS exception (driver) |
| Migrations | Flyway (Community) | Versioned schema (`V1__schema.sql`) | Apache 2.0 |
| ORM | Spring Data JPA / Hibernate | Rule and audit tables | open source |
| Cache | **Caffeine** | In-process cache of the compiled rule set, refresh-after-write | Apache 2.0 |
| Term matching | `org.ahocorasick:ahocorasick` | Aho-Corasick multi-term matcher | Apache 2.0 |
| API docs | springdoc-openapi | OpenAPI + Swagger UI | Apache 2.0 |
| **Web UI** | **Angular 21** (standalone components, signals, zoneless), TypeScript 5.9, RxJS | Chart editor, annotated results, tables, rules panel | MIT |
| UI build | Angular CLI / `@angular/build` (esbuild), `frontend-maven-plugin` 1.15 | Builds the UI inside the Maven build with a Maven-managed Node | MIT / Apache 2.0 |
| UI tests | Vitest 4 + jsdom via `ng test` | Store, API client, highlighting and component-flow tests | MIT |
| Tests | JUnit 5, AssertJ, Spring Test (MockMvc) | Unit, pipeline and end-to-end tests | EPL 2.0 / Apache 2.0 |
| Test/demo database | H2 (MySQL compatibility mode) | `run.bat h2` and all automated tests: no install needed | MPL 2.0 / EPL 1.0 |
| Containers | Docker Compose | Local MySQL | Apache 2.0 (Compose); see note below |

Everything the application depends on is free and open source. **Note:** Docker *Desktop* has commercial licensing terms for larger organisations; if that applies to you, install MySQL natively (or use another container runtime) and point the app at it with the `MYSQL_*` variables.

## 3. Build tooling

| Tool | Purpose |
|------|---------|
| `mvnw.cmd` / `mvn` | Compile (including JCas generation), test, package, run. `-Pui` also builds the Angular UI and copies it into `target/classes/static`, so the jar serves UI + API |
| `run.bat` | One-command launcher: checks Java and Docker, starts MySQL, waits until it is healthy, builds and runs the app with the UI; also `dev`, `uitest`, `test` |
| `docker-compose.yml` | Defines the `mysql:8.4` service, a named volume and a health check |

Generated code: `target/generated-sources/jcasgen/com/example/mednlp/types/*.java`. In an IDE, import the project as a Maven project (or run `mvnw.cmd generate-sources` once) so those classes resolve.

## 4. Runtime configuration

All settings can be overridden with environment variables or `--mednlp.*` command-line properties.

### Database (`application.yml`)

| Variable | Default | Meaning |
|----------|---------|---------|
| `MYSQL_HOST` | `localhost` | Database host |
| `MYSQL_PORT` | `3306` | Database port |
| `MYSQL_DATABASE` | `mednlp` | Schema name |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `mednlp` / `mednlp` | **Demo credentials**; change for anything shared |
| `SERVER_PORT` | `8080` | HTTP port |

### Pipeline (`mednlp.*`)

| Property | Default | Meaning |
|----------|---------|---------|
| `pool-size` | 4 | Concurrent analyses (one UIMA engine + CAS each) |
| `pool-borrow-timeout-ms` | 5000 | Wait for a free engine before answering 503 |
| `max-chars` | 1000000 | Larger charts → 413 |
| `negation-window-tokens` | 6 | Max tokens between a negation trigger and its concept |
| `regex-timeout-ms` | 2000 | Per rule, per document: stops runaway regexes |
| `rule-cache-ttl` | `PT5M` | Background reload interval for rules (manual refresh is always available) |
| `audit-enabled` | true | Write counts/timings to `processing_run` |
| `seed-enabled` | true | Fill **empty** rule tables from `seed/medical-rules.json` at startup |

Profiles: `h2` → in-memory H2 database, no external services.

## 5. Security and data handling

- Chart text is processed in memory only: it is **not stored**, **not logged**, and **not echoed in error responses**. The audit table stores counts and timings.
- The UI's "Save" keeps charts in the browser's `localStorage` (unencrypted, per browser profile). Charts saved there can contain PHI, so do not use the save feature with real patient data on a shared computer. The UI never uploads saved charts; only Analyze sends text to the API.
- The demo has **no authentication**. `/api/v1/admin/rules/refresh` and the rule tables are unprotected; add Spring Security or a gateway before exposing the service.
- The MySQL credentials in `docker-compose.yml` are for local demo use only.
- PHI detection is heuristic and is **not** a HIPAA de-identification tool.

## 6. Scaling notes (beyond the demo)

| Concern | Option |
|---------|--------|
| More throughput | Raise `mednlp.pool-size` (roughly one per CPU core), add instances behind a load balancer |
| Rules shared across instances | Each instance caches independently; call refresh on each, or add a message-based invalidation. Redis is only needed if you want a shared cache. |
| Bigger vocabulary | Load full ICD-10-CM / RxNorm / LOINC extracts into `concept` / `trigger_term`. Aho-Corasick lookup cost does not grow with vocabulary size; only rule-load time and memory do. |
| Production database | Managed MySQL 8.x, non-default credentials, TLS (`useSSL=true`), a Flyway-only DDL user |
| Observability | Add Micrometer registry (Prometheus) to the existing Actuator |
