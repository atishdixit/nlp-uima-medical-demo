# Setup

How to get the medical chart NLP demo running on Windows. Prerequisites and tool details are in
[docs/INFRASTRUCTURE.md](docs/INFRASTRUCTURE.md).

## Fastest path (about 2 minutes, nothing to install but Java)

```bat
run.bat h2
```

This builds the project and starts it with an in-memory database, so no Docker and no MySQL. Open
<http://localhost:8080/swagger-ui.html>. Data is lost when the app stops.

## Full setup with MySQL

### 1. Install the prerequisites

| Tool | How |
|------|-----|
| **JDK 21+** | `winget install EclipseAdoptium.Temurin.21.JDK` (or any JDK 21+). Then **open a new terminal** and check: `java -version` |
| **Docker Desktop** | `winget install Docker.DockerDesktop`, then start it and wait for "Engine running". *Skip this if you install MySQL natively (see below).* |
| Maven | Not needed. `mvnw.cmd` (included) downloads it on first use. |

`JAVA_HOME` is not required as long as `java` is on `PATH`.

### 2. Get the code

```bat
git clone https://github.com/atishdixit/nlp-uima-medical-demo.git
cd nlp-uima-medical-demo
```

### 3. Run

```bat
run.bat
```

What it does: checks Java 21+ and Docker → `docker compose up -d mysql` → waits until MySQL is *healthy* →
`mvnw spring-boot:run` (compiles, generates the JCas classes, migrates the schema with Flyway, seeds the rule tables).

Ready when you see `Started MedNlpApplication`. First run takes a few minutes (Maven downloads and the MySQL image pull); later runs start in about 15 seconds.

| Command | Effect |
|---------|--------|
| `run.bat` | MySQL in Docker + app |
| `run.bat h2` | In-memory database, no Docker |
| `run.bat test` | Automated tests (no Docker needed) |
| `run.bat db` | Only start MySQL |
| `run.bat stop` | Stop MySQL, keep its data |
| `run.bat reset` | Stop MySQL and **delete its data** (rules are re-seeded on next start) |

> **Port 3306 already in use?** A native MySQL service on this machine is common. Use another host port for the container:
> ```bat
> set MYSQL_PORT=3307
> run.bat
> ```
> The same variable is read by `run.bat`, `docker-compose.yml` and the application, so nothing else changes.

### Using a native MySQL instead of Docker

Create the schema and a user, then start the app with `MYSQL_*` variables (Flyway creates the tables on first start):

```sql
CREATE DATABASE mednlp CHARACTER SET utf8mb4;
CREATE USER 'mednlp'@'%' IDENTIFIED BY 'choose-a-password';
GRANT ALL ON mednlp.* TO 'mednlp'@'%';
```

```bat
set MYSQL_PASSWORD=choose-a-password
mvnw.cmd spring-boot:run
```

## 4. Try it

Open **Swagger UI**: <http://localhost:8080/swagger-ui.html>, or use curl (Windows 10+ has `curl.exe`).

Analyse the bundled sample chart:

```bat
curl -X POST "http://localhost:8080/api/v1/charts/analyze/text?redactPhi=true" ^
     -H "Content-Type: text/plain; charset=UTF-8" --data-binary @samples/soap-note.txt
```

Send JSON from a file (avoids Windows quote-escaping problems):

```bat
curl -X POST http://localhost:8080/api/v1/charts/analyze ^
     -H "Content-Type: application/json" --data-binary @samples/request-example.json
```

Other samples: `samples/negation-cases.txt`, `samples/phi-heavy.txt`, `samples/unsectioned-note.txt`.

Look at the active rules and check health:

```bat
curl http://localhost:8080/api/v1/rules/summary
curl http://localhost:8080/actuator/health
```

## 5. Change the rules without redeploying

Rules live in MySQL. Edit the tables, then reload:

```bat
docker exec -it mednlp-mysql mysql -umednlp -pmednlp mednlp
```

