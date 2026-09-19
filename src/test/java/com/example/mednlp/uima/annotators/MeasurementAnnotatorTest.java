package com.example.mednlp.uima.annotators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.mednlp.rules.RuleDefinitions;
import com.example.mednlp.rules.RuleDefinitions.ConceptDef;
import com.example.mednlp.rules.RuleDefinitions.RegexDef;
import com.example.mednlp.rules.RuleDefinitions.TermDef;
import com.example.mednlp.rules.RuleSet;
import com.example.mednlp.types.Measurement;
import com.example.mednlp.uima.PipelineFixture;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** UC-08 vitals, UC-09 dosage, UC-10 labs, EC-20 (regex timeout), EC-27 (mg/dL is not a dosage). */
class MeasurementAnnotatorTest {

    private static PipelineFixture pipeline;

    @BeforeAll
    static void setUp() {
        pipeline = PipelineFixture.seeded();
    }

    @AfterAll
    static void tearDown() {
        pipeline.close();
    }

    private static List<Measurement> of(String type) {
        return pipeline.measurements().stream().filter(m -> m.getMeasurementType().equals(type)).toList();
    }

    @Test
    void extractsVitalSigns() {
        pipeline.run("Vitals: BP 148/92 mmHg, HR 88, RR 18, Temp 98.6, SpO2 97%");
        assertThat(pipeline.measurements())
                .extracting(Measurement::getMeasurementType, Measurement::getValue, Measurement::getUnit)
                .containsExactlyInAnyOrder(
                        tuple("BLOOD_PRESSURE", "148/92", "mmHg"),
                        tuple("HEART_RATE", "88", "bpm"),
                        tuple("RESPIRATORY_RATE", "18", "/min"),
                        tuple("TEMPERATURE", "98.6", null),
                        tuple("OXYGEN_SATURATION", "97", "%"));
    }

    @Test
    void vitalLabelVariants() {
        pipeline.run("blood pressure: 120 / 80; Pulse: 64; heart rate=70");
        assertThat(of("BLOOD_PRESSURE")).extracting(Measurement::getValue).containsExactly("120 / 80");
        assertThat(of("HEART_RATE")).extracting(Measurement::getValue).containsExactly("64", "70");
    }

    @Test
    void extractsDosagesWithRouteAndFrequency() {
        pipeline.run("Metformin 500 mg BID. Lisinopril 10 mg PO daily. Albuterol 2.5 mg. Insulin 10 units qhs.");
        assertThat(of("DOSAGE")).extracting(Measurement::getCoveredText)
                .containsExactly("500 mg BID", "10 mg PO daily", "2.5 mg", "10 units qhs");
    }

    @Test
    void labValuesKeepTheirUnitsAndAreNotMistakenForDosages() {
        pipeline.run("Glucose 182 mg/dL, HbA1c 8.1%, Troponin 0.01 ng/mL.");
        assertThat(of("LAB_VALUE")).extracting(Measurement::getCoveredText)
                .containsExactly("Glucose 182 mg/dL", "HbA1c 8.1%", "Troponin 0.01 ng/mL");
        assertThat(of("DOSAGE")).isEmpty();
    }

    @Test
    void aBareMgPerDlIsNotADosage() {
        pipeline.run("Reading was 110 mg/dL today.");
        assertThat(pipeline.measurements()).isEmpty();
    }

    @Test
    void labelsWithoutValuesProduceNothing() {
        pipeline.run("BP stable, HR regular, HbA1c pending, temperature normal, attempt 5, temporary 3.");
        assertThat(pipeline.measurements()).isEmpty();
    }

    @Test
    void repeatedReadingsAreAllReported() {
        pipeline.run("BP 120/80 at 8am, BP 130/85 at noon.");
        assertThat(of("BLOOD_PRESSURE")).extracting(Measurement::getValue).containsExactly("120/80", "130/85");
    }

    @Test
    void aSlowRegexIsSkippedWithoutBreakingTheRestOfTheDocument() {
        // (.*a){20}b passes the static nested-quantifier check but is exponential on "aaaa...": only the runtime timeout stops it
        RuleDefinitions defs = new RuleDefinitions(List.of(),
                List.of(new RegexDef("EVIL", "MEASUREMENT", "EVIL", "(.*a){20}b", 0, null, true),
                        new RegexDef("HR", "MEASUREMENT", "HEART_RATE", "HR (\\d+)", 1, "bpm", true)),
                List.of(new ConceptDef("R50.9", "ICD10CM", "Fever", "SYMPTOM", true, List.of(new TermDef("fever", false, true)))),
                List.of());
        try (PipelineFixture p = new PipelineFixture(defs, new RuleSet.Options(6, 150))) {
            assertThat(p.rules().rejected()).isEmpty();
            long started = System.nanoTime();
            p.run("a".repeat(60) + " fever HR 72");
            long millis = (System.nanoTime() - started) / 1_000_000;

            assertThat(millis).as("the runaway regex must be cut off by the timeout").isLessThan(5_000);
            assertThat(p.concepts()).hasSize(1);
            assertThat(p.measurements()).extracting(Measurement::getMeasurementType, Measurement::getValue)
                    .containsExactly(tuple("HEART_RATE", "72"));
        }
    }
}
