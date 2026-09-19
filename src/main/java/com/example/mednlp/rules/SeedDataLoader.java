package com.example.mednlp.rules;

import com.example.mednlp.config.MedNlpProperties;
import com.example.mednlp.domain.Concept;
import com.example.mednlp.domain.ConceptRepository;
import com.example.mednlp.domain.NegationTrigger;
import com.example.mednlp.domain.NegationTriggerRepository;
import com.example.mednlp.domain.RegexRule;
import com.example.mednlp.domain.RegexRuleRepository;
import com.example.mednlp.domain.SectionHeader;
import com.example.mednlp.domain.SectionHeaderRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Fills EMPTY rule tables from the bundled seed JSON so a fresh database works out of the box.
 * Tables that already contain rows are never touched, so operator edits survive restarts.
 * (Seeding from JSON instead of SQL keeps regex escaping identical on MySQL and H2.)
 */
@Component
public class SeedDataLoader implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final MedNlpProperties props;
    private final SectionHeaderRepository sections;
    private final RegexRuleRepository regexes;
    private final ConceptRepository concepts;
    private final NegationTriggerRepository negations;

    public SeedDataLoader(MedNlpProperties props, SectionHeaderRepository sections, RegexRuleRepository regexes,
                          ConceptRepository concepts, NegationTriggerRepository negations) {
        this.props = props;
        this.sections = sections;
        this.regexes = regexes;
        this.concepts = concepts;
        this.negations = negations;
    }

    @Override
    public void afterPropertiesSet() {
        if (!props.seedEnabled()) {
            log.info("Rule seeding disabled (mednlp.seed-enabled=false)");
            return;
        }
        SeedFile seed = SeedFile.load();
        if (sections.count() == 0) {
            List<SectionHeader> rows = seed.sectionHeaders().stream()
                    .map(s -> new SectionHeader(s.name(), s.pattern(), s.priority(), s.experiencer())).toList();
            sections.saveAll(rows);
            log.info("Seeded {} section headers", rows.size());
        }
        if (regexes.count() == 0) {
            List<RegexRule> rows = seed.regexRules().stream()
                    .map(r -> new RegexRule(r.ruleName(), r.ruleGroup(), r.category(), r.pattern(),
                            r.valueGroup(), r.unit(), r.description())).toList();
            regexes.saveAll(rows);
            log.info("Seeded {} regex rules", rows.size());
        }
        if (concepts.count() == 0) {
            int terms = 0;
            for (SeedFile.Concept c : seed.concepts()) {
                Concept concept = new Concept(c.code(), c.codeSystem(), c.preferredName(), c.category());
                if (c.terms() != null) {
                    c.terms().forEach(t -> concept.addTerm(t, false));
                }
                if (c.caseSensitiveTerms() != null) {
                    c.caseSensitiveTerms().forEach(t -> concept.addTerm(t, true));
                }
                terms += concept.getTerms().size();
                concepts.save(concept);
            }
            log.info("Seeded {} concepts with {} trigger terms", seed.concepts().size(), terms);
        }
        if (negations.count() == 0) {
            List<NegationTrigger> rows = seed.negationTriggers().stream()
                    .map(n -> new NegationTrigger(n.term(), n.type())).toList();
            negations.saveAll(rows);
            log.info("Seeded {} negation triggers", rows.size());
        }
    }
}
