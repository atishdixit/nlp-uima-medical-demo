package com.example.mednlp.rules;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Immutable, compiled snapshot of every rule the pipeline needs. Annotators read the current
 * snapshot per document, so swapping in a new one (cache refresh) never disturbs a running request.
 */
public final class RuleSet {

    public enum TriggerType { PRE, POST, PSEUDO, TERMINATION }

    public record SectionRule(String name, Pattern pattern, int priority, String experiencer) {
    }

    public record RegexRule(String name, String group, String category, Pattern pattern, int valueGroup, String unit) {
    }

    public record ConceptInfo(String code, String codeSystem, String preferredName, String category) {
    }

    /** A rule that was skipped at compile time, with the reason (surfaced by the rules summary API). */
    public record Rejected(String kind, String name, String reason) {
    }

    public record Options(int negationWindowTokens, long regexTimeoutMs) {
    }

    private final List<SectionRule> sectionRules;
    private final Map<String, List<RegexRule>> regexRulesByGroup;
    private final TermMatcher<ConceptInfo> conceptMatcher;
    private final TermMatcher<TriggerType> triggerMatcher;
    private final Options options;
    private final List<Rejected> rejected;
    private final Instant loadedAt;
    private final int conceptCount;

    RuleSet(List<SectionRule> sectionRules, Map<String, List<RegexRule>> regexRulesByGroup,
            TermMatcher<ConceptInfo> conceptMatcher, TermMatcher<TriggerType> triggerMatcher,
            Options options, List<Rejected> rejected, int conceptCount) {
        this.sectionRules = List.copyOf(sectionRules);
        this.regexRulesByGroup = regexRulesByGroup;
        this.conceptMatcher = conceptMatcher;
        this.triggerMatcher = triggerMatcher;
        this.options = options;
        this.rejected = List.copyOf(rejected);
        this.loadedAt = Instant.now();
        this.conceptCount = conceptCount;
    }

    public List<SectionRule> sectionRules() {
        return sectionRules;
    }

    public List<RegexRule> regexRules(String group) {
        return regexRulesByGroup.getOrDefault(group, List.of());
    }

    public TermMatcher<ConceptInfo> conceptMatcher() {
        return conceptMatcher;
    }

    public TermMatcher<TriggerType> triggerMatcher() {
        return triggerMatcher;
    }

    public Options options() {
        return options;
    }

    public List<Rejected> rejected() {
        return rejected;
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public int conceptCount() {
        return conceptCount;
    }

    public int triggerTermCount() {
        return conceptMatcher.termCount();
    }

    public int negationTriggerCount() {
        return triggerMatcher.termCount();
    }

    public int regexRuleCount() {
        return regexRulesByGroup.values().stream().mapToInt(List::size).sum();
    }
}
