package com.example.mednlp.service;

import com.example.mednlp.api.Dtos.AnalyzeResponse;
import com.example.mednlp.api.Dtos.ConceptDto;
import com.example.mednlp.api.Dtos.MeasurementDto;
import com.example.mednlp.api.Dtos.PhiDto;
import com.example.mednlp.api.Dtos.SectionDto;
import com.example.mednlp.api.Dtos.SentenceDto;
import com.example.mednlp.api.Dtos.Stats;
import com.example.mednlp.config.MedNlpProperties;
import com.example.mednlp.domain.ProcessingRun;
import com.example.mednlp.domain.ProcessingRunRepository;
import com.example.mednlp.rules.RuleService;
import com.example.mednlp.rules.RuleSet;
import com.example.mednlp.types.ChartSection;
import com.example.mednlp.types.ChartSentence;
import com.example.mednlp.types.ClinicalConcept;
import com.example.mednlp.types.Measurement;
import com.example.mednlp.types.PhiSpan;
import com.example.mednlp.uima.EnginePool;
import java.util.ArrayList;
import java.util.List;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Validates input, runs the UIMA pipeline through the engine pool, and maps the JCas to DTOs. */
@Service
public class ChartAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ChartAnalysisService.class);

    private final EnginePool pool;
    private final RuleService ruleService;
    private final ProcessingRunRepository runs;
    private final MedNlpProperties props;

    public ChartAnalysisService(EnginePool pool, RuleService ruleService, ProcessingRunRepository runs,
                                MedNlpProperties props) {
        this.pool = pool;
        this.ruleService = ruleService;
        this.runs = runs;
        this.props = props;
    }

    public AnalyzeResponse analyze(String text, boolean redactPhi, boolean includeSentences) {
        if (text == null || text.isBlank()) {
            throw new InvalidChartException("The chart text must not be empty");
        }
        if (text.length() > props.maxChars()) {
            throw new ChartTooLargeException("The chart has " + text.length() + " characters; the limit is " + props.maxChars());
        }

        long started = System.nanoTime();
        AnalyzeResponse response = pool.analyze(text, jcas -> map(jcas, text, redactPhi, includeSentences, started));
        audit(response);
        return response;
    }

    private AnalyzeResponse map(JCas jcas, String text, boolean redactPhi, boolean includeSentences, long startedNanos) {
        List<SectionDto> sections = JCasUtil.select(jcas, ChartSection.class).stream()
                .map(s -> new SectionDto(s.getName(), s.getBegin(), s.getEnd(), s.getExperiencer())).toList();

        List<ChartSentence> sentenceAnnotations = new ArrayList<>(JCasUtil.select(jcas, ChartSentence.class));
        List<SentenceDto> sentences = includeSentences
                ? sentenceAnnotations.stream().map(s -> new SentenceDto(s.getBegin(), s.getEnd(), s.getCoveredText())).toList()
                : null;

        List<ConceptDto> concepts = JCasUtil.select(jcas, ClinicalConcept.class).stream()
                .map(c -> new ConceptDto(c.getBegin(), c.getEnd(), c.getCoveredText(), c.getCode(), c.getCodeSystem(),
                        c.getPreferredName(), c.getCategory(), c.getNegated(), c.getNegationTrigger(),
                        c.getExperiencer(), c.getSectionName())).toList();

        List<PhiSpan> phiSpans = new ArrayList<>(JCasUtil.select(jcas, PhiSpan.class));
        List<PhiDto> phi = phiSpans.stream().map(p -> new PhiDto(p.getPhiType(), p.getBegin(), p.getEnd())).toList();

        List<MeasurementDto> measurements = JCasUtil.select(jcas, Measurement.class).stream()
                .map(m -> new MeasurementDto(m.getMeasurementType(), m.getValue(), m.getUnit(), m.getBegin(), m.getEnd(),
                        m.getCoveredText(), m.getRuleName())).toList();

        String redacted = redactPhi ? redact(text, phiSpans) : null;

        RuleSet rules = ruleService.current();
        long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
        return new AnalyzeResponse(text.length(), sections, sentenceAnnotations.size(), sentences, concepts, phi,
                measurements, redacted, new Stats(durationMs, rules.loadedAt(), rules.rejected().size()));
    }

    /** PHI spans never overlap (resolved by PhiAnnotator) and arrive ordered by begin offset. */
    static String redact(String text, List<PhiSpan> spans) {
        StringBuilder sb = new StringBuilder(text.length());
        int cursor = 0;
        for (PhiSpan span : spans) {
            sb.append(text, cursor, span.getBegin()).append('[').append(span.getPhiType()).append(']');
            cursor = span.getEnd();
        }
        return sb.append(text, cursor, text.length()).toString();
    }

    private void audit(AnalyzeResponse r) {
        if (!props.auditEnabled()) {
            return;
        }
        try {
            int negated = (int) r.concepts().stream().filter(ConceptDto::negated).count();
            runs.save(new ProcessingRun(r.textLength(), r.concepts().size(), negated, r.phi().size(), r.stats().durationMs()));
        } catch (RuntimeException e) {
            log.warn("Could not write the audit row; the analysis result is unaffected", e);
        }
    }

    public static class InvalidChartException extends RuntimeException {
        public InvalidChartException(String message) {
            super(message);
        }
    }

    public static class ChartTooLargeException extends RuntimeException {
        public ChartTooLargeException(String message) {
            super(message);
        }
    }
}
