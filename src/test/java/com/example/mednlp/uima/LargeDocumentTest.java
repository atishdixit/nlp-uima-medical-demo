package com.example.mednlp.uima;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mednlp.types.ClinicalConcept;
import org.junit.jupiter.api.Test;

/** EC-26: a chart near the 1 MB limit is processed correctly and in reasonable time. */
class LargeDocumentTest {

    @Test
    void processesAboutOneMegabyteOfText() {
        String line = "Patient denies fever and cough. BP 120/80. Metformin 500 mg BID.\n";
        int repeats = 950_000 / line.length();
        String text = line.repeat(repeats);

        try (PipelineFixture pipeline = PipelineFixture.seeded()) {
            long started = System.nanoTime();
            pipeline.run(text);
            long seconds = (System.nanoTime() - started) / 1_000_000_000;

            assertThat(text.length()).isGreaterThan(900_000);
            assertThat(pipeline.concepts()).hasSize(3 * repeats);
            assertThat(pipeline.negated()).hasSize(2 * repeats);
            assertThat(pipeline.affirmed()).hasSize(repeats);
            assertThat(pipeline.measurements()).hasSize(2 * repeats); // BP + dosage per line
            assertThat(pipeline.concepts()).allSatisfy(c -> assertThat(c.getEnd()).isLessThanOrEqualTo(text.length()));
            assertThat(seconds).as("processing time in seconds").isLessThan(60);
        }
    }

    @Test
    void aVeryLongSingleLineWithoutPunctuationStillWorks() {
        String text = "no known fever ".repeat(20_000);
        try (PipelineFixture pipeline = PipelineFixture.seeded()) {
            pipeline.run(text);
            assertThat(pipeline.concepts()).hasSize(20_000);
            assertThat(pipeline.concepts()).extracting(ClinicalConcept::getCode).containsOnly("R50.9");
        }
    }
}
