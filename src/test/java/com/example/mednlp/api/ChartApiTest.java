package com.example.mednlp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mednlp.api.Dtos.AnalyzeRequest;
import com.example.mednlp.api.Dtos.AnalyzeResponse;
import com.example.mednlp.api.Dtos.ConceptDto;
import com.example.mednlp.api.Dtos.MeasurementDto;
import com.example.mednlp.domain.ProcessingRunRepository;
import com.example.mednlp.uima.annotators.SampleCharts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end through HTTP, Spring, JPA (H2 in MySQL mode) and the real UIMA pipeline. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ChartApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ProcessingRunRepository runs;

    private AnalyzeResponse analyze(String text, boolean redact, boolean sentences) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/charts/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AnalyzeRequest(text, redact, sentences))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), AnalyzeResponse.class);
    }

    private static List<ConceptDto> named(AnalyzeResponse r, String text) {
        return r.concepts().stream().filter(c -> c.text().equalsIgnoreCase(text)).toList();
    }

    // ---------------------------------------------------------------- UC-01..UC-13

    @Test
    void soapNoteEndToEnd() throws Exception {
        AnalyzeResponse r = analyze(SampleCharts.read("soap-note.txt"), false, false);

        // UC-02 sections
        assertThat(r.sections()).extracting(Dtos.SectionDto::name).containsExactly(
                "CHIEF_COMPLAINT", "HISTORY_OF_PRESENT_ILLNESS", "PAST_MEDICAL_HISTORY", "MEDICATIONS", "ALLERGIES",
                "FAMILY_HISTORY", "VITALS", "LABS", "ASSESSMENT", "PLAN");

        // UC-03 codes across three vocabularies
        assertThat(named(r, "type 2 diabetes mellitus")).hasSize(2).allSatisfy(c -> {
            assertThat(c.code()).isEqualTo("E11.9");
            assertThat(c.codeSystem()).isEqualTo("ICD10CM");
            assertThat(c.negated()).isFalse();
        });
        assertThat(named(r, "metformin")).hasSize(2).allSatisfy(c -> assertThat(c.code()).isEqualTo("6809"));
        assertThat(named(r, "lisinopril")).singleElement().satisfies(c -> assertThat(c.codeSystem()).isEqualTo("RXNORM"));
        assertThat(named(r, "HbA1c")).singleElement().satisfies(c -> assertThat(c.code()).isEqualTo("4548-4"));
        assertThat(named(r, "Troponin")).singleElement().satisfies(c -> {
            assertThat(c.code()).isEqualTo("10839-9");
            assertThat(c.negated()).isFalse();
        });
        assertThat(named(r, "COPD")).singleElement().satisfies(c -> assertThat(c.code()).isEqualTo("J44.9"));

        // UC-04..UC-06 negation
        assertThat(named(r, "pneumonia")).hasSize(2).allSatisfy(c -> assertThat(c.negated()).isTrue());
        assertThat(named(r, "pneumonia")).extracting(ConceptDto::negationTrigger).containsExactly("was ruled out", "No evidence of");
        assertThat(named(r, "fever")).singleElement().satisfies(c -> assertThat(c.negated()).isTrue());
        assertThat(named(r, "cough")).singleElement().satisfies(c -> assertThat(c.negated()).isTrue());
        assertThat(named(r, "headache")).singleElement().satisfies(c -> assertThat(c.negated()).isTrue());
        assertThat(named(r, "nausea")).singleElement().satisfies(c -> assertThat(c.negated()).isFalse());
        assertThat(named(r, "chest pain")).hasSize(2).allSatisfy(c -> assertThat(c.negated()).isFalse());
        assertThat(named(r, "Myocardial infarction")).singleElement().satisfies(c -> assertThat(c.negated()).isTrue());

        // UC-13 family history is attributed to the family, not the patient
        assertThat(named(r, "diabetes")).singleElement().satisfies(c -> {
            assertThat(c.experiencer()).isEqualTo("FAMILY");
            assertThat(c.section()).isEqualTo("FAMILY_HISTORY");
        });
        assertThat(named(r, "MI")).singleElement().satisfies(c -> assertThat(c.experiencer()).isEqualTo("FAMILY"));
        assertThat(named(r, "hypertension")).hasSize(3).allSatisfy(c -> assertThat(c.experiencer()).isEqualTo("PATIENT"));

        // UC-08/09/10 measurements
        assertThat(r.measurements()).extracting(MeasurementDto::type, MeasurementDto::value).contains(
                org.assertj.core.groups.Tuple.tuple("BLOOD_PRESSURE", "148/92"),
                org.assertj.core.groups.Tuple.tuple("HEART_RATE", "88"),
                org.assertj.core.groups.Tuple.tuple("RESPIRATORY_RATE", "18"),
                org.assertj.core.groups.Tuple.tuple("TEMPERATURE", "98.6"),
                org.assertj.core.groups.Tuple.tuple("OXYGEN_SATURATION", "97"));
        assertThat(r.measurements()).filteredOn(m -> m.type().equals("DOSAGE")).extracting(MeasurementDto::text)
                .containsExactly("500 mg BID", "10 mg PO daily", "40 mg daily");
        assertThat(r.measurements()).filteredOn(m -> m.type().equals("LAB_VALUE")).extracting(MeasurementDto::text)
                .containsExactly("Glucose 182 mg/dL", "HbA1c 8.1%", "Troponin 0.01 ng/mL", "Creatinine 1.1 mg/dL");

        // UC-11 PHI
        assertThat(r.phi()).extracting(Dtos.PhiDto::type).containsExactly("NAME", "MRN", "DOB", "PHONE", "PHONE");

        assertThat(r.redactedText()).isNull();
        assertThat(r.sentences()).isNull();
        assertThat(r.textLength()).isEqualTo(SampleCharts.read("soap-note.txt").length());
        assertThat(r.stats().rulesRejected()).isZero();
    }

    @Test
    void conceptOffsetsPointAtTheOriginalText() throws Exception {
        String text = SampleCharts.read("soap-note.txt");
        AnalyzeResponse r = analyze(text, false, false);
        assertThat(r.concepts()).isNotEmpty().allSatisfy(c -> assertThat(text.substring(c.begin(), c.end())).isEqualTo(c.text()));
        assertThat(r.measurements()).allSatisfy(m -> assertThat(text.substring(m.begin(), m.end())).isEqualTo(m.text()));
    }

    @Test
    void phiRedaction() throws Exception {
        String text = SampleCharts.read("soap-note.txt");
        AnalyzeResponse r = analyze(text, true, false);
        assertThat(r.redactedText()).contains("[NAME]", "[MRN]", "[DOB]", "[PHONE]")
                .doesNotContain("84736251", "John A. Smith", "555-987-6543", "04/12/1968")
                .contains("hypertension");
    }

    @Test
    void phiHeavyChart() throws Exception {
        AnalyzeResponse r = analyze(SampleCharts.read("phi-heavy.txt"), true, false);
        assertThat(r.phi()).extracting(Dtos.PhiDto::type)
                .containsExactly("NAME", "MRN", "DOB", "SSN", "EMAIL", "PHONE", "PHONE", "PHONE");
        assertThat(r.redactedText()).doesNotContain("123-45-6789", "maria.garcia@example.com", "555-000-1111", "Maria");
    }

    @Test
    void negationCasesChart() throws Exception {
        AnalyzeResponse r = analyze(SampleCharts.read("negation-cases.txt"), false, false);
        List<String> negated = r.concepts().stream().filter(ConceptDto::negated).map(ConceptDto::text).toList();
        // denies chest pain | No fever, cough or headache | Denies SOB (but chest pain stays) | Pneumonia was ruled out
        // | Troponin was negative | not taking metformin
        assertThat(negated).containsExactly("chest pain", "fever", "cough", "headache", "shortness of breath", "Pneumonia",
                "fever", "Troponin", "metformin");
        List<String> affirmed = r.concepts().stream().filter(c -> !c.negated()).map(ConceptDto::text).toList();
        // has chest pain | No increase in cough | Not certain about pneumonia | Pneumonia is not ruled out | Cough is present
        assertThat(affirmed).containsExactly("chest pain", "cough", "pneumonia", "Pneumonia", "Cough");
    }

    @Test
    void unsectionedFreeTextChart() throws Exception {
        AnalyzeResponse r = analyze(SampleCharts.read("unsectioned-note.txt"), false, true);
        assertThat(r.sections()).isEmpty();
        assertThat(r.concepts()).allSatisfy(c -> assertThat(c.section()).isEqualTo("UNSECTIONED"));
        assertThat(named(r, "type 2 diabetes mellitus")).hasSize(1);
        assertThat(named(r, "chest    pain")).hasSize(1);
        assertThat(named(r, "aspirin")).hasSize(1);
        assertThat(named(r, "asthma")).singleElement().satisfies(c -> assertThat(c.negated()).isTrue());
        assertThat(named(r, "shortness  of  breath")).singleElement().satisfies(c -> assertThat(c.negated()).isFalse());
        assertThat(r.measurements()).extracting(MeasurementDto::text).contains("81 mg daily", "Glucose 210 mg/dL");
        assertThat(r.sentences()).hasSize(r.sentenceCount()).isNotEmpty();
    }

    // ---------------------------------------------------------------- UC-16 and formats

    @Test
    void plainTextEndpoint() throws Exception {
        String text = SampleCharts.read("soap-note.txt");
        mvc.perform(post("/api/v1/charts/analyze/text?redactPhi=true&includeSentences=true")
                        .contentType("text/plain;charset=UTF-8")
                        .content(text.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concepts.length()").value(org.hamcrest.Matchers.greaterThan(20)))
                .andExpect(jsonPath("$.redactedText").value(org.hamcrest.Matchers.containsString("[MRN]")))
                .andExpect(jsonPath("$.sentences").isArray());
    }

    @Test
    void utf8TextKeepsOffsetsConsistent() throws Exception {
        String text = "Patient café 😀 denies fever — naïve cough";
        MvcResult result = mvc.perform(post("/api/v1/charts/analyze/text")
                        .contentType("text/plain;charset=UTF-8").content(text.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk()).andReturn();
        AnalyzeResponse r = mapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), AnalyzeResponse.class);
        ConceptDto fever = named(r, "fever").get(0);
        assertThat(fever.begin()).isEqualTo(text.indexOf("fever"));
        assertThat(fever.negated()).isTrue();
        assertThat(named(r, "cough")).singleElement().satisfies(c -> assertThat(c.begin()).isEqualTo(text.indexOf("cough")));
    }

    @Test
    void windowsLineEndings() throws Exception {
        AnalyzeResponse r = analyze("Chief Complaint: fever\r\nHPI: denies cough\r\nPlan: rest\r\n", false, false);
        assertThat(r.sections()).extracting(Dtos.SectionDto::name).containsExactly("CHIEF_COMPLAINT", "HISTORY_OF_PRESENT_ILLNESS", "PLAN");
        assertThat(named(r, "cough")).singleElement().satisfies(c -> assertThat(c.negated()).isTrue());
        assertThat(named(r, "fever")).singleElement().satisfies(c -> assertThat(c.negated()).isFalse());
    }

    @Test
    void chartWithoutAnyFindingsReturnsEmptyListsNotNulls() throws Exception {
        mvc.perform(post("/api/v1/charts/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"The patient is well and in good spirits.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concepts").isEmpty())
                .andExpect(jsonPath("$.phi").isEmpty())
                .andExpect(jsonPath("$.measurements").isEmpty())
                .andExpect(jsonPath("$.sections").isEmpty());
    }

    @Test
    void theSameChartAlwaysGivesTheSameResult() throws Exception {
        String text = SampleCharts.read("soap-note.txt");
        AnalyzeResponse first = analyze(text, true, true);
        for (int i = 0; i < 8; i++) { // more requests than pooled engines, so every engine is reused
            AnalyzeResponse again = analyze(text, true, true);
            assertThat(again.concepts()).isEqualTo(first.concepts());
            assertThat(again.measurements()).isEqualTo(first.measurements());
            assertThat(again.phi()).isEqualTo(first.phi());
            assertThat(again.redactedText()).isEqualTo(first.redactedText());
        }
    }

    // ---------------------------------------------------------------- error handling (EC-01..EC-03, EC-31)

    @Test
    void blankAndMissingTextAreRejectedWith400() throws Exception {
        for (String body : List.of("{\"text\":\"\"}", "{\"text\":\"   \\n\\t \"}", "{}", "{\"text\":null}")) {
            mvc.perform(post("/api/v1/charts/analyze").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("The chart text must not be empty"));
        }
    }

    @Test
    void malformedOrEmptyBodiesAreRejectedWith400() throws Exception {
        for (String body : List.of("{\"text\": ", "not json", "[]")) {
            mvc.perform(post("/api/v1/charts/analyze").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/charts/analyze").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/charts/analyze/text").contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/charts/analyze/text").contentType(MediaType.TEXT_PLAIN).content("   \n  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedContentTypeAndMethodAreRejected() throws Exception {
        mvc.perform(post("/api/v1/charts/analyze").contentType(MediaType.APPLICATION_XML).content("<text>fever</text>"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/v1/charts/analyze")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void errorResponsesDoNotEchoTheChartText() throws Exception {
        String secret = "PATIENT-SECRET-" + "x".repeat(10);
        MvcResult result = mvc.perform(post("/api/v1/charts/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"" + secret + "\", \"redactPhi\": \"not-a-boolean\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
    }

    // ---------------------------------------------------------------- UC-15 audit and operations

    @Test
    void everyAnalysisWritesAnAuditRowWithoutChartText() throws Exception {
        long before = runs.count();
        AnalyzeResponse r = analyze("Patient denies fever. MRN: 12345678", false, false);
        assertThat(runs.count()).isEqualTo(before + 1);
        var latest = runs.findAll().stream().max(java.util.Comparator.comparing(com.example.mednlp.domain.ProcessingRun::getId)).orElseThrow();
        assertThat(latest.getCharCount()).isEqualTo(r.textLength());
        assertThat(latest.getConceptCount()).isEqualTo(1);
        assertThat(latest.getNegatedCount()).isEqualTo(1);
        assertThat(latest.getPhiCount()).isEqualTo(1);
    }

    @Test
    void ruleSummaryDescribesTheActiveRules() throws Exception {
        mvc.perform(get("/api/v1/rules/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionRules").value(13))
                .andExpect(jsonPath("$.concepts").value(org.hamcrest.Matchers.greaterThan(30)))
                .andExpect(jsonPath("$.negationTriggers").value(org.hamcrest.Matchers.greaterThan(40)))
                .andExpect(jsonPath("$.rejected").isEmpty());
    }

    @Test
    void healthAndOpenApiEndpointsAreAvailable() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/charts/analyze")));
    }
}
