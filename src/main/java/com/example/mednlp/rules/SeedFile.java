package com.example.mednlp.rules;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.mednlp.rules.RuleDefinitions.ConceptDef;
import com.example.mednlp.rules.RuleDefinitions.NegationDef;
import com.example.mednlp.rules.RuleDefinitions.RegexDef;
import com.example.mednlp.rules.RuleDefinitions.SectionDef;
import com.example.mednlp.rules.RuleDefinitions.TermDef;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The bundled seed rules ({@code seed/medical-rules.json}). Loaded into empty MySQL tables at
 * startup, and used directly by unit tests so annotators can be tested without a database.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedFile(
        List<Section> sectionHeaders,
        List<Regex> regexRules,
        List<Concept> concepts,
        List<Negation> negationTriggers) {

    public static final String RESOURCE = "seed/medical-rules.json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(String name, String pattern, int priority, String experiencer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Regex(String ruleName, String ruleGroup, String category, String pattern,
                        int valueGroup, String unit, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Concept(String code, String codeSystem, String preferredName, String category,
                          List<String> terms, List<String> caseSensitiveTerms) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Negation(String term, String type) {
    }

    public static SeedFile load() {
        try (InputStream in = SeedFile.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Seed resource not found on classpath: " + RESOURCE);
            }
            return new ObjectMapper().readValue(in, SeedFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    /** Converts the seed to rule definitions (every rule enabled). */
    public RuleDefinitions toDefinitions() {
        List<SectionDef> sections = sectionHeaders == null ? List.of() : sectionHeaders.stream()
                .map(s -> new SectionDef(s.name(), s.pattern(), s.priority(), s.experiencer(), true)).toList();
        List<RegexDef> regexes = regexRules == null ? List.of() : regexRules.stream()
                .map(r -> new RegexDef(r.ruleName(), r.ruleGroup(), r.category(), r.pattern(),
                        r.valueGroup(), r.unit(), true)).toList();
        List<ConceptDef> defs = new ArrayList<>();
        for (Concept c : concepts == null ? List.<Concept>of() : concepts) {
            List<TermDef> terms = new ArrayList<>();
            orEmpty(c.terms()).forEach(t -> terms.add(new TermDef(t, false, true)));
            orEmpty(c.caseSensitiveTerms()).forEach(t -> terms.add(new TermDef(t, true, true)));
            defs.add(new ConceptDef(c.code(), c.codeSystem(), c.preferredName(), c.category(), true, terms));
        }
        List<NegationDef> negations = negationTriggers == null ? List.of() : negationTriggers.stream()
                .map(n -> new NegationDef(n.term(), n.type(), true)).toList();
        return new RuleDefinitions(sections, regexes, defs, negations);
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }
}
