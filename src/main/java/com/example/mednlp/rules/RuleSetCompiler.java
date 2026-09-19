package com.example.mednlp.rules;

import com.example.mednlp.rules.RuleDefinitions.ConceptDef;
import com.example.mednlp.rules.RuleDefinitions.NegationDef;
import com.example.mednlp.rules.RuleDefinitions.RegexDef;
import com.example.mednlp.rules.RuleDefinitions.SectionDef;
import com.example.mednlp.rules.RuleDefinitions.TermDef;
import com.example.mednlp.rules.RuleSet.ConceptInfo;
import com.example.mednlp.rules.RuleSet.Options;
import com.example.mednlp.rules.RuleSet.Rejected;
import com.example.mednlp.rules.RuleSet.RegexRule;
import com.example.mednlp.rules.RuleSet.SectionRule;
import com.example.mednlp.rules.RuleSet.TriggerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates and compiles {@link RuleDefinitions} into a {@link RuleSet}. A bad rule never breaks
 * the pipeline: it is skipped, logged, and reported in {@link RuleSet#rejected()}.
 */
public final class RuleSetCompiler {

    public static final String GROUP_PHI = "PHI";
    public static final String GROUP_MEASUREMENT = "MEASUREMENT";

    static final int MAX_PATTERN_LENGTH = 500;
    static final int MAX_TERM_LENGTH = 200;
    private static final Set<String> REGEX_GROUPS = Set.of(GROUP_PHI, GROUP_MEASUREMENT);

    /**
     * Heuristic for the classic catastrophic shapes "(x+)+", "(x*)*" and "(x+){2,}": a group that
     * contains an unbounded quantifier and is itself repeated without bound. The runtime timeout in
     * {@link SafeRegex} is the second line of defence for shapes this does not catch, e.g. (a|aa)+.
     */
    private static final Pattern NESTED_QUANTIFIER =
            Pattern.compile("\\((?:[^()\\\\]|\\\\.)*(?:[+*]|\\{\\d+,\\})(?:[^()\\\\]|\\\\.)*\\)\\s*(?:[+*]|\\{\\d+,\\})");

    private static final Logger log = LoggerFactory.getLogger(RuleSetCompiler.class);

    private RuleSetCompiler() {
    }

    public static RuleSet compile(RuleDefinitions defs, Options options) {
        List<Rejected> rejected = new ArrayList<>();

        List<SectionRule> sections = new ArrayList<>();
        for (SectionDef d : defs.sections()) {
            if (!d.enabled()) {
                continue;
            }
            Pattern p = compilePattern("SECTION", d.name(), d.pattern(), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE, rejected);
            if (p != null) {
                String experiencer = d.experiencer() == null || d.experiencer().isBlank()
                        ? "PATIENT" : d.experiencer().strip().toUpperCase(Locale.ROOT);
                sections.add(new SectionRule(d.name(), p, d.priority(), experiencer));
            }
        }

        Map<String, List<RegexRule>> regexByGroup = new HashMap<>();
        for (RegexDef d : defs.regexes()) {
            if (!d.enabled()) {
                continue;
            }
            String group = d.ruleGroup() == null ? "" : d.ruleGroup().strip().toUpperCase(Locale.ROOT);
            if (!REGEX_GROUPS.contains(group)) {
                reject(rejected, "REGEX", d.ruleName(), "unknown rule group '" + d.ruleGroup() + "'");
                continue;
            }
            Pattern p = compilePattern("REGEX", d.ruleName(), d.pattern(), 0, rejected);
            if (p == null) {
                continue;
            }
            if (d.valueGroup() < 0 || d.valueGroup() > p.matcher("").groupCount()) {
                reject(rejected, "REGEX", d.ruleName(), "value group " + d.valueGroup() + " does not exist in the pattern");
                continue;
            }
            regexByGroup.computeIfAbsent(group, k -> new ArrayList<>())
                    .add(new RegexRule(d.ruleName(), group, d.category(), p, d.valueGroup(), d.unit()));
        }

        TermMatcher.Builder<ConceptInfo> concepts = TermMatcher.builder();
        int conceptCount = 0;
        for (ConceptDef c : defs.concepts()) {
            if (!c.enabled()) {
                continue;
            }
            if (isBlank(c.code()) || isBlank(c.codeSystem())) {
                reject(rejected, "CONCEPT", c.preferredName(), "code and code system are required");
                continue;
            }
            ConceptInfo info = new ConceptInfo(c.code(), c.codeSystem(), c.preferredName(), c.category());
            conceptCount++;
            for (TermDef t : c.terms()) {
                if (!t.enabled()) {
                    continue;
                }
                if (isBlank(t.term()) || t.term().length() > MAX_TERM_LENGTH) {
                    reject(rejected, "TERM", c.code(), "blank or longer than " + MAX_TERM_LENGTH + " characters");
                    continue;
                }
                concepts.add(t.term(), t.caseSensitive(), info);
            }
        }

        TermMatcher.Builder<TriggerType> triggers = TermMatcher.builder();
        for (NegationDef n : defs.negations()) {
            if (!n.enabled()) {
                continue;
            }
            if (isBlank(n.term()) || n.term().length() > MAX_TERM_LENGTH) {
                reject(rejected, "NEGATION", n.term(), "blank or longer than " + MAX_TERM_LENGTH + " characters");
                continue;
            }
            try {
                TriggerType type = TriggerType.valueOf(n.type().strip().toUpperCase(Locale.ROOT));
                triggers.add(n.term(), false, type);
            } catch (IllegalArgumentException | NullPointerException e) {
                reject(rejected, "NEGATION", n.term(), "unknown trigger type '" + n.type() + "'");
            }
        }

        sections.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return new RuleSet(sections, regexByGroup, concepts.build(), triggers.build(), options, rejected, conceptCount);
    }

    private static Pattern compilePattern(String kind, String name, String pattern, int flags, List<Rejected> rejected) {
        if (isBlank(pattern)) {
            reject(rejected, kind, name, "empty pattern");
            return null;
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            reject(rejected, kind, name, "pattern longer than " + MAX_PATTERN_LENGTH + " characters");
            return null;
        }
        if (NESTED_QUANTIFIER.matcher(pattern).find()) {
            reject(rejected, kind, name, "nested unbounded quantifiers (possible catastrophic backtracking)");
            return null;
        }
        try {
            return Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            reject(rejected, kind, name, "invalid regex: " + e.getDescription());
            return null;
        }
    }

    private static void reject(List<Rejected> rejected, String kind, String name, String reason) {
        log.warn("Skipping {} rule '{}': {}", kind, name, reason);
        rejected.add(new Rejected(kind, name, reason));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
