-- Rule metadata for the medical chart NLP pipeline.
-- Portable DDL: runs unchanged on MySQL 8 and on H2 (MODE=MySQL) used by tests / the "h2" profile.

CREATE TABLE section_header (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    pattern     VARCHAR(500) NOT NULL,
    priority    INT          NOT NULL DEFAULT 0,
    experiencer VARCHAR(16)  NOT NULL DEFAULT 'PATIENT',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_section_header_name UNIQUE (name)
);

CREATE TABLE regex_rule (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rule_name   VARCHAR(64)  NOT NULL,
    rule_group  VARCHAR(32)  NOT NULL,
    category    VARCHAR(64)  NOT NULL,
    pattern     VARCHAR(500) NOT NULL,
    value_group INT          NOT NULL DEFAULT 0,
    unit        VARCHAR(16)  NULL,
    description VARCHAR(255) NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_regex_rule_name UNIQUE (rule_name)
);

CREATE TABLE concept (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(32)  NOT NULL,
    code_system    VARCHAR(32)  NOT NULL,
    preferred_name VARCHAR(255) NOT NULL,
    category       VARCHAR(32)  NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_concept_code UNIQUE (code_system, code)
);

CREATE TABLE trigger_term (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    term           VARCHAR(200) NOT NULL,
    case_sensitive BOOLEAN      NOT NULL DEFAULT FALSE,
    concept_id     BIGINT       NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_trigger_term UNIQUE (term),
    CONSTRAINT fk_trigger_term_concept FOREIGN KEY (concept_id) REFERENCES concept (id)
);

CREATE TABLE negation_trigger (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    term         VARCHAR(100) NOT NULL,
    trigger_type VARCHAR(16)  NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_negation_trigger_term UNIQUE (term)
);

-- Audit trail: counts and timings only. Chart text is never persisted (PHI).
CREATE TABLE processing_run (
    id            BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at    TIMESTAMP NOT NULL,
    char_count    INT       NOT NULL,
    concept_count INT       NOT NULL,
    negated_count INT       NOT NULL,
    phi_count     INT       NOT NULL,
    duration_ms   BIGINT    NOT NULL
);
