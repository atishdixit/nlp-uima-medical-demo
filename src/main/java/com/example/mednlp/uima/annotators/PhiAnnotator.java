package com.example.mednlp.uima.annotators;

import com.example.mednlp.rules.RuleSet;
import com.example.mednlp.rules.RuleSet.RegexRule;
import com.example.mednlp.rules.RuleSetCompiler;
import com.example.mednlp.rules.SafeRegex;
import com.example.mednlp.types.PhiSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 3. Flags protected health information (MRN, SSN, phone, e-mail, DOB, patient name) using
 * the PHI regex rules. Overlapping hits from different rules are resolved to the longest one so a
 * value is reported once.
 */
public class PhiAnnotator extends RuleAwareAnnotator {

    private static final Logger log = LoggerFactory.getLogger(PhiAnnotator.class);

    private record Span(int start, int end, String type) {
    }

    @Override
    public void process(JCas jcas) throws AnalysisEngineProcessException {
        RuleSet rules = rules();
        String text = jcas.getDocumentText();

        List<Span> spans = new ArrayList<>();
        for (RegexRule rule : rules.regexRules(RuleSetCompiler.GROUP_PHI)) {
            try {
                for (SafeRegex.Hit hit : SafeRegex.findAll(rule.pattern(), text, 0, rules.options().regexTimeoutMs())) {
                    spans.add(new Span(hit.start(), hit.end(), rule.category()));
                }
            } catch (SafeRegex.RegexTimeoutException e) {
                log.warn("PHI rule '{}' timed out and was skipped for this document", rule.name());
            }
        }

        spans.sort(Comparator.comparingInt((Span s) -> s.end() - s.start()).reversed().thenComparingInt(Span::start));
        TreeMap<Integer, Integer> accepted = new TreeMap<>();
        for (Span s : spans) {
            var before = accepted.floorEntry(s.start());
            var after = accepted.ceilingEntry(s.start());
            boolean overlaps = (before != null && before.getValue() > s.start())
                    || (after != null && after.getKey() < s.end());
            if (overlaps) {
                continue;
            }
            accepted.put(s.start(), s.end());
            PhiSpan phi = new PhiSpan(jcas, s.start(), s.end());
            phi.setPhiType(s.type());
            phi.addToIndexes();
        }
    }
}
