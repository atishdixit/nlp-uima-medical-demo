package com.example.mednlp.rules;

/** Where raw rule definitions come from (MySQL in production; a stub in tests). */
@FunctionalInterface
public interface RuleDefinitionSource {

    RuleDefinitions load();
}
