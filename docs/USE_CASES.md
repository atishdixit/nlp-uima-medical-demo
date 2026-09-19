# Use cases and edge cases

Reference catalog of what the service is expected to do, how it behaves at the edges, and **which automated test proves it**.
Every ID below appears in the test sources, so `grep -r "EC-15" src/test` finds the proof.

- Backend: `run.bat test` (or `mvnw.cmd test`). 144 tests, no database or Docker needed (H2 in MySQL mode).
- UI: `run.bat uitest` (needs Node 20.19+). 71 tests (Vitest); see [section 4](#4-ui-use-cases-angular) for the UI use cases.
- Sample charts live in [`samples/`](../samples) and are also used as test fixtures.
- Test names are `Class#method`; all classes are under `src/test/java/com/example/mednlp/`.

Legend: **UC** = use case, **EC** = edge case, **LIM** = documented limitation.

---

## 1. Use cases

| ID | Use case | Expected behaviour | Proven by |
|----|----------|--------------------|-----------|
| UC-01 | Analyse a complete SOAP-style chart | Sections, coded concepts, negation, measurements and PHI are all returned in one response | `api.ChartApiTest#soapNoteEndToEnd`, `#conceptOffsetsPointAtTheOriginalText` |
| UC-02 | Detect chart sections | `CC:`, `HPI:`, `PMH:`, `Medications:`, `Allergies:`, `Family History:`, `Vitals:`, `Labs:`, `Assessment:` / `A/P:`, `Plan:` are recognised, case-insensitively; a section ends at the next header | `uima.annotators.SectionAnnotatorTest#detectsSectionsAndTheirExtent`, `#headersAreCaseInsensitiveAndTolerateSpacesAndIndentation`, `#assessmentAliasesMapToOneSection` |
| UC-03 | Map text to vocabulary codes | `hypertension`→ICD-10-CM `I10`, `metformin`→RxNorm `6809`, `hemoglobin a1c`→LOINC `4548-4`; each concept carries system, preferred name, category and section | `ClinicalConceptAnnotatorTest#mapsTermsToIcd10RxNormAndLoincCodes` |
| UC-04 | Negation by a preceding trigger | `denies chest pain`, `No fever, cough or headache`, `without fever or cough`, `No evidence of pneumonia`: the concept is `negated` and `negationTrigger` names the phrase (the longest trigger wins) | `NegationAnnotatorTest#preTriggerNegatesTheFollowingConcept`, `#oneTriggerNegatesAWholeList`, `#longestTriggerWins`, `#withoutNegatesLists` |
| UC-05 | Negation by a following trigger | `Pneumonia was ruled out`, `Troponin was negative` | `NegationAnnotatorTest#postTriggerNegatesThePrecedingConcept`, `#postTriggerDoesNotNegateAConceptThatFollowsIt` |
| UC-06 | Negation scope ends at a terminator | `Denies shortness of breath but has chest pain` → only the first is negated; `Denies fever and reports cough` → cough stays affirmed | `NegationAnnotatorTest#terminationTermStopsTheScope`, `#reportsActsAsATerminator` |
| UC-07 | Pseudo-negation is not negation | `No increase in cough`, `Not certain about pneumonia`, `Pneumonia is not ruled out`, `Cannot rule out pneumonia`, `No change in fever` stay affirmed | `NegationAnnotatorTest#pseudoNegationDoesNotNegate` |
| UC-08 | Extract vital signs | `BP 148/92 mmHg`, `HR 88`, `RR 18`, `Temp 98.6`, `SpO2 97%` → typed measurements with value and unit; label variants (`Pulse:`, `heart rate=70`, `blood pressure: 120 / 80`) | `uima.annotators.MeasurementAnnotatorTest#extractsVitalSigns`, `#vitalLabelVariants`, `#repeatedReadingsAreAllReported` |
| UC-09 | Extract dosages | `500 mg BID`, `10 mg PO daily`, `2.5 mg`, `10 units qhs` | `MeasurementAnnotatorTest#extractsDosagesWithRouteAndFrequency` |
| UC-10 | Extract lab values | `Glucose 182 mg/dL`, `HbA1c 8.1%`, `Troponin 0.01 ng/mL` keep their units | `MeasurementAnnotatorTest#labValuesKeepTheirUnitsAndAreNotMistakenForDosages` |
| UC-11 | Detect PHI | MRN, SSN, DOB, e-mail, phone (three formats), patient name after a `Patient:` label | `uima.annotators.PhiAnnotatorTest#detectsIdentifiers`, `#detectsPhoneNumberFormats`, `#detectsPatientNamesAfterALabel`, `#samplePhiChartIsFullyDetected` |
| UC-12 | Redact PHI | With `redactPhi=true` the response includes text with PHI replaced by `[MRN]`, `[PHONE]`… | `api.ChartApiTest#phiRedaction`, `#phiHeavyChart` |
| UC-13 | Family-history findings are not the patient's | Concepts inside `Family History:` get `experiencer = FAMILY` | `SectionAnnotatorTest#familyHistorySectionMarksFindingsAsFamily`, `ChartApiTest#soapNoteEndToEnd` |
| UC-14 | Change behaviour by editing MySQL rows | New terms, disabled terms/concepts, edited regexes and new negation triggers take effect after `POST /api/v1/admin/rules/refresh`, not before | `api.RuleRefreshTest#aNewTermIsVisibleOnlyAfterRefresh`, `#disablingATermRemovesItAfterRefresh`, `#disablingAConceptRemovesAllItsTerms`, `#disablingARegexRuleTakesEffect`, `#editingARegexPatternTakesEffect`, `#addingANegationTriggerChangesBehaviourAfterRefresh` |
| UC-15 | Audit trail | Each analysis writes a row with character, concept, negated and PHI counts and the duration, **never the text** | `ChartApiTest#everyAnalysisWritesAnAuditRowWithoutChartText` |
| UC-16 | Submit a chart as plain text | `POST /api/v1/charts/analyze/text` accepts `text/plain` (UTF-8) with `redactPhi` / `includeSentences` query flags | `ChartApiTest#plainTextEndpoint` |
| UC-17 | Operate and inspect | `/api/v1/rules/summary`, `/actuator/health`, `/v3/api-docs` and Swagger UI | `ChartApiTest#ruleSummaryDescribesTheActiveRules`, `#healthAndOpenApiEndpointsAreAvailable` |

Sample charts and what they exercise:

| File | Exercises |
|------|-----------|
| `samples/soap-note.txt` | UC-01..UC-13: every section, all three vocabularies, negation in HPI/Plan, family history, vitals, labs, dosages, PHI |
| `samples/negation-cases.txt` | UC-04..UC-07 in one file (ten lines, asserted line by line in `ChartApiTest#negationCasesChart`) |
| `samples/phi-heavy.txt` | UC-11/UC-12: name, MRN, DOB, SSN, e-mail and three phone formats |
| `samples/unsectioned-note.txt` | EC-08, EC-12: no headers, irregular spacing, a concept split across lines |

---

## 2. Edge cases

### Input handling and API

| ID | Edge case | Expected behaviour | Proven by |
|----|-----------|--------------------|-----------|
| EC-01 | Empty, whitespace-only, missing or `null` text | `400` Problem+JSON: "The chart text must not be empty" | `ChartApiTest#blankAndMissingTextAreRejectedWith400` |
| EC-02 | Malformed JSON, non-object JSON, empty body (JSON or plain text) | `400` | `ChartApiTest#malformedOrEmptyBodiesAreRejectedWith400` |
| EC-03 | Unsupported content type / wrong HTTP method | `415` / `405` | `ChartApiTest#unsupportedContentTypeAndMethodAreRejected` |
| EC-04 | Chart longer than `mednlp.max-chars` | `413`, message states both sizes | `api.ChartLimitsTest#aChartOverTheLimitIsRejectedWith413` |
| EC-05 | Chart exactly at the limit | Accepted | `ChartLimitsTest#aChartExactlyAtTheLimitIsAccepted` |
| EC-06 | Unicode: accents, em dashes, emoji (surrogate pairs), Turkish `İ` | Offsets stay valid: `text.substring(begin, end)` equals the reported text | `ClinicalConceptAnnotatorTest#unicodeTextKeepsOffsetsAligned`, `ChartApiTest#utf8TextKeepsOffsetsConsistent` |
| EC-07 | Windows `\r\n` line endings | Sections, sentences and negation behave as with `\n` | `SectionAnnotatorTest#windowsLineEndingsAreSupported`, `SentenceAnnotatorTest#handlesWindowsLineEndings`, `ChartApiTest#windowsLineEndings` |
| EC-30 | Chart with nothing to find | `200` with empty arrays (never `null`) | `ChartApiTest#chartWithoutAnyFindingsReturnsEmptyListsNotNulls` |
| EC-31 | Error responses | Never echo or log the submitted chart (PHI) | `ChartApiTest#errorResponsesDoNotEchoTheChartText` |
| EC-33 | Same chart submitted repeatedly | Identical result every time, so no annotations leak between requests through reused engines/CAS | `ChartApiTest#theSameChartAlwaysGivesTheSameResult`, `ClinicalConceptAnnotatorTest#repeatedRunsOnTheSameEngineDoNotLeakAnnotations` |

### Text matching and structure

| ID | Edge case | Expected behaviour | Proven by |
|----|-----------|--------------------|-----------|
| EC-08 | Double spaces, tabs, line breaks or non-breaking spaces inside a term (`chest    pain`, `shortness\nof breath`) | Still matched; offsets and covered text refer to the original text | `ClinicalConceptAnnotatorTest#toleratesIrregularWhitespaceAndReportsOriginalOffsets`, `#nonBreakingSpaceIsTreatedAsWhitespace`, `rules.NormalizedTextTest` |
| EC-09 | Overlapping terms (`type 2 diabetes mellitus` ⊃ `diabetes mellitus` ⊃ `diabetes`) | Longest wins; only one concept | `ClinicalConceptAnnotatorTest#longestMatchWins`, `rules.TermMatcherTest#prefersTheLongestOverlappingTerm` |
| EC-10 | Abbreviations flagged case-sensitive (`MI` vs `mi`, `Mi`) | Only the exact case matches | `ClinicalConceptAnnotatorTest#abbreviationsMarkedCaseSensitiveOnlyMatchExactCase` |
| EC-11 | Term inside a longer word (`Coughing`, `Prehypertension`, `HTN2`, `Known`, `notes`, `nose`) | Not matched, for concepts and for negation triggers | `ClinicalConceptAnnotatorTest#matchesMustSitOnWordBoundaries`, `NegationAnnotatorTest#triggersInsideOtherWordsAreIgnored`, `TermMatcherTest#wordBoundariesAreEnforcedOnBothSides` |
| EC-12 | No section headers; header not at line start (`The plan: …`) | No sections; concepts report `UNSECTIONED` | `SectionAnnotatorTest#textBeforeFirstHeaderIsUnsectioned`, `#headerMustStartTheLine`, `ChartApiTest#unsectionedFreeTextChart` |
| EC-13 | Repeated headers; two rules matching the same header | Separate sections; higher `priority` wins | `SectionAnnotatorTest#repeatedHeadersProduceSeparateSections`, `#whenTwoRulesMatchTheSameHeaderTheHigherPriorityWins` |
| EC-14 | Sentence traps: `98.6`, `Dr. Smith`, `J. Smith`, `1. Aspirin`, `98.6 F. BP…`, `5 mg. Continue`, lower-case after a period, `;`, blank lines, punctuation-only lines | Split or not split correctly | `uima.annotators.SentenceAnnotatorTest` (13 tests) |
| EC-27 | `110 mg/dL` next to a dosage rule | `mg/dL` is a lab unit, not a dosage | `MeasurementAnnotatorTest#aBareMgPerDlIsNotADosage`, `#labValuesKeepTheirUnitsAndAreNotMistakenForDosages` |
| EC-28 | Overlapping PHI matches; look-alikes (`1234-56-7890`, `2024-01-15`, trailing `.` after an e-mail) | One span, the longest; no false positives | `PhiAnnotatorTest#overlappingMatchesAreReportedOnceUsingTheLongest`, `#doesNotFlagClinicalNumbers`, `#trailingPunctuationIsNotPartOfTheEmail` |

### Negation

| ID | Edge case | Expected behaviour | Proven by |
|----|-----------|--------------------|-----------|
| EC-15 | Negation must not cross sentence, semicolon or line boundaries | `Denies fever. Cough is present.` → cough affirmed | `NegationAnnotatorTest#negationDoesNotLeakIntoTheNextSentence`, `#semicolonAndNewlineLimitTheScope` |
| EC-16 | Window boundary (`mednlp.negation-window-tokens`, default 6) | 6 tokens between trigger and concept → negated; 7 → not | `NegationAnnotatorTest#windowBoundary_sixTokensNegates_sevenDoesNot` |
| EC-17 | A trigger word that is part of a concept term (`no known allergies`) | Ignored as a trigger | `NegationAnnotatorTest#aTriggerInsideAConceptTermIsNotATrigger` |
| — | Upper-case text, medication refusal | `DENIES CHEST PAIN` negated; `not taking metformin` reported negated | `NegationAnnotatorTest#triggersAreCaseInsensitive`, `#notTakingAMedicationIsTreatedAsNegated` |

### Rules, database and operations

| ID | Edge case | Expected behaviour | Proven by |
|----|-----------|--------------------|-----------|
| EC-18 | Invalid regex in MySQL | Rule skipped, reason listed in `rejected`; all other rules keep working | `rules.RuleSetCompilerTest#invalidRegexIsRejectedNotFatal`, `RuleRefreshTest#badRulesInTheDatabaseAreRejectedAndReportedWhileEverythingElseKeepsWorking` |
| EC-19 | Catastrophic-backtracking shapes (`(a+)+$`, `(\w*)*x`, `(a+){2,}`) | Rejected at load time; bounded nesting such as `(…){1,2}` is allowed | `RuleSetCompilerTest#nestedUnboundedQuantifiersAreRejected`, `#boundedNestingIsAllowed` |
| EC-20 | A slow regex the static check cannot see (`(.*a){20}b`) | Cut off by `mednlp.regex-timeout-ms`; that rule is skipped for that document only, the rest of the chart is still processed | `rules.SafeRegexTest#abortsARunawayPatternWithinItsBudget`, `MeasurementAnnotatorTest#aSlowRegexIsSkippedWithoutBreakingTheRestOfTheDocument` |
| EC-21 | Disabled rows (`enabled = FALSE`) | Ignored silently, not reported as errors | `RuleSetCompilerTest#disabledRulesAreIgnoredSilently`, `RuleRefreshTest#disabling*` |
| EC-22 | Empty/oversized/`NULL` patterns, unknown rule group, bad `value_group`, unknown negation type, missing concept code, over-long term | Rejected with a reason | `RuleSetCompilerTest` (`emptyBlankAndOversizedPatternsAreRejected`, `unknownGroupAndBadValueGroupAreRejected`, `invalidNegationTriggersAreRejected`, `conceptsNeedACodeAndTermsNeedContent`) |
| EC-23 | MySQL becomes unreachable after startup | The last good rules keep serving; refresh reports `503`; startup itself fails fast if rules cannot be loaded | `rules.RuleServiceTest#keepsServingTheLastGoodRulesWhenAReloadFails`, `#startupFailsFastWhenRulesCannotBeLoaded` |
| EC-24 | 16 threads × 25 requests over 4 pooled engines | Every result identical to the single-threaded baseline | `api.ConcurrencyTest#parallelRequestsGetIsolatedAndCorrectResults` |
| EC-25 | All engines busy | Waits up to `pool-borrow-timeout-ms`, then `503`; pool recovers afterwards | `uima.EnginePoolTest#failsFastWithEngineBusyWhenEveryEngineIsLeased` |
| EC-26 | A chart of about 1 MB (14k lines) and a 300 KB line without punctuation | Correct counts, well under the time budget | `uima.LargeDocumentTest` |
| EC-29 | Regex that can match the empty string (`a*`) | Zero-length matches are ignored | `SafeRegexTest#zeroLengthMatchesAreSkipped` |
| EC-32 | Empty rule tables and seeding disabled | Application starts; every chart returns empty findings | `api.EmptyRulesTest` |
| EC-34 | Two concepts sharing a term | The database rejects a duplicate; in-memory the first wins | `RuleRefreshTest#theDatabaseRejectsDuplicateTriggerTerms`, `RuleSetCompilerTest#whenTwoConceptsShareATermTheFirstOneWins` |
| EC-35 | Restart after an operator edited the rules | Seeding never overwrites populated tables | `RuleRefreshTest#seedingNeverOverwritesOperatorEdits` |
| EC-36 | An analysis fails half-way | The engine is replaced; the pool keeps working | `EnginePoolTest#anExtractorFailureDoesNotPoisonThePool` |

---

## 3. Known limitations (LIM)

These are pinned by `uima.annotators.KnownLimitationsTest`, which asserts *today's* behaviour. If you improve one, that test
fails on purpose: update the test and this table together.

| ID | Limitation | Example | Consequence |
|----|------------|---------|-------------|
| LIM-01 | Negation is lexical, not syntactic | `No fever, has cough.` | `cough` is reported negated (inside the window, no terminator) |
| LIM-02 | Family history is recognised only by section | `HPI: Mother had diabetes.` | attributed to the patient |
| LIM-03 | Allergies are not distinguished | `Allergies: aspirin` | extracted as a medication; read the `section` field |
| LIM-04 | No hypothetical / temporal context | `Rule out pneumonia. History of asthma.` | both reported as plain affirmed concepts |
| LIM-05 | Sentences are line-based | prose hard-wrapped over two lines | split into two sentences |
| LIM-06 | Names are found only after a `Patient:` label | `John Smith was seen today.` | not flagged as PHI |

Also see [ARCHITECTURE.md §9](ARCHITECTURE.md#9-known-limitations) for the non-observable ones (illustrative vocabulary,
unauthenticated admin endpoint, per-instance cache).

---

## 4. UI use cases (Angular)

The UI (`frontend/`) is exercised in two ways: Vitest specs drive the real component tree with a mocked HTTP backend
(`app.spec.ts` for whole flows, the others for units), and the finished app was also checked by hand in a browser against the real
backend. Spec files are under `frontend/src/app/`.

| ID | Use case | Expected behaviour | Proven by |
|----|----------|--------------------|-----------|
| UI-01 | Edit a chart and analyze it | Analyze is disabled for empty text; the request is sent with redaction and sentences switched on; the summary shows counts and timing | `app.spec.ts` (`starts with an empty editor…`, `enables Analyze once there is text…`, `analyzes the chart and shows the summary…`), `analysis.store.spec.ts` (`sends the chart with redaction and sentences…`) |
| UI-02 | See findings in place | Affirmed concepts green, negated concepts red and struck through with the trigger in the tooltip, measurements underlined, PHI outlined, section headers as margin badges; overlaps (a concept inside a measurement) are painted together | `highlight.spec.ts`, `app.spec.ts#analyzes the chart and shows the summary and the highlighted findings` |
| UI-03 | Inspect findings as tables | Concepts (code, system, status, trigger, section, about), measurements, PHI text, sections, sentences, redacted text and raw JSON, each with a friendly empty state; concepts can be filtered by status and free text | `app.spec.ts` (`lists concepts in a table…`, `filters the concept table…`, `shows measurements, and friendly empty states…`, `shows the PHI text found and the redacted copy`, `shows the raw JSON`) |
| UI-04 | Jump from a table row to the chart | The row's finding is selected in the annotated chart and scrolled into view | `app.spec.ts#jumps to the chart and marks the finding…` |
| UI-05 | Know when results are out of date | Editing after an analysis shows a warning; the highlights keep matching the text that was analysed; re-analyzing clears it | `analysis.store.spec.ts#marks the results stale…`, `app.spec.ts#warns that the results are stale…` |
| UI-06 | Analyze as I type | Off by default; a burst of typing produces one request after a 700 ms pause; nothing is sent for blank text or text already analysed | `analysis.store.spec.ts` (`analyze as I type`) |
| UI-07 | Keep my own charts | Save, update, save-as-new, open and delete charts in the browser's localStorage; nothing is sent to the server | `saved-charts.service.spec.ts`, `app.spec.ts#saves a chart in this browser, lists it, and can delete it again` |
| UI-08 | Load a chart from a sample or a file | Four bundled samples (the repository's `samples/` folder, so the UI, tests and docs share one copy); Windows line endings are normalised | `api.service.spec.ts` (`fetches sample charts as text`), `analysis.store.spec.ts` (`samples and clearing`), `app.spec.ts#loads a sample chart into the editor` |
| UI-09 | Understand failures | The backend's problem+json message is shown for 400 / 413 / 503; an unreachable backend says so and the header offers Retry; previous results are kept when a re-run fails | `api.service.spec.ts` (`describeError`), `analysis.store.spec.ts`, `app.spec.ts` (`shows the server error message…`, `offers a retry when the backend is offline`) |
| UI-10 | Check and reload rules | The header shows rule counts and rejected rules with reasons; "Reload rules from MySQL" re-reads the tables and re-analyzes the chart on screen so a rule edit is visible at once | `analysis.store.spec.ts` (`rules`), `app.spec.ts#lists rejected rules in the rules panel…`, `#shows a rejected-rules warning…` |

| ID | UI edge case | Expected behaviour | Proven by |
|----|--------------|--------------------|-----------|
| UI-E1 | Chart over the 1,000,000-character limit | Flagged in the editor; Analyze disabled; no request | `app.spec.ts#flags a chart that is longer than the server limit`, `analysis.store.spec.ts#does not call the server…` |
| UI-E2 | Two analyses in flight | The older request is cancelled, so an old response can never overwrite a newer one | `analysis.store.spec.ts#cancels an older request…` |
| UI-E3 | Emoji / surrogate pairs | Server offsets (UTF-16) line up with JavaScript string indexes, so highlights land on the right text | `highlight.spec.ts#keeps offsets correct after emoji…` |
| UI-E4 | Response no longer matches the text, or spans outside it | Highlighting degrades (clamps) instead of throwing | `highlight.spec.ts` (`clamps spans…`, `degrades gracefully…`) |
| UI-E5 | localStorage blocked, full, corrupt or from another version | The UI keeps working in memory; bad entries are ignored | `saved-charts.service.spec.ts` |
| UI-E6 | Opened over a LAN IP (insecure origin, no `crypto.randomUUID`) | Saving still works (fallback id) | `saved-charts.service.spec.ts#does not need crypto.randomUUID…` |
| UI-E7 | Very large chart (over 150,000 characters) | The annotated view is replaced by a hint to use the tables (thousands of DOM nodes would freeze the page) | `app.spec.ts#replaces the annotated view by a hint for very large charts…` |
| UI-E8 | Machine locale (e.g. en-IN grouping `10,00,000`) | Numbers use a fixed locale so the UI is the same everywhere | `app.spec.ts#enables Analyze once there is text and shows the character count` |

UI limitations: rule tables can be *checked and reloaded* from the UI but not edited there (edit them in MySQL, then use
"Reload rules"); saved charts live in one browser only; the annotated view is capped at 150,000 characters.

---

## 5. Adding a new case

1. Add the behaviour to a test, prefixed with the next free `UC-`/`EC-`/`UI-` ID in a comment.
2. Add a row here with the test name.
3. If it needs a chart, put it in `samples/` (it is copied to the test classpath and to the UI automatically).
