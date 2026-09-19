package com.example.mednlp.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mednlp.rules.SafeRegex.Hit;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** EC-20 and EC-29: time-boxed regex execution and zero-length matches. */
class SafeRegexTest {

    @Test
    void abortsARunawayPatternWithinItsBudget() {
        Pattern evil = Pattern.compile("(.*a){20}b"); // exponential on a long run of a's
        String text = "a".repeat(60);

        long started = System.nanoTime();
        assertThatThrownBy(() -> SafeRegex.findAll(evil, text, 0, 200))
                .isInstanceOf(SafeRegex.RegexTimeoutException.class);
        long millis = (System.nanoTime() - started) / 1_000_000;
        assertThat(millis).isLessThan(5_000);
    }

    @Test
    void normalPatternsRunToCompletion() {
        List<Hit> hits = SafeRegex.findAll(Pattern.compile("\\d+"), "a1 b22 c333", 0, 1000);
        assertThat(hits).extracting(Hit::start, Hit::end).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, 2), org.assertj.core.groups.Tuple.tuple(4, 6),
                org.assertj.core.groups.Tuple.tuple(8, 11));
    }

    @Test
    void zeroLengthMatchesAreSkipped() {
        List<Hit> hits = SafeRegex.findAll(Pattern.compile("a*"), "baab", 0, 1000);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).start()).isEqualTo(1);
        assertThat(hits.get(0).end()).isEqualTo(3);
    }

    @Test
    void valueGroupSelectsTheCapturedRegion() {
        Hit hit = SafeRegex.findAll(Pattern.compile("HR (\\d+)"), "seen HR 72 today", 1, 1000).get(0);
        assertThat("seen HR 72 today".substring(hit.valueStart(), hit.valueEnd())).isEqualTo("72");
        assertThat("seen HR 72 today".substring(hit.start(), hit.end())).isEqualTo("HR 72");
    }

    @Test
    void aValueGroupThatDidNotParticipateFallsBackToTheWholeMatch() {
        Hit hit = SafeRegex.findAll(Pattern.compile("(\\d+)?x"), "x", 1, 1000).get(0);
        assertThat(hit.valueStart()).isEqualTo(hit.start());
        assertThat(hit.valueEnd()).isEqualTo(hit.end());
    }
}
