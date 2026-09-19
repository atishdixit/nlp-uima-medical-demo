package com.example.mednlp.uima.annotators;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mednlp.rules.RuleDefinitions;
import com.example.mednlp.rules.RuleDefinitions.SectionDef;
import com.example.mednlp.types.ChartSection;
import com.example.mednlp.uima.PipelineFixture;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** UC-02 and EC-12/EC-13: section detection from the section_header rules. */
class SectionAnnotatorTest {

    private static PipelineFixture pipeline;

    @BeforeAll
    static void setUp() {
        pipeline = PipelineFixture.seeded();
    }

    @AfterAll
    static void tearDown() {
        pipeline.close();
    }

    private static List<String> names(PipelineFixture p) {
        return p.sections().stream().map(ChartSection::getName).toList();
    }

    @Test
    void detectsSectionsAndTheirExtent() {
        String text = "CC: chest pain\nHPI: 55 year old\nPlan: rest";
        pipeline.run(text);

        assertThat(names(pipeline)).containsExactly("CHIEF_COMPLAINT", "HISTORY_OF_PRESENT_ILLNESS", "PLAN");
        ChartSection cc = pipeline.sections().get(0);
        assertThat(text.substring(cc.getBegin(), cc.getEnd())).isEqualTo("CC: chest pain");
        ChartSection plan = pipeline.sections().get(2);
        assertThat(text.substring(plan.getBegin(), plan.getEnd())).isEqualTo("Plan: rest");
    }

    @Test
    void headersAreCaseInsensitiveAndTolerateSpacesAndIndentation() {
        pipeline.run("chief complaint : cough\n   ALLERGIES: NKDA");
        assertThat(names(pipeline)).containsExactly("CHIEF_COMPLAINT", "ALLERGIES");
    }

    @Test
    void assessmentAliasesMapToOneSection() {
        pipeline.run("A/P: stable\nAssessment and Plan: stable\nImpression: fine");
        assertThat(names(pipeline)).containsExactly("ASSESSMENT", "ASSESSMENT", "ASSESSMENT");
    }

    @Test
    void headerMustStartTheLine() {
        pipeline.run("The plan: rest and fluids");
        assertThat(pipeline.sections()).isEmpty();
    }

    @Test
    void textBeforeFirstHeaderIsUnsectioned() {
        pipeline.run("Seen in clinic.\nHPI: cough for fever");
        assertThat(pipeline.sections()).hasSize(1);
        assertThat(pipeline.concept("cough").getSectionName()).isEqualTo("HISTORY_OF_PRESENT_ILLNESS");
        pipeline.run("cough without any header");
        assertThat(pipeline.sections()).isEmpty();
        assertThat(pipeline.concept("cough").getSectionName()).isEqualTo("UNSECTIONED");
    }

    @Test
    void repeatedHeadersProduceSeparateSections() {
        pipeline.run("Medications: aspirin\nMedications: metformin");
        assertThat(names(pipeline)).containsExactly("MEDICATIONS", "MEDICATIONS");
    }

    @Test
    void windowsLineEndingsAreSupported() {
        pipeline.run("Chief Complaint: fever\r\nPlan: rest\r\n");
        assertThat(names(pipeline)).containsExactly("CHIEF_COMPLAINT", "PLAN");
    }

    @Test
    void whenTwoRulesMatchTheSameHeaderTheHigherPriorityWins() {
        RuleDefinitions defs = new RuleDefinitions(
                List.of(new SectionDef("LOW", "^[ \\t]*plan[ \\t]*:", 1, null, true),
                        new SectionDef("HIGH", "^[ \\t]*plan[ \\t]*:", 5, null, true)),
                List.of(), List.of(), List.of());
        try (PipelineFixture p = PipelineFixture.with(defs)) {
            p.run("Plan: rest");
            assertThat(names(p)).containsExactly("HIGH");
        }
    }

    @Test
    void familyHistorySectionMarksFindingsAsFamily() {
        pipeline.run("Family History: diabetes in mother\nAssessment: hypertension");
        assertThat(pipeline.concept("diabetes").getExperiencer()).isEqualTo("FAMILY");
        assertThat(pipeline.concept("hypertension").getExperiencer()).isEqualTo("PATIENT");
    }
}
