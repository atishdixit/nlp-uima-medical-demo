package com.example.mednlp.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mednlp.rules.RuleDefinitions.ConceptDef;
import com.example.mednlp.rules.RuleDefinitions.NegationDef;
import com.example.mednlp.rules.RuleDefinitions.RegexDef;
import com.example.mednlp.rules.RuleDefinitions.SectionDef;
import com.example.mednlp.rules.RuleDefinitions.TermDef;
import com.example.mednlp.rules.RuleSet.Options;
import com.example.mednlp.rules.RuleSet.Rejected;
import java.util.List;
import org.junit.jupiter.api.Test;

/** EC-18..EC-22: bad or disabled rules must never break the pipeline. */
class RuleSetCompilerTest {

    private static final Options OPTIONS = new Options(6, 1000);

    private static RuleSet compileRegexes(RegexDef... regexes) {
        return RuleSetCompiler.compile(new RuleDefinitions(List.of(), List.of(regexes), List.of(), List.of()), OPTIONS);
    }

    private static RegexDef regex(String name, String group, String pattern, int valueGroup, boolean enabled) {
        return new RegexDef(name, group, "CAT", pattern, valueGroup, null, enabled);
    }

    private static List<String> reasons(RuleSet rules) {
        return rules.rejected().stream().map(Rejected::reason).toList();
    }

    @Test
    void theBundledSeedRulesAllCompile() {
        RuleSet rules = RuleSetCompiler.compile(SeedFile.load().toDefinitions(), OPTIONS);
        assertThat(rules.rejected()).isEmpty();
        assertThat(rules.conceptCount()).isGreaterThan(30);
        assertThat(rules.triggerTermCount()).isGreaterThan(40);
        assertThat(rules.negationTriggerCount()).isGreaterThan(40);
        assertThat(rules.regexRules("PHI")).hasSize(6);
        assertThat(rules.sectionRules()).hasSize(13);
    }

