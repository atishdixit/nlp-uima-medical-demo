package com.example.mednlp.api;

import com.example.mednlp.api.Dtos.AnalyzeRequest;
import com.example.mednlp.api.Dtos.AnalyzeResponse;
import com.example.mednlp.api.Dtos.RuleSummary;
import com.example.mednlp.rules.RuleService;
import com.example.mednlp.service.ChartAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Medical chart NLP")
public class ChartController {

    private final ChartAnalysisService analysis;
    private final RuleService rules;

    public ChartController(ChartAnalysisService analysis, RuleService rules) {
        this.analysis = analysis;
        this.rules = rules;
    }

    @Operation(summary = "Analyse a chart sent as JSON")
    @PostMapping(value = "/charts/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnalyzeResponse analyze(@RequestBody AnalyzeRequest request) {
        return analysis.analyze(request.text(), request.redactPhi(), request.includeSentences());
    }

    @Operation(summary = "Analyse a chart sent as plain text (handy with curl --data-binary @file.txt)")
    @PostMapping(value = "/charts/analyze/text", consumes = MediaType.TEXT_PLAIN_VALUE)
    public AnalyzeResponse analyzeText(@RequestBody String text,
                                       @RequestParam(defaultValue = "false") boolean redactPhi,
                                       @RequestParam(defaultValue = "false") boolean includeSentences) {
        return analysis.analyze(text, redactPhi, includeSentences);
    }

    @Operation(summary = "Show the active rule set: counts and any rules that were rejected")
    @GetMapping("/rules/summary")
    public RuleSummary ruleSummary() {
        return RuleSummary.of(rules.current());
    }

    @Operation(summary = "Reload rules from MySQL now (call after editing the rule tables)")
    @PostMapping("/admin/rules/refresh")
    public RuleSummary refreshRules() {
        return RuleSummary.of(rules.refresh());
    }
}
