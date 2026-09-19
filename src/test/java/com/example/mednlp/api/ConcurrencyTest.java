package com.example.mednlp.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mednlp.api.Dtos.AnalyzeResponse;
import com.example.mednlp.service.ChartAnalysisService;
import com.example.mednlp.uima.annotators.SampleCharts;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** EC-24: many simultaneous requests share four pooled engines and must never see each other's data. */
@SpringBootTest(properties = {"mednlp.pool-size=4", "mednlp.audit-enabled=false"})
@ActiveProfiles("h2")
class ConcurrencyTest {

    @Autowired
    ChartAnalysisService service;

    private static String signature(AnalyzeResponse r) {
        StringBuilder sb = new StringBuilder();
        r.concepts().forEach(c -> sb.append(c.begin()).append('-').append(c.end()).append(':').append(c.code())
                .append(c.negated() ? "!" : "+").append(c.section()).append(';'));
        r.measurements().forEach(m -> sb.append(m.type()).append('=').append(m.value()).append(';'));
        r.phi().forEach(p -> sb.append(p.type()).append(p.begin()).append(';'));
        r.sections().forEach(s -> sb.append(s.name()).append(';'));
        return sb.toString();
    }

    @Test
    void parallelRequestsGetIsolatedAndCorrectResults() throws Exception {
        List<String> charts = List.of(
                SampleCharts.read("soap-note.txt"),
                SampleCharts.read("negation-cases.txt"),
                SampleCharts.read("phi-heavy.txt"),
                SampleCharts.read("unsectioned-note.txt"),
                "fever");
        List<String> expected = charts.stream().map(c -> signature(service.analyze(c, false, false))).toList();

        int threads = 16;
        int perThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int offset = t;
                futures.add(executor.submit(() -> {
                    List<String> mismatches = new ArrayList<>();
                    for (int i = 0; i < perThread; i++) {
                        int idx = (offset + i) % charts.size();
                        String actual = signature(service.analyze(charts.get(idx), false, false));
                        if (!actual.equals(expected.get(idx))) {
                            mismatches.add("chart " + idx + " differed");
                        }
                    }
                    return mismatches;
                }));
            }
            for (Future<List<String>> f : futures) {
                assertThat(f.get(120, TimeUnit.SECONDS)).isEmpty();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
