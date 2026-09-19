# Use cases and edge cases

Reference catalog of what the service is expected to do, how it behaves at the edges, and **which automated test proves it**.
Every ID below appears in the test sources, so `grep -r "EC-15" src/test` finds the proof.

- Run everything: `run.bat test` (or `mvnw.cmd test`). 144 tests, no database or Docker needed (H2 in MySQL mode).
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

## 4. Adding a new case

1. Add the behaviour to a test, prefixed with the next free `UC-`/`EC-` ID in a Javadoc comment.
2. Add a row here with the test name.
3. If it needs a chart, put it in `samples/` (it is copied to the test classpath automatically).
