package com.example.mednlp.rules;

import com.example.mednlp.domain.ConceptRepository;
import com.example.mednlp.domain.NegationTriggerRepository;
import com.example.mednlp.domain.RegexRuleRepository;
import com.example.mednlp.domain.SectionHeaderRepository;
import com.example.mednlp.rules.RuleDefinitions.ConceptDef;
import com.example.mednlp.rules.RuleDefinitions.NegationDef;
import com.example.mednlp.rules.RuleDefinitions.RegexDef;
import com.example.mednlp.rules.RuleDefinitions.SectionDef;
import com.example.mednlp.rules.RuleDefinitions.TermDef;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads every rule table from MySQL. Disabled rows are passed on and skipped by the compiler. */
@Component
public class DbRuleDefinitionSource implements RuleDefinitionSource {

    private final SectionHeaderRepository sections;
    private final RegexRuleRepository regexes;
    private final ConceptRepository concepts;
    private final NegationTriggerRepository negations;

    public DbRuleDefinitionSource(SectionHeaderRepository sections, RegexRuleRepository regexes,
                                  ConceptRepository concepts, NegationTriggerRepository negations) {
        this.sections = sections;
        this.regexes = regexes;
        this.concepts = concepts;
        this.negations = negations;
    }

    @Override
    @Transactional(readOnly = true)
    public RuleDefinitions load() {
        List<SectionDef> sectionDefs = sections.findAll().stream()
                .map(s -> new SectionDef(s.getName(), s.getPattern(), s.getPriority(), s.getExperiencer(), s.isEnabled()))
                .toList();
        List<RegexDef> regexDefs = regexes.findAll().stream()
                .map(r -> new RegexDef(r.getRuleName(), r.getRuleGroup(), r.getCategory(), r.getPattern(),
                        r.getValueGroup(), r.getUnit(), r.isEnabled()))
                .toList();
        List<ConceptDef> conceptDefs = concepts.findAllWithTerms().stream()
                .map(c -> new ConceptDef(c.getCode(), c.getCodeSystem(), c.getPreferredName(), c.getCategory(),
                        c.isEnabled(),
                        c.getTerms().stream().map(t -> new TermDef(t.getTerm(), t.isCaseSensitive(), t.isEnabled())).toList()))
                .toList();
        List<NegationDef> negationDefs = negations.findAll().stream()
                .map(n -> new NegationDef(n.getTerm(), n.getTriggerType(), n.isEnabled()))
                .toList();
        return new RuleDefinitions(sectionDefs, regexDefs, conceptDefs, negationDefs);
    }
}
