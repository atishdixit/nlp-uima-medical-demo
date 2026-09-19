package com.example.mednlp.uima.annotators;

import com.example.mednlp.rules.RuleSet;
import com.example.mednlp.rules.RuleSet.SectionRule;
import com.example.mednlp.rules.SafeRegex;
import com.example.mednlp.types.ChartSection;
import java.util.Map;
import java.util.TreeMap;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 1. Finds section headers (HPI, MEDICATIONS, PLAN ...) with the regexes in
 * {@code section_header}. A section runs from its header to the next header; text before the first
 * header is left unsectioned. If several rules match at the same position the highest priority wins.
 */
public class SectionAnnotator extends RuleAwareAnnotator {

    private static final Logger log = LoggerFactory.getLogger(SectionAnnotator.class);

    @Override
    public void process(JCas jcas) throws AnalysisEngineProcessException {
        RuleSet rules = rules();
        String text = jcas.getDocumentText();

        TreeMap<Integer, SectionRule> headers = new TreeMap<>();
        for (SectionRule rule : rules.sectionRules()) { // already sorted by priority, highest first
            try {
                for (SafeRegex.Hit hit : SafeRegex.findAll(rule.pattern(), text, 0, rules.options().regexTimeoutMs())) {
                    headers.putIfAbsent(hit.start(), rule);
                }
            } catch (SafeRegex.RegexTimeoutException e) {
                log.warn("Section rule '{}' timed out and was skipped for this document", rule.name());
            }
        }

        Integer[] starts = headers.keySet().toArray(new Integer[0]);
        for (int i = 0; i < starts.length; i++) {
            int begin = starts[i];
            int end = i + 1 < starts.length ? starts[i + 1] : text.length();
            while (end > begin && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            SectionRule rule = headers.get(begin);
            ChartSection section = new ChartSection(jcas, begin, end);
            section.setName(rule.name());
            section.setExperiencer(rule.experiencer());
            section.addToIndexes();
        }
    }
}
