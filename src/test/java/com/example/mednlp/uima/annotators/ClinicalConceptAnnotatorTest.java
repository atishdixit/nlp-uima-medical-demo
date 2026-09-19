package com.example.mednlp.uima.annotators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.mednlp.types.ClinicalConcept;
import com.example.mednlp.uima.PipelineFixture;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** UC-03 concept-to-code mapping and the matching edge cases EC-06..EC-11. */
class ClinicalConceptAnnotatorTest {

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
    void mapsTermsToIcd10RxNormAndLoincCodes() {
        pipeline.run("hypertension, metformin and hemoglobin a1c");
        assertThat(pipeline.concepts())
                .extracting(ClinicalConcept::getCode, ClinicalConcept::getCodeSystem, ClinicalConcept::getCategory)
                .containsExactly(
                        tuple("I10", "ICD10CM", "DIAGNOSIS"),
                        tuple("6809", "RXNORM", "MEDICATION"),
                        tuple("4548-4", "LOINC", "LAB"));
    }

    @Test
    void longestMatchWins() {
        pipeline.run("History of type 2 diabetes mellitus.");
        assertThat(pipeline.concepts()).hasSize(1);
        assertThat(pipeline.concepts().get(0).getCoveredText()).isEqualTo("type 2 diabetes mellitus");
        assertThat(pipeline.concepts().get(0).getCode()).isEqualTo("E11.9");
    }

    @Test
    void shorterTermStillMatchesOnItsOwn() {
        pipeline.run("Diabetes noted.");
        assertThat(pipeline.concepts()).extracting(ClinicalConcept::getCode).containsExactly("E14.9");
    }

    @Test
    void matchingIsCaseInsensitiveForNormalTerms() {
        pipeline.run("HYPERTENSION and Pneumonia");
        assertThat(pipeline.concepts()).extracting(ClinicalConcept::getCode).containsExactly("I10", "J18.9");
    }

    @Test
    void abbreviationsMarkedCaseSensitiveOnlyMatchExactCase() {
        pipeline.run("History of MI.");
        assertThat(pipeline.concepts()).extracting(ClinicalConcept::getCode).containsExactly("I21.9");

        pipeline.run("Take mi tablets, Mi and mI are not the abbreviation.");
        assertThat(pipeline.concepts()).isEmpty();
    }

    @Test
    void matchesMustSitOnWordBoundaries() {
        pipeline.run("Coughing noted. Prehypertension. HTN2 was typed. Feverish.");
        assertThat(pipeline.concepts()).isEmpty();
    }

    @Test
    void punctuationCountsAsABoundary() {
        pipeline.run("(fever)/cough,headache");
        assertThat(pipeline.concepts()).extracting(ClinicalConcept::getCoveredText).containsExactly("fever", "cough", "headache");
    }

    @Test
    void toleratesIrregularWhitespaceAndReportsOriginalOffsets() {
        String text = "chest    pain and shortness\nof breath";
        pipeline.run(text);
        assertThat(pipeline.concepts()).extracting(ClinicalConcept::getCoveredText)
                .containsExactly("chest    pain", "shortness\nof breath");
        ClinicalConcept first = pipeline.concepts().get(0);
        assertThat(text.substring(first.getBegin(), first.getEnd())).isEqualTo("chest    pain");
    }

    @Test
    void nonBreakingSpaceIsTreatedAsWhitespace() {
        pipeline.run("chest pain");
        assertThat(pipeline.concepts()).extracting(ClinicalConcept::getCode).containsExactly("R07.9");
    }

    @Test
    void unicodeTextKeepsOffsetsAligned() {
        String text = "Patient café — température élevée; 😀 fever noted. İSTANBUL cough";
        pipeline.run(text);
        assertThat(pipeline.concepts()).hasSize(2);
        for (ClinicalConcept c : pipeline.concepts()) {
            assertThat(text.substring(c.getBegin(), c.getEnd())).isEqualTo(c.getCoveredText());
        }
        assertThat(pipeline.concepts()).extracting(ClinicalConcept::getCoveredText).containsExactly("fever", "cough");
    }

    @Test
    void everyOccurrenceIsReported() {
        pipeline.run("fever yesterday, fever today");
        assertThat(pipeline.concepts()).hasSize(2);
    }

    @Test
    void textWithoutConceptsYieldsNothing() {
        pipeline.run("The patient is well and in good spirits.");
        assertThat(pipeline.concepts()).isEmpty();
    }

    @Test
    void repeatedRunsOnTheSameEngineDoNotLeakAnnotations() {
        pipeline.run("fever");
        pipeline.run("cough");
        assertThat(pipeline.concepts()).extracting(ClinicalConcept::getCoveredText).containsExactly("cough");
    }

    @Test
    void unsectionedFreeTextWorksEndToEnd() {
        pipeline.run("Pt seen today   with type 2 diabetes mellitus   and\nchest    pain.");
        List<String> texts = pipeline.concepts().stream().map(ClinicalConcept::getCoveredText).toList();
        assertThat(texts).containsExactly("type 2 diabetes mellitus", "chest    pain");
        assertThat(pipeline.concepts()).allSatisfy(c -> assertThat(c.getSectionName()).isEqualTo("UNSECTIONED"));
    }
}
