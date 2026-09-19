package com.example.mednlp.uima.annotators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** EC-14: sentence boundaries, the scope limit for negation. */
class SentenceAnnotatorTest {

    private static List<String> split(String text) {
        return SentenceAnnotator.split(text).stream().map(s -> text.substring(s[0], s[1])).toList();
    }

    @Test
    void splitsOnPeriodFollowedByCapital() {
        assertThat(split("Patient denies fever. Reports cough.")).containsExactly("Patient denies fever.", "Reports cough.");
    }

    @Test
    void doesNotSplitInsideDecimals() {
        assertThat(split("Temp 98.6 and BP 120/80.")).containsExactly("Temp 98.6 and BP 120/80.");
    }

    @Test
    void temperatureUnitLetterIsNotAnInitial() {
        assertThat(split("Temp 98.6 F. BP 120/80.")).containsExactly("Temp 98.6 F.", "BP 120/80.");
    }

    @Test
    void doesNotSplitAfterTitleAbbreviations() {
        assertThat(split("Seen by Dr. Smith today. No pain.")).containsExactly("Seen by Dr. Smith today.", "No pain.");
    }

    @Test
    void doesNotSplitAfterInitials() {
        assertThat(split("Seen by J. Smith.")).containsExactly("Seen by J. Smith.");
    }

    @Test
    void unitAbbreviationsMayEndASentence() {
        assertThat(split("Take metformin 500 mg b.i.d. Continue exercise.")).hasSize(2);
        assertThat(split("Take 5 mg. Continue exercise.")).hasSize(2);
    }

    @Test
    void doesNotSplitAfterListNumberAtSentenceStart() {
        assertThat(split("1. Aspirin 81 mg\n2. Metformin")).containsExactly("1. Aspirin 81 mg", "2. Metformin");
    }

    @Test
    void lowercaseContinuationIsNotABoundary() {
        assertThat(split("No fever. and no cough")).hasSize(1);
    }

    @Test
    void newlineAndSemicolonAreBoundaries() {
        assertThat(split("BP 120/80\nHR 72\n\nPlan: rest")).containsExactly("BP 120/80", "HR 72", "Plan: rest");
        assertThat(split("Denies fever; reports cough.")).containsExactly("Denies fever;", "reports cough.");
    }

    @Test
    void handlesWindowsLineEndings() {
        assertThat(split("Denies fever.\r\nReports cough.\r\n")).containsExactly("Denies fever.", "Reports cough.");
    }

    @Test
    void questionAndExclamationMarks() {
        assertThat(split("Fever? Cough!")).containsExactly("Fever?", "Cough!");
    }

    @Test
    void textWithoutTerminalPunctuationIsOneSentence() {
        assertThat(split("chest pain")).containsExactly("chest pain");
    }

    @Test
    void emptyWhitespaceAndPunctuationOnlyInputsYieldNothing() {
        assertThat(split("")).isEmpty();
        assertThat(split("   \n\t \n")).isEmpty();
        assertThat(split("...")).isEmpty();
        assertThat(split("- \n * \n")).isEmpty();
    }
}
