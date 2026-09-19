package com.example.mednlp.uima.annotators;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mednlp.rules.RuleDefinitions;
import com.example.mednlp.rules.RuleDefinitions.ConceptDef;
import com.example.mednlp.rules.RuleDefinitions.NegationDef;
import com.example.mednlp.rules.RuleDefinitions.TermDef;
import com.example.mednlp.uima.PipelineFixture;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** UC-04..UC-07 (negation) and EC-15..EC-17 (scope, window, no leakage). */
class NegationAnnotatorTest {

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
    void preTriggerNegatesTheFollowingConcept() {
        pipeline.run("Patient denies chest pain.");
        assertThat(pipeline.negated()).containsExactly("chest pain");
        assertThat(pipeline.concept("chest pain").getNegationTrigger()).isEqualTo("denies");
    }

    @Test
    void oneTriggerNegatesAWholeList() {
        pipeline.run("No fever, cough or headache.");
        assertThat(pipeline.negated()).containsExactly("fever", "cough", "headache");
    }

    @Test
    void longestTriggerWins() {
        pipeline.run("No evidence of pneumonia.");
        assertThat(pipeline.concept("pneumonia").getNegationTrigger()).isEqualTo("No evidence of");
    }

    @Test
    void postTriggerNegatesThePrecedingConcept() {
        pipeline.run("Pneumonia was ruled out.");
        assertThat(pipeline.negated()).containsExactly("Pneumonia");
        assertThat(pipeline.concept("pneumonia").getNegationTrigger()).isEqualTo("was ruled out");
        pipeline.run("Troponin was negative.");
        assertThat(pipeline.negated()).containsExactly("Troponin");
    }

    @Test
    void postTriggerDoesNotNegateAConceptThatFollowsIt() {
        pipeline.run("Unlikely pneumonia.");
        assertThat(pipeline.negated()).isEmpty();
    }

    @Test
    void terminationTermStopsTheScope() {
        pipeline.run("Denies shortness of breath but has chest pain.");
        assertThat(pipeline.negated()).containsExactly("shortness of breath");
        assertThat(pipeline.affirmed()).containsExactly("chest pain");
    }

    @Test
    void reportsActsAsATerminator() {
        pipeline.run("Denies fever and reports cough.");
        assertThat(pipeline.negated()).containsExactly("fever");
        assertThat(pipeline.affirmed()).containsExactly("cough");
    }

    @Test
    void pseudoNegationDoesNotNegate() {
        for (String text : List.of("No increase in cough.", "Not certain about pneumonia.",
                "Pneumonia is not ruled out.", "Cannot rule out pneumonia.", "No change in fever.")) {
            pipeline.run(text);
            assertThat(pipeline.negated()).as(text).isEmpty();
            assertThat(pipeline.concepts()).as(text).hasSize(1);
        }
    }

    @Test
    void negationDoesNotLeakIntoTheNextSentence() {
        pipeline.run("Denies fever. Cough is present.");
        assertThat(pipeline.negated()).containsExactly("fever");
        assertThat(pipeline.affirmed()).containsExactly("Cough");
    }

    @Test
    void semicolonAndNewlineLimitTheScope() {
        pipeline.run("Denies fever; cough persists.\nDenies headache\nnausea noted");
        assertThat(pipeline.negated()).containsExactly("fever", "headache");
        assertThat(pipeline.affirmed()).containsExactly("cough", "nausea");
    }

    @Test
    void windowBoundary_sixTokensNegates_sevenDoesNot() {
        pipeline.run("Denies any history of previous episodes of pneumonia.");   // 6 tokens between
        assertThat(pipeline.negated()).containsExactly("pneumonia");
        pipeline.run("Denies any known history of previous episodes of pneumonia."); // 7 tokens between
        assertThat(pipeline.negated()).isEmpty();
    }

    @Test
    void triggersAreCaseInsensitive() {
        pipeline.run("DENIES CHEST PAIN");
        assertThat(pipeline.negated()).containsExactly("CHEST PAIN");
    }

    @Test
    void triggersInsideOtherWordsAreIgnored() {
        pipeline.run("Known asthma, notes cough, nose bleed with fever.");
        assertThat(pipeline.negated()).isEmpty();
    }

    @Test
    void withoutNegatesLists() {
        pipeline.run("Presented without fever or cough.");
        assertThat(pipeline.negated()).containsExactly("fever", "cough");
    }

    @Test
    void notTakingAMedicationIsTreatedAsNegated() {
        pipeline.run("Patient is not taking metformin.");
        assertThat(pipeline.negated()).containsExactly("metformin");
    }

    @Test
    void aTriggerInsideAConceptTermIsNotATrigger() {
        RuleDefinitions defs = new RuleDefinitions(List.of(), List.of(),
                List.of(new ConceptDef("NKA", "TEST", "No known allergies", "STATUS", true,
                        List.of(new TermDef("no known allergies", false, true)))),
                List.of(new NegationDef("no", "PRE", true)));
        try (PipelineFixture p = PipelineFixture.with(defs)) {
            p.run("Patient has no known allergies.");
            assertThat(p.concepts()).hasSize(1);
            assertThat(p.negated()).isEmpty();
        }
    }
}
