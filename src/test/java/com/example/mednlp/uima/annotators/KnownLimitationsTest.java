package com.example.mednlp.uima.annotators;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mednlp.types.ClinicalConcept;
import com.example.mednlp.uima.PipelineFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * LIM-01..LIM-06: each test pins a limitation documented in docs/ARCHITECTURE.md (section 9) and
 * docs/USE_CASES.md. They assert today's behaviour, so an improvement that changes it makes the
 * test fail on purpose: update the test and the docs together.
 */
class KnownLimitationsTest {

    private static PipelineFixture pipeline;

    @BeforeAll
    static void setUp() {
        pipeline = PipelineFixture.seeded();
    }

    @AfterAll
    static void tearDown() {
        pipeline.close();
    }

    @Test
    void lim01_negationCanLeakWithinTheWindowWhenNoTerminatorIsPresent() {
        pipeline.run("No fever, has cough.");
        assertThat(pipeline.negated()).containsExactly("fever", "cough"); // "cough" is really present
    }

    @Test
    void lim02_familyHistoryIsOnlyRecognisedBySection() {
        pipeline.run("HPI: Mother had diabetes.");
        assertThat(pipeline.concept("diabetes").getExperiencer()).isEqualTo("PATIENT");
    }

    @Test
    void lim03_allergiesAreExtractedLikeAnyOtherConcept() {
        pipeline.run("Allergies: aspirin");
        ClinicalConcept aspirin = pipeline.concept("aspirin");
        assertThat(aspirin.getSectionName()).isEqualTo("ALLERGIES");
        assertThat(aspirin.getCategory()).isEqualTo("MEDICATION");
        assertThat(aspirin.getNegated()).isFalse();
    }

    @Test
    void lim04_hypotheticalAndTemporalContextIsNotModelled() {
        pipeline.run("Rule out pneumonia. History of asthma.");
        assertThat(pipeline.negated()).isEmpty();
        assertThat(pipeline.affirmed()).containsExactly("pneumonia", "asthma");
    }

    @Test
    void lim05_aHardWrappedSentenceIsSplitAtTheLineBreak() {
        pipeline.run("The patient has been having a fever and\nfeels weak.");
        assertThat(pipeline.sentences()).hasSize(2);
    }

    @Test
    void lim06_patientNamesAreOnlyFoundAfterALabel() {
        pipeline.run("John Smith was seen today. Patient: Jane Doe");
        assertThat(pipeline.phi()).hasSize(1);
        assertThat(pipeline.phi().get(0).getCoveredText()).isEqualTo("Patient: Jane Doe");
    }
}
