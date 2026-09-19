package com.example.mednlp.uima.annotators;

import com.example.mednlp.rules.RuleProviderRegistry;
import com.example.mednlp.rules.RuleSet;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;

/**
 * Base class for annotators driven by database rules. The only configuration is the id of the
 * {@link com.example.mednlp.rules.RuleProvider} to read; the current {@link RuleSet} is fetched once
 * per document so a cache refresh applies to the very next chart without rebuilding any engine.
 */
public abstract class RuleAwareAnnotator extends JCasAnnotator_ImplBase {

    public static final String PARAM_RULE_PROVIDER_ID = "ruleProviderId";

    @ConfigurationParameter(name = PARAM_RULE_PROVIDER_ID, mandatory = true)
    private String ruleProviderId;

    protected RuleSet rules() {
        return RuleProviderRegistry.get(ruleProviderId).current();
    }
}
