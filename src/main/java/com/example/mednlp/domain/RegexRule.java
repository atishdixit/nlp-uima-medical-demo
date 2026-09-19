package com.example.mednlp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A regex rule of group PHI or MEASUREMENT. {@code valueGroup} selects the capture group that
 * holds the extracted value (0 = the whole match).
 */
@Entity
@Table(name = "regex_rule")
public class RegexRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ruleName;

    @Column(nullable = false)
    private String ruleGroup;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String pattern;

    private int valueGroup;

    private String unit;

    private String description;

    private boolean enabled = true;

    protected RegexRule() {
    }

    public RegexRule(String ruleName, String ruleGroup, String category, String pattern,
                     int valueGroup, String unit, String description) {
        this.ruleName = ruleName;
        this.ruleGroup = ruleGroup;
        this.category = category;
        this.pattern = pattern;
        this.valueGroup = valueGroup;
        this.unit = unit;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getRuleName() { return ruleName; }
    public String getRuleGroup() { return ruleGroup; }
    public String getCategory() { return category; }
    public String getPattern() { return pattern; }
    public int getValueGroup() { return valueGroup; }
    public String getUnit() { return unit; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setPattern(String pattern) { this.pattern = pattern; }
}
