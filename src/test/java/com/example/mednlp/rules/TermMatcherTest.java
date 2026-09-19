package com.example.mednlp.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mednlp.rules.TermMatcher.Match;
import java.util.List;
import org.junit.jupiter.api.Test;

class TermMatcherTest {

    private static TermMatcher<String> matcher() {
        return TermMatcher.<String>builder()
                .add("diabetes", false, "DM")
                .add("type 2 diabetes mellitus", false, "T2")
                .add("diabetes mellitus", false, "DMM")
                .add("MI", true, "MI")
                .add("c/o", false, "CO")
                .build();
    }

    private static List<String> texts(List<Match<String>> matches) {
        return matches.stream().map(Match::text).toList();
    }

    @Test
    void prefersTheLongestOverlappingTerm() {
        List<Match<String>> m = matcher().findAll("type 2 diabetes mellitus");
        assertThat(m).singleElement().satisfies(x -> assertThat(x.payload()).isEqualTo("T2"));
    }

    @Test
    void findsDisjointMatchesInOrder() {
        assertThat(texts(matcher().findAll("diabetes, MI and diabetes mellitus"))).containsExactly("diabetes", "MI", "diabetes mellitus");
    }

    @Test
    void caseSensitivityIsPerTerm() {
        assertThat(matcher().findAll("mi Mi mI")).isEmpty();
        assertThat(matcher().findAll("DIABETES")).hasSize(1);
    }

    @Test
    void termsWithPunctuationAreSupported() {
        assertThat(texts(matcher().findAll("pt c/o pain"))).containsExactly("c/o");
    }

    @Test
    void wordBoundariesAreEnforcedOnBothSides() {
        assertThat(matcher().findAll("prediabetes diabetesx 2diabetes")).isEmpty();
        assertThat(matcher().findAll("(diabetes)")).hasSize(1);
    }

    @Test
    void offsetsRefertoTheGivenText() {
        String text = "History: diabetes";
        Match<String> m = matcher().findAll(text).get(0);
        assertThat(text.substring(m.start(), m.end())).isEqualTo("diabetes");
    }

    @Test
    void emptyInputsAndEmptyMatchersAreSafe() {
        assertThat(matcher().findAll("")).isEmpty();
        assertThat(TermMatcher.<String>builder().build().findAll("diabetes")).isEmpty();
        assertThat(TermMatcher.<String>builder().add("  ", false, "x").add(null, false, "y").build().termCount()).isZero();
    }

    @Test
    void duplicateTermsKeepTheFirstPayload() {
        TermMatcher<String> m = TermMatcher.<String>builder().add("x-ray", false, "first").add("X-RAY", false, "second").build();
        assertThat(m.termCount()).isEqualTo(1);
        assertThat(m.findAll("an x-ray")).singleElement().satisfies(x -> assertThat(x.payload()).isEqualTo("first"));
    }

    @Test
    void tenThousandRepetitionsStayFastAndCorrect() {
        String text = "diabetes MI ".repeat(10_000);
        assertThat(matcher().findAll(text)).hasSize(20_000);
    }
}
