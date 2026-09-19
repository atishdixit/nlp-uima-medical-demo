package com.example.mednlp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** NegEx-style trigger. {@code triggerType} is PRE, POST, PSEUDO or TERMINATION (validated when compiled). */
@Entity
@Table(name = "negation_trigger")
public class NegationTrigger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String term;

    @Column(nullable = false)
    private String triggerType;

    private boolean enabled = true;

    protected NegationTrigger() {
    }

    public NegationTrigger(String term, String triggerType) {
        this.term = term;
        this.triggerType = triggerType;
    }

    public Long getId() { return id; }
    public String getTerm() { return term; }
    public String getTriggerType() { return triggerType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
