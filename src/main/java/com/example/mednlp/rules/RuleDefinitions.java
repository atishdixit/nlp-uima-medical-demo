package com.example.mednlp.rules;

import java.util.List;

/**
 * Raw, uncompiled rule data. It has the same shape whether it came from MySQL or from the seed
 * JSON, and {@link RuleSetCompiler} is the single place that validates and compiles it.
 */
public record RuleDefinitions(
        List<SectionDef> sections,
        List<RegexDef> regexes,
        List<ConceptDef> concepts,
        List<NegationDef> negations) {

    public record SectionDef(String name, String pattern, int priority, String experiencer, boolean enabled) {
    }

    public record RegexDef(String ruleName, String ruleGroup, String category, String pattern,
                           int valueGroup, String unit, boolean enabled) {
    }

    public record TermDef(String term, boolean caseSensitive, boolean enabled) {
    }

    public record ConceptDef(String code, String codeSystem, String preferredName, String category,
                             boolean enabled, List<TermDef> terms) {
    }

    public record NegationDef(String term, String type, boolean enabled) {
    }

    public static RuleDefinitions empty() {
        return new RuleDefinitions(List.of(), List.of(), List.of(), List.of());
    }
}
