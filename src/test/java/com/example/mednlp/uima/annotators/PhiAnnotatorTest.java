package com.example.mednlp.uima.annotators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.mednlp.rules.RuleDefinitions;
import com.example.mednlp.rules.RuleDefinitions.RegexDef;
import com.example.mednlp.types.PhiSpan;
import com.example.mednlp.uima.PipelineFixture;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** UC-11 PHI detection and EC-28 (overlaps, false positives). */
class PhiAnnotatorTest {

    private static PipelineFixture pipeline;

    @BeforeAll
    static void setUp() {
        pipeline = PipelineFixture.seeded();
    }

    @AfterAll
    static void tearDown() {
        pipeline.close();
    }

    private static List<String> phiOf(PipelineFixture p, String type) {
        return p.phi().stream().filter(s -> s.getPhiType().equals(type)).map(PhiSpan::getCoveredText).toList();
    }

    @Test
    void detectsIdentifiers() {
        pipeline.run("MRN: 12345678, SSN 123-45-6789, DOB: 04/12/1968, mail john.doe@example.com.");
        assertThat(phiOf(pipeline, "MRN")).containsExactly("MRN: 12345678");
        assertThat(phiOf(pipeline, "SSN")).containsExactly("123-45-6789");
        assertThat(phiOf(pipeline, "DOB")).containsExactly("DOB: 04/12/1968");
        assertThat(phiOf(pipeline, "EMAIL")).containsExactly("john.doe@example.com");
    }

    @Test
    void detectsPhoneNumberFormats() {
        pipeline.run("Call (555) 123-4567 or 555-123-4567 or +1 555.123.4567 today.");
        assertThat(phiOf(pipeline, "PHONE")).containsExactly("(555) 123-4567", "555-123-4567", "+1 555.123.4567");
    }

    @Test
    void detectsPatientNamesAfterALabel() {
        pipeline.run("Patient: John A. Smith\nPatient Name: Maria Garcia\nPatient: unknown");
        assertThat(phiOf(pipeline, "NAME")).containsExactly("Patient: John A. Smith", "Patient Name: Maria Garcia");
    }

    @Test
    void doesNotFlagClinicalNumbers() {
        pipeline.run("BP 120/80, HR 72, seen on 2024-01-15, order 1234-56-7890, glucose 110 mg/dL.");
        assertThat(pipeline.phi()).isEmpty();
    }

    @Test
    void trailingPunctuationIsNotPartOfTheEmail() {
        pipeline.run("Contact a@b.com.");
        assertThat(phiOf(pipeline, "EMAIL")).containsExactly("a@b.com");
    }

    @Test
    void overlappingMatchesAreReportedOnceUsingTheLongest() {
        RuleDefinitions defs = new RuleDefinitions(List.of(),
                List.of(new RegexDef("SHORT", "PHI", "SHORT", "\\d{3}-\\d{4}", 0, null, true),
                        new RegexDef("LONG", "PHI", "LONG", "\\d{3}-\\d{3}-\\d{4}", 0, null, true)),
                List.of(), List.of());
        try (PipelineFixture p = PipelineFixture.with(defs)) {
            p.run("Number 555-123-4567 only");
            assertThat(p.phi()).extracting(PhiSpan::getPhiType, PhiSpan::getCoveredText)
                    .containsExactly(tuple("LONG", "555-123-4567"));
        }
    }

    @Test
    void samplePhiChartIsFullyDetected() throws Exception {
        pipeline.run(SampleCharts.read("phi-heavy.txt"));
        assertThat(pipeline.phi()).extracting(PhiSpan::getPhiType)
                .containsExactly("NAME", "MRN", "DOB", "SSN", "EMAIL", "PHONE", "PHONE", "PHONE");
    }
}
