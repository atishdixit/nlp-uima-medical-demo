package com.example.mednlp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "section_header")
public class SectionHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String pattern;

    private int priority;

    @Column(nullable = false)
    private String experiencer = "PATIENT";

    private boolean enabled = true;

    protected SectionHeader() {
    }

    public SectionHeader(String name, String pattern, int priority, String experiencer) {
        this.name = name;
        this.pattern = pattern;
        this.priority = priority;
        this.experiencer = experiencer == null ? "PATIENT" : experiencer;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPattern() { return pattern; }
    public int getPriority() { return priority; }
    public String getExperiencer() { return experiencer; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setPattern(String pattern) { this.pattern = pattern; }
}
