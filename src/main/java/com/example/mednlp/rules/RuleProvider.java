package com.example.mednlp.rules;

/** Supplies the current compiled rules. Called once per document, so refreshes take effect immediately. */
@FunctionalInterface
public interface RuleProvider {

    RuleSet current();
}
