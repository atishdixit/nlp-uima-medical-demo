package com.example.mednlp.api;

import com.example.mednlp.rules.RuleSet;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Request/response records for the REST API. Offsets are 0-based UTF-16 indexes into the submitted text. */
public final class Dtos {

    private Dtos() {
    }

    @Schema(description = "Chart to analyse")
    public record AnalyzeRequest(
            @Schema(description = "Free-text medical chart", requiredMode = Schema.RequiredMode.REQUIRED) String text,
            @Schema(description = "Also return the text with PHI replaced by [TYPE] placeholders") boolean redactPhi,
            @Schema(description = "Also return the detected sentences") boolean includeSentences) {
    }

    public record SectionDto(String name, int begin, int end, String experiencer) {
    }

    public record SentenceDto(int begin, int end, String text) {
    }

    public record ConceptDto(int begin, int end, String text, String code, String codeSystem, String preferredName,
                             String category, boolean negated, String negationTrigger, String experiencer,
                             String section) {
    }

    public record PhiDto(String type, int begin, int end) {
    }

    public record MeasurementDto(String type, String value, String unit, int begin, int end, String text, String rule) {
    }

    public record Stats(long durationMs, Instant rulesLoadedAt, int rulesRejected) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnalyzeResponse(
            int textLength,
            List<SectionDto> sections,
            int sentenceCount,
            List<SentenceDto> sentences,
            List<ConceptDto> concepts,
            List<PhiDto> phi,
            List<MeasurementDto> measurements,
            String redactedText,
            Stats stats) {
    }

    public record RuleSummary(Instant loadedAt, int sectionRules, int regexRules, int concepts, int triggerTerms,
                              int negationTriggers, List<RuleSet.Rejected> rejected) {

        public static RuleSummary of(RuleSet rules) {
            return new RuleSummary(rules.loadedAt(), rules.sectionRules().size(), rules.regexRuleCount(),
                    rules.conceptCount(), rules.triggerTermCount(), rules.negationTriggerCount(), rules.rejected());
        }
    }
}