    @Test
    void invalidRegexIsRejectedNotFatal() {
        RuleSet rules = compileRegexes(regex("BAD", "PHI", "[unclosed", 0, true), regex("GOOD", "PHI", "\\d+", 0, true));
        assertThat(rules.regexRules("PHI")).extracting(RuleSet.RegexRule::name).containsExactly("GOOD");
        assertThat(rules.rejected()).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("BAD");
            assertThat(r.reason()).startsWith("invalid regex");
        });
    }

    @Test
    void nestedUnboundedQuantifiersAreRejected() {
        RuleSet rules = compileRegexes(
                regex("A", "PHI", "(a+)+$", 0, true),
                regex("B", "PHI", "(\\w*)*x", 0, true),
                regex("C", "PHI", "(a+){2,}", 0, true));
        assertThat(rules.regexRules("PHI")).isEmpty();
        assertThat(reasons(rules)).allMatch(r -> r.contains("catastrophic backtracking"));
    }

    @Test
    void boundedNestingIsAllowed() {
        RuleSet rules = compileRegexes(regex("OK", "PHI", "(?:[ \\t]+[A-Z][a-z]+){1,2}", 0, true));
        assertThat(rules.regexRules("PHI")).hasSize(1);
        assertThat(rules.rejected()).isEmpty();
    }

    @Test
    void emptyBlankAndOversizedPatternsAreRejected() {
        RuleSet rules = compileRegexes(
                regex("EMPTY", "PHI", "", 0, true),
                regex("BLANK", "PHI", "   ", 0, true),
                regex("NULL", "PHI", null, 0, true),
                regex("HUGE", "PHI", "a".repeat(RuleSetCompiler.MAX_PATTERN_LENGTH + 1), 0, true));
        assertThat(rules.regexRules("PHI")).isEmpty();
        assertThat(rules.rejected()).hasSize(4);
    }

    @Test
    void unknownGroupAndBadValueGroupAreRejected() {
        RuleSet rules = compileRegexes(
                regex("G", "SOMETHING", "\\d+", 0, true),
                regex("V", "MEASUREMENT", "(\\d+)", 2, true),
                regex("N", "MEASUREMENT", "(\\d+)", -1, true));
        assertThat(rules.regexRules("MEASUREMENT")).isEmpty();
        assertThat(reasons(rules)).anyMatch(r -> r.contains("unknown rule group"))
                .anyMatch(r -> r.contains("value group 2"));
        assertThat(rules.rejected()).hasSize(3);
    }

    @Test
    void ruleGroupIsCaseInsensitive() {
        RuleSet rules = compileRegexes(regex("LOWER", "phi", "\\d+", 0, true));
        assertThat(rules.regexRules("PHI")).hasSize(1);
    }

    @Test
    void disabledRulesAreIgnoredSilently() {
        RuleDefinitions defs = new RuleDefinitions(
                List.of(new SectionDef("S", "^x", 0, null, false)),
                List.of(regex("R", "PHI", "\\d+", 0, false)),
                List.of(new ConceptDef("C1", "SYS", "Concept", "DIAGNOSIS", false, List.of(new TermDef("term", false, true))),
                        new ConceptDef("C2", "SYS", "Concept 2", "DIAGNOSIS", true,
                                List.of(new TermDef("live", false, true), new TermDef("dead", false, false)))),
                List.of(new NegationDef("denies", "PRE", false)));
        RuleSet rules = RuleSetCompiler.compile(defs, OPTIONS);

        assertThat(rules.rejected()).isEmpty();
        assertThat(rules.sectionRules()).isEmpty();
        assertThat(rules.regexRules("PHI")).isEmpty();
        assertThat(rules.conceptCount()).isEqualTo(1);
        assertThat(rules.conceptMatcher().findAll("term live dead")).extracting(m -> m.text()).containsExactly("live");
        assertThat(rules.negationTriggerCount()).isZero();
    }

    @Test
    void invalidNegationTriggersAreRejected() {
        RuleDefinitions defs = new RuleDefinitions(List.of(), List.of(), List.of(), List.of(
                new NegationDef("weird", "SIDEWAYS", true),
                new NegationDef("nulltype", null, true),
                new NegationDef("  ", "PRE", true),
                new NegationDef("lower", "pre", true)));
        RuleSet rules = RuleSetCompiler.compile(defs, OPTIONS);
        assertThat(rules.negationTriggerCount()).isEqualTo(1); // "lower": types are case-insensitive
        assertThat(rules.rejected()).hasSize(3);
    }

    @Test
    void conceptsNeedACodeAndTermsNeedContent() {
        RuleDefinitions defs = new RuleDefinitions(List.of(), List.of(), List.of(
                new ConceptDef(null, "SYS", "No code", "DIAGNOSIS", true, List.of(new TermDef("x", false, true))),
                new ConceptDef("C", " ", "No system", "DIAGNOSIS", true, List.of(new TermDef("y", false, true))),
                new ConceptDef("C3", "SYS", "Fine", "DIAGNOSIS", true,
                        List.of(new TermDef("", false, true), new TermDef("t".repeat(201), false, true), new TermDef("ok", false, true)))),
                List.of());
        RuleSet rules = RuleSetCompiler.compile(defs, OPTIONS);
        assertThat(rules.conceptCount()).isEqualTo(1);
        assertThat(rules.triggerTermCount()).isEqualTo(1);
        assertThat(rules.rejected()).hasSize(4);
    }

    @Test
    void whenTwoConceptsShareATermTheFirstOneWins() {
        RuleDefinitions defs = new RuleDefinitions(List.of(), List.of(), List.of(
                new ConceptDef("A", "SYS", "First", "DIAGNOSIS", true, List.of(new TermDef("shared", false, true))),
                new ConceptDef("B", "SYS", "Second", "DIAGNOSIS", true, List.of(new TermDef("shared", false, true)))),
                List.of());
        RuleSet rules = RuleSetCompiler.compile(defs, OPTIONS);
        assertThat(rules.conceptMatcher().findAll("shared")).singleElement().satisfies(m -> assertThat(m.payload().code()).isEqualTo("A"));
    }

    @Test
    void emptyDefinitionsGiveAnEmptyButUsableRuleSet() {
        RuleSet rules = RuleSetCompiler.compile(RuleDefinitions.empty(), OPTIONS);
        assertThat(rules.rejected()).isEmpty();
        assertThat(rules.conceptMatcher().findAll("anything at all")).isEmpty();
        assertThat(rules.triggerMatcher().findAll("no")).isEmpty();
    }
}
