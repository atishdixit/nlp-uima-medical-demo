package com.example.mednlp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mednlp.domain.Concept;
import com.example.mednlp.domain.ConceptRepository;
import com.example.mednlp.domain.NegationTrigger;
import com.example.mednlp.domain.NegationTriggerRepository;
import com.example.mednlp.domain.RegexRule;
import com.example.mednlp.domain.RegexRuleRepository;
import com.example.mednlp.rules.SeedDataLoader;
import com.example.mednlp.service.ChartAnalysisService;
import com.example.mednlp.api.Dtos.AnalyzeResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC-14 and EC-18..EC-22: operators edit the rule tables and call the refresh endpoint; no redeploy.
 * Each test gets a fresh context and therefore a fresh, re-seeded in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RuleRefreshTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ChartAnalysisService service;
    @Autowired
    ConceptRepository concepts;
    @Autowired
    RegexRuleRepository regexes;
    @Autowired
    NegationTriggerRepository negations;
    @Autowired
    SeedDataLoader seedLoader;

    private AnalyzeResponse analyze(String text) {
        return service.analyze(text, false, false);
    }

    private void refresh() throws Exception {
        mvc.perform(post("/api/v1/admin/rules/refresh")).andExpect(status().isOk());
    }

    @Test
    void aNewTermIsVisibleOnlyAfterRefresh() throws Exception {
        concepts.save(new Concept("G43.909", "ICD10CM", "Migraine, unspecified", "DIAGNOSIS").addTerm("migraine", false));

        assertThat(analyze("history of migraine").concepts()).as("rules are cached").isEmpty();

        refresh();

        assertThat(analyze("history of migraine").concepts()).singleElement().satisfies(c -> {
            assertThat(c.code()).isEqualTo("G43.909");
            assertThat(c.preferredName()).isEqualTo("Migraine, unspecified");
        });
    }

    @Test
    void disablingATermRemovesItAfterRefresh() throws Exception {
        assertThat(analyze("cough").concepts()).hasSize(1);

        Concept cough = concepts.findAllWithTerms().stream().filter(c -> c.getCode().equals("R05.9")).findFirst().orElseThrow();
        cough.getTerms().forEach(t -> t.setEnabled(false));
        concepts.save(cough);
        refresh();

        assertThat(analyze("cough").concepts()).isEmpty();
        assertThat(analyze("fever").concepts()).as("other rules are untouched").hasSize(1);
    }

    @Test
    void disablingAConceptRemovesAllItsTerms() throws Exception {
        Concept fever = concepts.findAllWithTerms().stream().filter(c -> c.getCode().equals("R50.9")).findFirst().orElseThrow();
        fever.setEnabled(false);
        concepts.save(fever);
        refresh();
        assertThat(analyze("fever").concepts()).isEmpty();
    }

    @Test
    void disablingARegexRuleTakesEffect() throws Exception {
        assertThat(analyze("mail a@b.com").phi()).hasSize(1);

        RegexRule email = regexes.findAll().stream().filter(r -> r.getRuleName().equals("PHI_EMAIL")).findFirst().orElseThrow();
        email.setEnabled(false);
        regexes.save(email);
        refresh();

        assertThat(analyze("mail a@b.com").phi()).isEmpty();
    }

    @Test
    void editingARegexPatternTakesEffect() throws Exception {
        RegexRule hr = regexes.findAll().stream().filter(r -> r.getRuleName().equals("MEAS_HEART_RATE")).findFirst().orElseThrow();
        hr.setPattern("(?i)\\b(?:HR)[ \\t]*[:=]?[ \\t]*(\\d{2,3})\\b"); // "pulse" no longer counts
        regexes.save(hr);
        assertThat(analyze("Pulse 64").measurements()).hasSize(1);
        refresh();
        assertThat(analyze("Pulse 64").measurements()).isEmpty();
        assertThat(analyze("HR 64").measurements()).hasSize(1);
    }

    @Test
    void badRulesInTheDatabaseAreRejectedAndReportedWhileEverythingElseKeepsWorking() throws Exception {
        regexes.save(new RegexRule("BAD_SYNTAX", "PHI", "X", "[unclosed", 0, null, null));
        regexes.save(new RegexRule("BAD_NESTED", "MEASUREMENT", "X", "(a+)+$", 0, null, null));
        regexes.save(new RegexRule("BAD_GROUP", "SOMETHING_ELSE", "X", "\\d+", 0, null, null));
        negations.save(new NegationTrigger("sideways", "SIDEWAYS"));

        mvc.perform(post("/api/v1/admin/rules/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected.length()").value(4))
                .andExpect(jsonPath("$.rejected[*].name").value(
                        org.hamcrest.Matchers.containsInAnyOrder("BAD_SYNTAX", "BAD_NESTED", "BAD_GROUP", "sideways")));

        AnalyzeResponse r = analyze("Patient denies fever. BP 120/80. MRN: 12345678.");
        assertThat(r.concepts()).hasSize(1);
        assertThat(r.concepts().get(0).negated()).isTrue();
        assertThat(r.measurements()).hasSize(1);
        assertThat(r.phi()).hasSize(1);
        assertThat(r.stats().rulesRejected()).isEqualTo(4);
    }

    @Test
    void addingANegationTriggerChangesBehaviourAfterRefresh() throws Exception {
        assertThat(analyze("Patient refuses aspirin.").concepts().get(0).negated()).isFalse();
        negations.save(new NegationTrigger("refuses", "PRE"));
        refresh();
        assertThat(analyze("Patient refuses aspirin.").concepts().get(0).negated()).isTrue();
    }

    @Test
    void theDatabaseRejectsDuplicateTriggerTerms() {
        Concept duplicate = new Concept("X99", "ICD10CM", "Duplicate cough", "SYMPTOM").addTerm("cough", false);
        assertThatThrownBy(() -> concepts.save(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seedingNeverOverwritesOperatorEdits() throws Exception {
        long conceptCount = concepts.count();
        Concept cough = concepts.findAllWithTerms().stream().filter(c -> c.getCode().equals("R05.9")).findFirst().orElseThrow();
        cough.getTerms().forEach(t -> t.setEnabled(false));
        concepts.save(cough);

        seedLoader.afterPropertiesSet(); // what would happen on the next application start

        assertThat(concepts.count()).isEqualTo(conceptCount);
        refresh();
        assertThat(analyze("cough").concepts()).isEmpty();
        List<Concept> reloaded = concepts.findAllWithTerms();
        assertThat(reloaded).filteredOn(c -> c.getCode().equals("R05.9")).allSatisfy(c ->
                assertThat(c.getTerms()).allMatch(t -> !t.isEnabled()));
    }
}
