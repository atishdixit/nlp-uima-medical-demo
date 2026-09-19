package com.example.mednlp.uima.annotators;

import com.example.mednlp.rules.RuleSet;
import com.example.mednlp.rules.RuleSet.RegexRule;
import com.example.mednlp.rules.RuleSetCompiler;
import com.example.mednlp.rules.SafeRegex;
import com.example.mednlp.types.Measurement;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 6. Extracts vitals, lab values and dosages using the MEASUREMENT regex rules. The rule's
 * {@code value_group} chooses which capture group becomes the value (0 = whole match).
 */
public class MeasurementAnnotator extends RuleAwareAnnotator {

    private static final Logger log = LoggerFactory.getLogger(MeasurementAnnotator.class);

    @Override
    public void process(JCas jcas) throws AnalysisEngineProcessException {
        RuleSet rules = rules();
        String text = jcas.getDocumentText();
        for (RegexRule rule : rules.regexRules(RuleSetCompiler.GROUP_MEASUREMENT)) {
            try {
                for (SafeRegex.Hit hit : SafeRegex.findAll(rule.pattern(), text, rule.valueGroup(), rules.options().regexTimeoutMs())) {
                    Measurement m = new Measurement(jcas, hit.start(), hit.end());
                    m.setMeasurementType(rule.category());
                    m.setValue(text.substring(hit.valueStart(), hit.valueEnd()).strip());
                    m.setUnit(rule.unit());
                    m.setRuleName(rule.name());
                    m.addToIndexes();
                }
            } catch (SafeRegex.RegexTimeoutException e) {
                log.warn("Measurement rule '{}' timed out and was skipped for this document", rule.name());
            }
        }
    }
}