```sql
-- a new concept and its trigger term
INSERT INTO concept (code, code_system, preferred_name, category)
VALUES ('G43.909', 'ICD10CM', 'Migraine, unspecified', 'DIAGNOSIS');
INSERT INTO trigger_term (term, case_sensitive, concept_id)
SELECT 'migraine', FALSE, id FROM concept WHERE code = 'G43.909' AND code_system = 'ICD10CM';

-- a new measurement regex (value = capture group 1)
-- NOTE: in a MySQL string literal every backslash must be doubled
INSERT INTO regex_rule (rule_name, rule_group, category, pattern, value_group, unit)
VALUES ('MEAS_WEIGHT', 'MEASUREMENT', 'WEIGHT', '(?i)\\bweight[ \\t]*[:=]?[ \\t]*(\\d{2,3}(?:\\.\\d)?)', 1, 'kg');

-- switch a rule off
UPDATE trigger_term SET enabled = FALSE WHERE term = 'cough';

-- a negation trigger (types: PRE, POST, PSEUDO, TERMINATION)
INSERT INTO negation_trigger (term, trigger_type) VALUES ('refuses', 'PRE');
```

```bat
curl -X POST http://localhost:8080/api/v1/admin/rules/refresh
```

The response lists any **rejected** rules (invalid regex, unknown type …) with the reason; the rest keep working. Without a
refresh the app picks up changes by itself within `mednlp.rule-cache-ttl` (5 minutes by default).

Table reference: [docs/ARCHITECTURE.md §6](docs/ARCHITECTURE.md#6-data-model-mysql). Seed data: `src/main/resources/seed/medical-rules.json` (loaded **only into empty tables**).

> The `mysql` client prints a backslash as `\\` in query output; the stored value has one backslash.

## 6. Run the tests

```bat
run.bat test
```

144 tests in about a minute; they need no database or Docker (H2 in MySQL mode). What each one proves is listed in
[docs/USE_CASES.md](docs/USE_CASES.md).

## 7. Build a runnable jar

```bat
mvnw.cmd -DskipTests package
java -jar target\nlp-uima-medical-demo-1.0.0.jar
```

Add `--spring.profiles.active=h2` to run it without MySQL.

## 8. IDE tips (IntelliJ IDEA / VS Code)

- Import as a **Maven** project. The JCas classes (`com.example.mednlp.types.*`) are generated into `target/generated-sources/jcasgen` by the first build; if the IDE shows them as missing, run `mvnw.cmd generate-sources` once and reload the Maven project.
- Run `MedNlpApplication` with the environment variable `SPRING_PROFILES_ACTIVE=h2` for a quick start without MySQL.

## 9. Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| `Java 21 or newer is required` | An older JDK is first on `PATH`. Install JDK 21, open a **new** terminal, check `java -version`. |
| `The Docker daemon is not running` | Start Docker Desktop and wait for "Engine running", or use `run.bat h2`. |
| `ports are not available … 3306` | Another MySQL owns the port. `set MYSQL_PORT=3307` and rerun. |
| `Communications link failure` / app cannot connect | MySQL is not up or the port differs. `run.bat db`, then make sure `MYSQL_PORT` is set identically in the shell that starts the app. |
| `Access denied for user 'mednlp'` | The container's volume was created with different credentials. `run.bat reset`, then `run.bat`. |
| `Schema-validation: wrong column type` | The tables were changed by hand. Restore the schema, or `run.bat reset` for a demo database. |
| `Flyway upgrade recommended: MySQL 8.4 is newer…` | A harmless warning; migrations run correctly on 8.4. |
| `Address already in use: 8080` | Set `SERVER_PORT=8081` (or stop the other process). |
| First start is slow | Maven is downloading dependencies and/or Docker is pulling `mysql:8.4`. Only happens once. |
| Behind a corporate proxy | Configure Maven (`~/.m2/settings.xml` proxies) and Docker Desktop proxy settings. |
| Cannot download Maven wrapper | Install Maven 3.9+ and run with `mvn` (`run.bat` falls back to it if `mvnw.cmd` is deleted). |
| A rule "does nothing" | Check `GET /api/v1/rules/summary` → `rejected` for the reason; then `enabled`; then call the refresh endpoint. |
